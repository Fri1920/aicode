package com.aicode.feature.agent.domain.container

import com.aicode.core.util.FileLogger
import com.aicode.feature.workspace.domain.remote.RemoteAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteSshConnection"

/**
 * 共享的 SSH 连接管理器：持有单个 sshj [SSHClient]，供 [RemoteSshEngine]（exec channel）
 * 与 [RemoteSftpFileAccess]（SFTP channel）复用同一 SSH 连接。
 *
 * 连接配置（host/port/username/auth）由调用方在 [connect] 时传入。连接断开后下次 [connect]
 * 重新建立。所有操作串行化（[mutex]），避免并发导致 sshj 状态错乱。
 */
@Singleton
class RemoteSshConnection @Inject constructor() {

    @Volatile
    private var sshClient: SSHClient? = null

    @Volatile
    private var sftpClient: SFTPClient? = null

    private val mutex = Mutex()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    /** 连接状态流，供 UI 显示指示器、工作区初始化等待连接就绪。 */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    /** 重连成功后回调，供工作区重新加载。由 [startSupervisor] 注册。 */
    private var onReconnected: (suspend () -> Unit)? = null
    private var supervisorJob: Job? = null

    /** 当前连接配置快照，供重连与路径映射使用。 */
    @Volatile
    var config: RemoteConnectionConfig? = null
        private set

    suspend fun connect(config: RemoteConnectionConfig) = mutex.withLock {
        if (isConnected() && this.config == config) return@withLock
        disconnectInternal()
        this.config = config
        _connectionState.value = ConnectionState.CONNECTING
        try {
            withContext(Dispatchers.IO) {
                val client = SSHClient().apply {
                    addHostKeyVerifier(PromiscuousVerifier())
                    connect(config.host, config.port)
                    when (val auth = config.auth) {
                        is RemoteAuth.Password -> authPassword(config.username, auth.password)
                        is RemoteAuth.PrivateKey -> {
                            val keyProvider = if (auth.passphrase != null) {
                                loadKeys(auth.privateKeyPath, auth.passphrase)
                            } else {
                                loadKeys(auth.privateKeyPath)
                            }
                            authPublickey(config.username, keyProvider)
                        }
                    }
                    // 启用 SSH 心跳保活，防止空闲超时断连
                    runCatching {
                        connection.keepAlive?.let {
                            it.setKeepAliveInterval(30)
                            it.start()
                        }
                    }
                }
                sshClient = client
                FileLogger.i(TAG, "SSH 已连接 ${config.host}:${config.port} as ${config.username}")
            }
            _connectionState.value = ConnectionState.CONNECTED
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.FAILED
            throw e
        }
    }

    /** 无参 connect：用上次保存的 config 重连。 */
    suspend fun connect() {
        val cfg = config ?: throw IllegalStateException("未配置 SSH 连接")
        connect(cfg)
    }

    suspend fun disconnect() = mutex.withLock {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        runCatching { sftpClient?.close() }
        runCatching { sshClient?.disconnect() }
        sftpClient = null
        sshClient = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun isConnected(): Boolean =
        sshClient?.isConnected == true && sshClient?.isAuthenticated == true

    /** 开一个 exec session 执行命令。调用方负责关闭返回的 Command。 */
    fun startExecSession(command: String): Session.Command {
        val client = sshClient ?: throw IllegalStateException("SSH 未连接")
        val session = client.startSession()
        return session.exec(command)
    }

    /** 开一个新的 Session，供调用方分配 PTY 并启动 shell。调用方负责关闭 Session。 */
    fun startShellSession(): Session {
        val client = sshClient ?: throw IllegalStateException("SSH 未连接")
        return client.startSession()
    }

    /** 获取共享的 SFTP client（惰性创建）。调用方不应关闭它——由 [disconnect] 统一管理。 */
    suspend fun getSftpClient(): SFTPClient = mutex.withLock {
        sftpClient?.takeIf { sshClient?.isConnected == true }?.let { return@withLock it }
        val client = sshClient ?: throw IllegalStateException("SSH 未连接")
        val sftp = withContext(Dispatchers.IO) { client.newSFTPClient() }
        sftpClient = sftp
        sftp
    }

    /** [getSftpClient] 的阻塞版，供非 suspend 调用方（如 [RemoteSftpFileAccess]）使用。 */
    fun getSftpClientBlocking(): SFTPClient = runBlocking { getSftpClient() }

    /**
     * 启动连接监督协程：远程模式下定期探活，连接断开则自动重连（指数退避），
     * 重连成功后触发 [onReconnected] 回调（供工作区重新加载）并维护 [connectionState]。
     * 幂等：重复调用不会起多个 supervisor。仅在远程模式下生效。 */
    fun startSupervisor(scope: CoroutineScope, onReconnected: suspend () -> Unit) {
        this.onReconnected = onReconnected
        if (supervisorJob?.isActive == true) return
        supervisorJob = scope.launch {
            var backoffMs = 5000L
            val maxBackoffMs = 30000L
            while (isActive) {
                delay(15000) // 探活间隔
                val cfg = config ?: continue
                if (isConnected()) {
                    backoffMs = 5000L // 连接正常，重置退避
                    continue
                }
                // 连接已断，尝试重连
                _connectionState.value = ConnectionState.CONNECTING
                runCatching { connect(cfg) }
                    .onSuccess {
                        FileLogger.i(TAG, "SSH 自动重连成功")
                        backoffMs = 5000L
                        runCatching { onReconnected.invoke() }
                    }
                    .onFailure {
                        FileLogger.w(TAG, "SSH 自动重连失败，${backoffMs}ms 后重试", it)
                        _connectionState.value = ConnectionState.FAILED
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
                    }
            }
        }
    }

    /**
     * 更新 /workspace 符号链接指向当前选中工作区的远程路径，让 AI 用 /workspace/... 路径时
     * Bash 命令（pwd 等）能直接访问到正确的工作区目录。先试 /workspace（需 root），
     * 失败则 fallback 到 ~/workspace。应在工作区选中/初始化后调用。
     */
    suspend fun updateWorkspaceSymlink(workspacePath: String) {
        val client = sshClient ?: return
        val ws = workspacePath.trimEnd('/')
        if (ws.isEmpty()) return
        withContext(Dispatchers.IO) {
            runCatching {
                val session = client.startSession()
                val cmd = session.exec("ln -sfn '$ws' /workspace 2>/dev/null || ln -sfn '$ws' ~/workspace 2>/dev/null; echo done")
                java.io.BufferedReader(java.io.InputStreamReader(cmd.inputStream)).readText()
                session.close()
            }.onFailure { FileLogger.w(TAG, "更新 workspace 符号链接失败: $ws", it) }
        }
    }
}

/** 远程 SSH 连接状态，供 UI 指示器与工作区初始化时序判断。 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED
}

/** 远程 SSH 连接配置。 */
data class RemoteConnectionConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: RemoteAuth,
    /** 远程服务器上的工作区根路径（AI 的 /workspace 映射到此路径）。 */
    val remoteWorkspacePath: String
)
