package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.data.repository.ExecutionMode
import com.aicode.feature.settings.data.repository.RemoteConnectionSettings

/**
 * 执行环境模式选择区：本地 PRoot 容器 vs 远程 SSH 服务器。
 *
 * 嵌入容器设置页顶部。切换到远程模式时展开 SSH 连接配置（host/port/username/password/远程路径）。
 * 模式切换后需重启 App 生效（DI Singleton 在启动时按模式注入）。
 */
@Composable
internal fun ExecutionModeSection(
    mode: ExecutionMode,
    remoteConnection: RemoteConnectionSettings?,
    onModeChange: (ExecutionMode) -> Unit,
    onRemoteConnectionChange: (RemoteConnectionSettings) -> Unit
) {
    var host by remember(remoteConnection) { mutableStateOf(remoteConnection?.host ?: "") }
    var port by remember(remoteConnection) { mutableStateOf((remoteConnection?.port ?: 22).toString()) }
    var username by remember(remoteConnection) { mutableStateOf(remoteConnection?.username ?: "") }
    var password by remember(remoteConnection) { mutableStateOf(remoteConnection?.password ?: "") }
    var remotePath by remember(remoteConnection) { mutableStateOf(remoteConnection?.remoteWorkspacePath ?: "/home/user/workspace") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            text = "执行环境",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = Spacing.xs)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = "选择命令执行与文件读写后端。切换后需重启 App 生效。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.padding(top = Spacing.sm))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = mode == ExecutionMode.LOCAL_PROOT,
                        onClick = { onModeChange(ExecutionMode.LOCAL_PROOT) }
                    )
                    Text(
                        text = "本地 PRoot 容器",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = mode == ExecutionMode.REMOTE_SSH,
                        onClick = { onModeChange(ExecutionMode.REMOTE_SSH) }
                    )
                    Text(
                        text = "远程 SSH 服务器",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = Spacing.sm)
                    )
                }

                if (mode == ExecutionMode.REMOTE_SSH) {
                    Spacer(Modifier.padding(top = Spacing.md))
                    Text(
                        text = "SSH 连接配置",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.padding(top = Spacing.sm))
                    OutlinedTextField(
                        value = host,
                        onValueChange = {
                            host = it
                            emitConnection(host, port, username, password, remotePath, onRemoteConnectionChange)
                        },
                        label = { Text("主机地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.padding(top = Spacing.sm))
                    OutlinedTextField(
                        value = port,
                        onValueChange = {
                            port = it
                            emitConnection(host, port, username, password, remotePath, onRemoteConnectionChange)
                        },
                        label = { Text("端口") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.padding(top = Spacing.sm))
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            emitConnection(host, port, username, password, remotePath, onRemoteConnectionChange)
                        },
                        label = { Text("用户名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.padding(top = Spacing.sm))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            emitConnection(host, port, username, password, remotePath, onRemoteConnectionChange)
                        },
                        label = { Text("密码") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.padding(top = Spacing.sm))
                    OutlinedTextField(
                        value = remotePath,
                        onValueChange = {
                            remotePath = it
                            emitConnection(host, port, username, password, remotePath, onRemoteConnectionChange)
                        },
                        label = { Text("远程工作区路径") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.padding(top = Spacing.sm))
                    Text(
                        text = "AI 的 /workspace 将映射到此远程路径。命令执行走 SSH exec channel，文件读写走 SFTP。MCP stdio server 仍走本地 PRoot。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun emitConnection(
    host: String,
    port: String,
    username: String,
    password: String,
    remotePath: String,
    onRemoteConnectionChange: (RemoteConnectionSettings) -> Unit
) {
    if (host.isBlank() || username.isBlank()) return
    onRemoteConnectionChange(
        RemoteConnectionSettings(
            host = host,
            port = port.toIntOrNull() ?: 22,
            username = username,
            password = password,
            remoteWorkspacePath = remotePath.ifBlank { "/home/$username/workspace" }
        )
    )
}
