package com.aicode.feature.agent.domain.mcp

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.LinuxContainerEngine
import com.aicode.feature.agent.domain.tool.ToolRegistry
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

data class McpServerStatus(
    val name: String,
    val state: State,
    val toolCount: Int = 0,
    val error: String? = null
) {
    enum class State { CONNECTING, CONNECTED, FAILED, DISABLED }
}

// reloadMutex 串行化重连，避免设置页连点导致并发注册/反注册竞态。
@Singleton
class McpManager @Inject constructor(
    private val configRepository: McpConfigRepository,
    private val toolRegistry: ToolRegistry,
    private val okHttpClient: OkHttpClient,
    private val containerEngine: LinuxContainerEngine,
    private val workspaceRepository: WorkspaceRepository
) {
    private companion object {
        const val TAG = "McpManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reloadMutex = Mutex()

    private val activeClients = mutableMapOf<String, McpClient>()
    private val registeredToolNames = mutableSetOf<String>()

    private val _statuses = MutableStateFlow<List<McpServerStatus>>(emptyList())
    val statuses: StateFlow<List<McpServerStatus>> = _statuses.asStateFlow()

    fun start() {
        // 跟随当前工作区切换自动重载（首帧立即发射当前值，等价启动即重连；
        // 项目切换时合并配置与 stdio 工具的项目路径都会变化，需要重建连接）。
        scope.launch {
            workspaceRepository.current.collectLatest {
                reload()
            }
        }
    }

    suspend fun reload() = reloadMutex.withLock {
        val servers = configRepository.getEffectiveServers()
        FileLogger.i(TAG, "重新加载 MCP 配置，共 ${servers.size} 个 server")

        teardown()

        if (servers.isEmpty()) {
            _statuses.value = emptyList()
            return@withLock
        }

        // 先把所有 server 置为「连接中/禁用」，UI 立即有反馈。
        _statuses.value = servers.map { cfg ->
            McpServerStatus(
                name = cfg.name,
                state = if (cfg.enabled) McpServerStatus.State.CONNECTING else McpServerStatus.State.DISABLED
            )
        }

        // 并行连接所有启用的 server；各自独立失败。
        val results = withContext(Dispatchers.IO) {
            servers.filter { it.enabled }.map { cfg ->
                async { connectOne(cfg) }
            }.awaitAll()
        }

        // 合并禁用项与连接结果，保持原始顺序。
        val byName = results.associateBy { it.name }
        _statuses.value = servers.map { cfg ->
            byName[cfg.name] ?: McpServerStatus(cfg.name, McpServerStatus.State.DISABLED)
        }
    }

    private suspend fun connectOne(cfg: McpServerConfig): McpServerStatus {
        val t0 = System.currentTimeMillis()
        return try {
            FileLogger.i(TAG, "[${cfg.name}] 开始连接（${if (cfg.isStdio) "stdio" else "HTTP"}）")
            val transport = if (cfg.isStdio) {
                // 本地 stdio server 需要容器就绪；未就绪不自动初始化，直接失败并引导去终端页完成初始化。
                containerEngine.notReadyHint()?.let {
                    throw IllegalStateException(it)
                }
                StdioTransport(
                    serverName = cfg.name,
                    engine = containerEngine,
                    program = cfg.command!!,
                    programArgs = cfg.args,
                    projectPath = workspaceRepository.currentPath(),
                    extraEnv = cfg.env
                )
            } else {
                StreamableHttpTransport(
                    endpoint = cfg.url.orEmpty(),
                    client = okHttpClient,
                    extraHeaders = cfg.headers
                )
            }
            val client = McpClient(serverName = cfg.name, transport = transport)
            client.connect()

            val tools = client.tools.map { McpTool(client, it) }
            val enabledTools = tools.filter { it.remoteName !in cfg.disabledTools }
            synchronized(activeClients) {
                activeClients[cfg.name] = client
                enabledTools.forEach { tool ->
                    toolRegistry.register(tool.name, tool)
                    registeredToolNames.add(tool.name)
                }
            }
            FileLogger.i(TAG, "[${cfg.name}] 连接成功，注册 ${enabledTools.size}/${tools.size} 个工具（${System.currentTimeMillis() - t0}ms）")
            McpServerStatus(cfg.name, McpServerStatus.State.CONNECTED, toolCount = enabledTools.size)
        } catch (e: Exception) {
            FileLogger.e(TAG, "[${cfg.name}] 连接失败（${System.currentTimeMillis() - t0}ms）", e)
            McpServerStatus(cfg.name, McpServerStatus.State.FAILED, error = e.message)
        }
    }

    fun getServerTools(serverName: String): List<McpToolDescriptor> {
        return synchronized(activeClients) {
            activeClients[serverName]?.tools ?: emptyList()
        }
    }

    /**
     * 仅重连单个 server（保存/启用开关/编辑刷新用），其他 server 的连接与已注册工具不受影响。
     * 配置中不存在该 name 时静默返回。
     */
    suspend fun reloadServer(name: String) = reloadMutex.withLock {
        val cfg = configRepository.getEffectiveServers().firstOrNull { it.name == name } ?: return@withLock
        teardownServer(name)
        if (_statuses.value.none { it.name == name }) {
            _statuses.value = _statuses.value + McpServerStatus(name, McpServerStatus.State.CONNECTING)
        }
        if (!cfg.enabled) {
            _statuses.value = _statuses.value.map {
                if (it.name == name) McpServerStatus(name, McpServerStatus.State.DISABLED) else it
            }
            return@withLock
        }
        _statuses.value = _statuses.value.map {
            if (it.name == name) McpServerStatus(name, McpServerStatus.State.CONNECTING) else it
        }
        val result = withContext(Dispatchers.IO) { connectOne(cfg) }
        _statuses.value = _statuses.value.map { if (it.name == name) result else it }
    }

    /**
     * 仅重连当前未连接的 server（已连接的跳过），新会话时兜底：manageMcp 新增/删除只改配置不重连，
     * 提示「下次会话生效」；本方法让新会话真正连上未连接的 server，不打断已连接的工具。
     */
    suspend fun reconnectUnconnected() = reloadMutex.withLock {
        val servers = configRepository.getEffectiveServers().filter { it.enabled }
        for (cfg in servers) {
            val connected = synchronized(activeClients) { activeClients.containsKey(cfg.name) }
            if (connected) continue
            if (_statuses.value.none { it.name == cfg.name }) {
                _statuses.value = _statuses.value + McpServerStatus(cfg.name, McpServerStatus.State.CONNECTING)
            }
            _statuses.value = _statuses.value.map {
                if (it.name == cfg.name) McpServerStatus(cfg.name, McpServerStatus.State.CONNECTING) else it
            }
            val result = withContext(Dispatchers.IO) { connectOne(cfg) }
            _statuses.value = _statuses.value.map { if (it.name == cfg.name) result else it }
        }
    }

    /** 删除 server 时仅断开其连接并反注册其工具，不影响其他 server。 */
    suspend fun removeServer(name: String) = reloadMutex.withLock {
        teardownServer(name)
        _statuses.value = _statuses.value.filterNot { it.name == name }
    }

    private fun teardownServer(name: String) {
        synchronized(activeClients) {
            val client = activeClients.remove(name) ?: return
            runCatching { client.close() }
            client.tools.forEach { tool ->
                toolRegistry.unregister(tool.name)
                registeredToolNames.remove(tool.name)
            }
        }
    }

    private fun teardown() {
        synchronized(activeClients) {
            registeredToolNames.forEach { toolRegistry.unregister(it) }
            registeredToolNames.clear()
            activeClients.values.forEach { runCatching { it.close() } }
            activeClients.clear()
        }
    }
}
