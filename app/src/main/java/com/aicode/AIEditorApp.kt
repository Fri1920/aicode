package com.aicode

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.aicode.core.util.AILogger
import com.aicode.core.util.FileLogger
import net.schmizz.sshj.common.SecurityUtils
import com.aicode.feature.agent.domain.container.ContainerInstaller
import com.aicode.feature.credentials.data.GitCredentialsFileSync
import com.aicode.feature.agent.domain.mcp.McpManager
import com.aicode.feature.settings.data.repository.KeepaliveSettingsRepository
import com.aicode.feature.settings.data.repository.LogSettingsRepository
import com.aicode.feature.terminal.domain.TerminalKeepaliveService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AIEditorApp : Application() {

    private companion object {
        const val TAG = "AIEditorApp"
    }

    /** Hilt 字段注入：在 [onCreate] 的 super 调用后即可用。 */
    @Inject
    lateinit var logSettings: LogSettingsRepository

    /** 后台保活开关持久化。 */
    @Inject
    lateinit var keepaliveSettings: KeepaliveSettingsRepository

    /** MCP 生命周期总管：启动即连接已配置的远程 server。 */
    @Inject
    lateinit var mcpManager: McpManager

    /** git 凭据/署名落盘同步器：启动即把 Room 凭据 + DataStore 署名写到容器持久挂载目录，
     *  供终端/AI/UI 三端 git 经 credential.helper=store 共用，兜底 rootfs 升级或文件被删。 */
    @Inject
    lateinit var gitCredentialsFileSync: GitCredentialsFileSync

    /** 三端 git 缺凭据的统一弹窗桥：监听容器内 credential helper 经文件 IPC 发来的未登录请求，
     *  暴露 StateFlow 供全局弹窗回填后回喂 git。必须在主线程启动（FileObserver 绑定主 Looper）。 */
    @Inject
    lateinit var credentialRequestBridge: com.aicode.feature.credentials.data.CredentialRequestBridge

    /** 执行模式仓库（本地 PRoot / 远程 SSH）。 */
    @Inject
    lateinit var executionModeRepository: com.aicode.feature.settings.data.repository.ExecutionModeRepository

    /** 执行模式同步缓存：启动时从 DataStore 读首帧注入 DI。 */
    @Inject
    lateinit var executionModeHolder: com.aicode.feature.settings.data.repository.ExecutionModeHolder

    /** 远程 SSH 连接管理器：远程模式下启动即用配置建立连接。 */
    @Inject
    lateinit var remoteSshConnection: com.aicode.feature.agent.domain.container.RemoteSshConnection

    /** 长驻作用域：持续把持久化的日志等级同步到 FileLogger。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        registerBouncyCastle()
        FileLogger.init(this)
        AILogger.init(this)
        installCrashHandler()
        createNotificationChannels()
        // 主线程启动凭据请求监听（FileObserver 必须主线程创建与 startWatching），
        // 监听容器内 credential helper 写来的 cred-req-* → 全局弹窗回填 → 回喂 git 续跑。
        credentialRequestBridge.start()
        // 启动即把最新的内置指南手册提取到私有配置目录
        appScope.launch {
            ContainerInstaller.extractDocs(this@AIEditorApp)
        }
        // 启动即把内置提示词全量释放到 ~/.aicode/prompts/（覆盖式，随 App 升级更新）；
        // 用户自定义覆盖放在 ~/.aicode/prompts.custom/，同名即覆盖，不被升级覆盖。
        appScope.launch {
            ContainerInstaller.extractPrompts(this@AIEditorApp)
        }
        // 启动即把 Room 凭据 + DataStore 署名落盘到容器持久挂载（/root/.aicode），
        // 让终端裸 git / AI 工具 / UI 三端共用同一份凭据与署名配置。
        appScope.launch {
            gitCredentialsFileSync.syncAll()
        }
        // 启动即加载持久化等级，并随设置页改动实时生效（唯一同步点）。
        appScope.launch {
            logSettings.levelFlow.collectLatest { FileLogger.setMinLevel(it) }
        }
        // 启动即同步从 DataStore 读首帧执行模式缓存到 ExecutionModeHolder，供 DI @Provides 同步读取。
        // 异步读首帧模式写入 ExecutionModeHolder。委托层（DelegatingCommandEngine/DelegatingFileAccess）
        // 每次方法调用时才按 holder.currentMode() 转发，不依赖注入时机，故 holder 晚几毫秒写入无妨——
        // 首次命令/文件操作一定在 UI 启动之后，那时 holder 早已就绪。
        // SSH 连接放后台，失败不阻塞 UI（连接失败时首次命令会触发 ensureInstalled 重试）。
        appScope.launch {
            val mode = executionModeRepository.executionModeFlow.first()
            executionModeHolder.setMode(mode)
            if (mode == com.aicode.feature.settings.data.repository.ExecutionMode.REMOTE_SSH) {
                executionModeRepository.remoteConnectionFlow.first()?.let { settings ->
                    runCatching {
                        remoteSshConnection.connect(
                            com.aicode.feature.agent.domain.container.RemoteConnectionConfig(
                                host = settings.host,
                                port = settings.port,
                                username = settings.username,
                                auth = com.aicode.feature.workspace.domain.remote.RemoteAuth.Password(settings.password),
                                remoteWorkspacePath = settings.remoteWorkspacePath
                            )
                        )
                    }.onFailure { FileLogger.e(TAG, "启动时 SSH 连接失败，将在首次命令时重试", it) }
                }
            }
        }
        // 后台保活常驻通知的唯一反应器：监听开关，启停 TerminalKeepaliveService 的常驻模式。
        // 既覆盖设置页实时切换，也覆盖冷启动恢复。仅在「由开变关」时发 disable，
        // 避免为关闭而凭空拉起从未开过的 Service。
        appScope.launch {
            var last: Boolean? = null
            keepaliveSettings.enabledFlow.distinctUntilChanged().collect { enabled ->
                if (enabled) {
                    TerminalKeepaliveService.enablePersistent(this@AIEditorApp)
                } else if (last == true) {
                    TerminalKeepaliveService.disablePersistent(this@AIEditorApp)
                }
                last = enabled
            }
        }
        // 连接已配置的 MCP server，把其工具注册进 ToolRegistry（内部自有 scope，失败不影响启动）。
        mcpManager.start()
    }

    /** 注册完整版 BouncyCastle 取代 Android 自带的裁剪版。
     *  sshj 0.38.0 用 X25519 做密钥交换，Android 自带的 BC provider 不含 X25519 算法，
     *  需先移除裁剪版再注册 bcprov-jdk18on（sshj 传递依赖）的完整版，并告诉 sshj 使用它。
     *  必须在任何 sshj 调用之前完成。 */
    private fun registerBouncyCastle() {
        java.security.Security.removeProvider("BC")
        java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1)
        SecurityUtils.setSecurityProvider("BC")
    }

    /** 捕获未处理异常并落盘，随后交回系统默认处理器（保留原有崩溃弹窗/上报行为）。 */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            FileLogger.e("CRASH", "线程 ${thread.name} 未捕获异常", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                "terminal_service",
                "Terminal Services",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for background terminal tasks"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
