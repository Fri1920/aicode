package com.aicode.feature.agent.domain.container

import com.aicode.core.util.FileLogger
import com.aicode.feature.workspace.domain.remote.RemoteAuth
import kotlinx.coroutines.Dispatchers
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

    /** 当前连接配置快照，供重连与路径映射使用。 */
    @Volatile
    var config: RemoteConnectionConfig? = null
        private set

    suspend fun connect(config: RemoteConnectionConfig) = mutex.withLock {
        if (isConnected() && this.config == config) return@withLock
        disconnectInternal()
        this.config = config
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
            }
            sshClient = client
            FileLogger.i(TAG, "SSH 已连接 ${config.host}:${config.port} as ${config.username}")
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
    }

    fun isConnected(): Boolean =
        sshClient?.isConnected == true && sshClient?.isAuthenticated == true

    /** 开一个 exec session 执行命令。调用方负责关闭返回的 Command。 */
    fun startExecSession(command: String): Session.Command {
        val client = sshClient ?: throw IllegalStateException("SSH 未连接")
        val session = client.startSession()
        return session.exec(command)
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
