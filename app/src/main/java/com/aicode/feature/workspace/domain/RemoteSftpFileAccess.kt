package com.aicode.feature.workspace.domain

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.RemoteSshConnection
import com.aicode.feature.workspace.domain.WorkspacePathMapper.Companion.CONTAINER_ROOT
import net.schmizz.sshj.sftp.FileMode
import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import javax.inject.Inject

private const val TAG = "RemoteSftpFileAccess"

/**
 * [FileAccessProvider] 的远程 SFTP 实现：用 sshj SFTP channel 读写远程文件。
 *
 * 路径映射：AI 给的 `/workspace/...` 映射到 [RemoteSshConnection.config] 的 `remoteWorkspacePath` + 相对路径。
 * 其它绝对路径（如 `/etc/...`）直接作为远程绝对路径使用。
 *
 * 共享 [RemoteSshConnection] 的 SSH 连接（与 [RemoteSshEngine] 复用同一 SSHClient）。
 * [copyToLocal] 通过 SFTP 下载到本地缓存目录，供 [ViewImageTool] 等需要本地文件路径的工具使用。
 */
class RemoteSftpFileAccess @Inject constructor(
    private val connection: RemoteSshConnection,
    @Suppress("unused") private val pathMapper: WorkspacePathMapper
) : FileAccessProvider {

    /** 把 AI 路径映射到远程服务器上的真实路径。 */
    private fun toRemotePath(path: String): String {
        val cfg = connection.config ?: throw IllegalStateException("SSH 未连接")
        val p = path.trim().let { if (it.startsWith("~/")) "/home/${cfg.username}/" + it.removePrefix("~/") else it }
        return when {
            p == CONTAINER_ROOT || p == "$CONTAINER_ROOT/" -> cfg.remoteWorkspacePath.trimEnd('/')
            p.startsWith("$CONTAINER_ROOT/") ->
                cfg.remoteWorkspacePath.trimEnd('/') + "/" + p.removePrefix("$CONTAINER_ROOT/")
            p.startsWith("/") -> p
            else -> cfg.remoteWorkspacePath.trimEnd('/') + "/" + p
        }
    }

    /** 把远程路径还原为 AI 视角的容器路径（回显用）。 */
    private fun toDisplayPathFromRemote(remotePath: String): String {
        val cfg = connection.config ?: return remotePath
        val ws = cfg.remoteWorkspacePath.trimEnd('/')
        return when {
            remotePath == ws -> CONTAINER_ROOT
            remotePath.startsWith("$ws/") -> CONTAINER_ROOT + "/" + remotePath.removePrefix("$ws/")
            else -> remotePath
        }
    }

    override fun readFile(path: String): String {
        val remote = toRemotePath(path)
        val tempFile = File.createTempFile("aicode_remote_", ".txt").apply { deleteOnExit() }
        try {
            val sftp = connection.getSftpClientBlocking()
            sftp.get(remote, tempFile.absolutePath)
            return tempFile.readText()
        } catch (e: Exception) {
            FileLogger.e(TAG, "SFTP readFile 失败: $remote", e)
            throw NoSuchFileException(File(remote))
        } finally {
            tempFile.delete()
        }
    }

    override fun readLines(path: String): Sequence<String> {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        val tempFile = File.createTempFile("aicode_remote_", ".tmp").apply { deleteOnExit() }
        runCatching {
            sftp.get(remote, tempFile.absolutePath)
        }.getOrElse {
            tempFile.delete()
            throw NoSuchFileException(File(remote))
        }
        return tempFile.bufferedReader().useLines { it.toList() }.asSequence()
            .also { tempFile.delete() }
    }

    override fun writeFile(path: String, content: String, overwrite: Boolean) {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        if (exists(path) && !overwrite) throw FileAlreadyExistsException(File(remote))
        val tempFile = File.createTempFile("aicode_upload_", ".tmp").apply { deleteOnExit() }
        try {
            tempFile.writeText(content)
            runCatching { sftp.mkdirs(remote.substringBeforeLast('/')) }
            sftp.put(tempFile.absolutePath, remote)
        } finally {
            tempFile.delete()
        }
    }

    override fun exists(path: String): Boolean {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        return runCatching { sftp.statExistence(remote) != null }.getOrDefault(false)
    }

    override fun isDirectory(path: String): Boolean {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        return runCatching {
            sftp.statExistence(remote)?.type == FileMode.Type.DIRECTORY
        }.getOrDefault(false)
    }

    override fun isFile(path: String): Boolean {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        return runCatching {
            val attrs = sftp.statExistence(remote) ?: return false
            attrs.type != FileMode.Type.DIRECTORY
        }.getOrDefault(false)
    }

    override fun fileSize(path: String): Long {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        return runCatching { sftp.statExistence(remote)?.size ?: 0L }.getOrDefault(0L)
    }

    override fun lastModified(path: String): Long {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        return runCatching {
            (sftp.statExistence(remote)?.mtime ?: 0L) * 1000L
        }.getOrDefault(0L)
    }

    override fun permissions(path: String): String {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        return runCatching {
            sftp.statExistence(remote) ?: return "---"
            "rwx"
        }.getOrDefault("---")
    }

    override fun listFiles(path: String): List<FileEntry> {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        return runCatching {
            sftp.ls(remote).map { resource ->
                FileEntry(
                    name = resource.name,
                    isDirectory = resource.attributes.type == FileMode.Type.DIRECTORY,
                    size = resource.attributes.size,
                    lastModified = resource.attributes.mtime * 1000L,
                    localFile = null,
                    permissions = "rwx"
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun readBytes(path: String): ByteArray {
        val remote = toRemotePath(path)
        val tempFile = File.createTempFile("aicode_remote_", ".bin").apply { deleteOnExit() }
        try {
            val sftp = connection.getSftpClientBlocking()
            sftp.get(remote, tempFile.absolutePath)
            return tempFile.readBytes()
        } catch (e: Exception) {
            FileLogger.e(TAG, "SFTP readBytes 失败: $remote", e)
            throw NoSuchFileException(File(remote))
        } finally {
            tempFile.delete()
        }
    }

    override fun copyToLocal(path: String): File {
        val remote = toRemotePath(path)
        val tempFile = File.createTempFile("aicode_remote_", ".copy").apply { deleteOnExit() }
        try {
            val sftp = connection.getSftpClientBlocking()
            sftp.get(remote, tempFile.absolutePath)
            return tempFile
        } catch (e: Exception) {
            tempFile.delete()
            FileLogger.e(TAG, "SFTP copyToLocal 失败: $remote", e)
            throw NoSuchFileException(File(remote))
        }
    }

    override fun delete(path: String) {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        runCatching {
            val attrs = sftp.statExistence(remote) ?: return
            if (attrs.type == FileMode.Type.DIRECTORY) sftp.rmdir(remote) else sftp.rm(remote)
        }
    }

    override fun mkdirs(path: String) {
        val remote = toRemotePath(path)
        val sftp = connection.getSftpClientBlocking()
        runCatching { sftp.mkdirs(remote) }
    }

    override fun parentPath(path: String): String? {
        val remote = toRemotePath(path)
        val parent = remote.substringBeforeLast('/', "")
        if (parent.isEmpty()) return null
        return toDisplayPathFromRemote(parent)
    }

    override fun toDisplayPath(path: String): String = toDisplayPathFromRemote(toRemotePath(path))
}
