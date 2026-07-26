package com.aicode.feature.settings.presentation.component

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.container.ContainerProfile
import com.aicode.feature.agent.domain.container.RootfsSource
import com.aicode.feature.settings.data.repository.ExecutionMode
import com.aicode.feature.workspace.domain.model.RemoteConnection
import com.aicode.feature.workspace.domain.model.RemoteProtocol
import compose.icons.FeatherIcons
import compose.icons.feathericons.Edit3
import compose.icons.feathericons.Plus
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Trash2

/**
 * 容器镜像二级页：列出内置与自定义 profile，单选切换；新建（本地镜像导入 tar.gz + 填启动参数，或远程 SSH 复用工作区通道）；
 * 删除自定义（本地镜像连带清理其 rootfs 目录，远程 SSH 无 rootfs）。
 *
 * 选中某个 profile 时按其 [ContainerProfile.mode] 同步切全局执行模式——本地镜像走 PRoot 容器，
 * 远程 SSH 镜像走 SSH exec/SFTP。内置 Alpine 默认本地模式。
 */
@Composable
internal fun ContainerSection(
    profiles: List<ContainerProfile>,
    activeProfileId: String,
    onSelect: (String) -> Unit,
    onSaveCustom: (ContainerProfile) -> Unit,
    onEditCustom: (ContainerProfile) -> Unit,
    onDeleteCustom: (ContainerProfile) -> Unit,
    onSwitchConfirmed: () -> Unit = {},
    onResetBuiltin: () -> Unit = {},
    remoteConnections: List<RemoteConnection> = emptyList()
) {
    var showAddSheet by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<ContainerProfile?>(null) }
    var deletingProfile by remember { mutableStateOf<ContainerProfile?>(null) }
    var pendingSwitch by remember { mutableStateOf<ContainerProfile?>(null) }
    var pendingReset by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        item {
            Text(
                text = "选择一个容器镜像作为命令执行后端。本地镜像走 PRoot 容器，远程 SSH 镜像连接远程服务器执行命令。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.sm)
            )
        }

        items(profiles, key = { it.id }) { profile ->
            val active = profile.id == activeProfileId
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!active) pendingSwitch = profile
                        }
                        .padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = profileSubtitle(profile, remoteConnections),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.xs)
                        )
                        if (profile.mode == ExecutionMode.LOCAL_PROOT && profile.extraBindings.isNotEmpty()) {
                            Text(
                                text = "绑定: ${profile.extraBindings.joinToString(" ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!profile.isBuiltin) {
                        IconButton(onClick = { editingProfile = profile }) {
                            Icon(
                                imageVector = FeatherIcons.Edit3,
                                contentDescription = "编辑",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { deletingProfile = profile }) {
                            Icon(
                                imageVector = FeatherIcons.Trash2,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        IconButton(onClick = { pendingReset = true }) {
                            Icon(
                                imageVector = FeatherIcons.RefreshCw,
                                contentDescription = "重置",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAddSheet = true },
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Icon(
                        imageVector = FeatherIcons.Plus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "添加镜像",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        ProfileEditSheet(
            initial = null,
            remoteConnections = remoteConnections,
            onDismiss = { showAddSheet = false },
            onConfirm = { profile ->
                val id = "custom-${System.currentTimeMillis()}"
                onSaveCustom(
                    if (profile.mode == ExecutionMode.REMOTE_SSH) {
                        profile.copy(id = id, name = profile.name.ifBlank { "远程 SSH" })
                    } else {
                        profile.copy(id = id, name = profile.name.ifBlank { "自定义镜像" })
                    }
                )
                showAddSheet = false
            }
        )
    }

    editingProfile?.let { editing ->
        ProfileEditSheet(
            initial = editing,
            remoteConnections = remoteConnections,
            onDismiss = { editingProfile = null },
            onConfirm = { profile ->
                onEditCustom(profile.copy(id = editing.id))
                editingProfile = null
            }
        )
    }

    deletingProfile?.let { deleting ->
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text("删除镜像配置") },
            text = { Text("确定删除「${deleting.name}」？${if (deleting.mode == ExecutionMode.LOCAL_PROOT && !deleting.isBuiltin) "其 rootfs 目录会被一并清除。" else ""}内置 Alpine 不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCustom(deleting)
                    deletingProfile = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingProfile = null }) { Text("取消") } }
        )
    }

    pendingSwitch?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("切换容器镜像") },
            text = { Text("切换到「${target.name}」后，当前正在运行的 AI 会话将被停止、终端标签将被关闭。是否继续切换？") },
            confirmButton = {
                TextButton(onClick = {
                    onSwitchConfirmed()
                    onSelect(target.id)
                    pendingSwitch = null
                }) { Text("切换") }
            },
            dismissButton = { TextButton(onClick = { pendingSwitch = null }) { Text("取消") } }
        )
    }

    if (pendingReset) {
        AlertDialog(
            onDismissRequest = { pendingReset = false },
            title = { Text("重置内置容器") },
            text = { Text("将删除内置 Alpine 容器的 rootfs 目录（含已安装的工具与配置），下次启动容器时会重新解压并初始化。是否继续？") },
            confirmButton = {
                TextButton(onClick = {
                    onResetBuiltin()
                    pendingReset = false
                }) { Text("重置") }
            },
            dismissButton = { TextButton(onClick = { pendingReset = false }) { Text("取消") } }
        )
    }
}

/** 镜像列表项副标题：按 mode 与来源类型描述。 */
private fun profileSubtitle(profile: ContainerProfile, connections: List<RemoteConnection>): String {
    return when {
        profile.isBuiltin -> "内置镜像 · 自动 (bash/sh)"
        profile.mode == ExecutionMode.REMOTE_SSH -> {
            val ssh = profile.rootfsSource as? RootfsSource.RemoteSsh
            val connName = ssh?.connectionId?.let { cid -> connections.firstOrNull { it.id == cid }?.name }
            "远程 SSH · ${connName ?: "通道已删除"} · ${ssh?.remoteWorkspacePath ?: ""}"
        }
        else -> {
            val shellDesc = profile.shellPath?.ifBlank { null } ?: "/bin/sh"
            "导入的 tar.gz · shell: $shellDesc"
        }
    }
}

/**
 * 添加/编辑镜像的 ModalBottomSheet：顶部 SegmentedButton 切换本地镜像 / 远程 SSH。
 * 本地镜像分支：名称、shell 路径、额外绑定、额外参数、选 tar.gz 文件。
 * 远程 SSH 分支：名称、下拉选工作区已配置的 SFTP 通道、远程工作区路径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditSheet(
    initial: ContainerProfile?,
    remoteConnections: List<RemoteConnection>,
    onDismiss: () -> Unit,
    onConfirm: (ContainerProfile) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // SFTP 通道才适合 SSH exec（FTP/LOCAL 不走 sshj）
    val sshConnections = remoteConnections.filter { it.protocol == RemoteProtocol.SFTP }

    var mode by remember { mutableStateOf(initial?.mode ?: ExecutionMode.LOCAL_PROOT) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // 本地镜像字段
    var shellPath by remember { mutableStateOf(initial?.shellPath ?: "/bin/sh") }
    var bindingsText by remember { mutableStateOf(initial?.extraBindings?.joinToString(" ") ?: "") }
    var argsText by remember { mutableStateOf(initial?.extraArgs?.joinToString(" ") ?: "") }
    val initialUri = (initial?.rootfsSource as? RootfsSource.LocalFile)?.uri
    var pickedUri by remember { mutableStateOf(initialUri) }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pickedUri = uri.toString() }
    // 远程 SSH 字段
    val initialSsh = (initial?.rootfsSource as? RootfsSource.RemoteSsh)
    var selectedConnId by remember { mutableStateOf(initialSsh?.connectionId ?: sshConnections.firstOrNull()?.id ?: "") }
    var remotePath by remember { mutableStateOf(initialSsh?.remoteWorkspacePath ?: "") }
    var connExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initial == null) "添加镜像" else "编辑镜像",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ExecutionMode.LOCAL_PROOT,
                    onClick = { mode = ExecutionMode.LOCAL_PROOT },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("本地镜像") }
                SegmentedButton(
                    selected = mode == ExecutionMode.REMOTE_SSH,
                    onClick = { mode = ExecutionMode.REMOTE_SSH },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("远程 SSH") }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (mode == ExecutionMode.LOCAL_PROOT) {
                OutlinedTextField(
                    value = shellPath,
                    onValueChange = { shellPath = it },
                    label = { Text("shell 路径（如 /bin/sh、/bin/bash）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bindingsText,
                    onValueChange = { bindingsText = it },
                    label = { Text("额外绑定（空格分隔，如 /sdcard:/mnt）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    label = { Text("额外 proot 参数（空格分隔）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                TextButton(
                    onClick = { pickLauncher.launch(arrayOf("*/*")) }
                ) {
                    Text(
                        pickedUri?.let {
                            if (it == initialUri) "已导入（点此重新选择）" else "已选择文件"
                        } ?: "选择 tar.gz / tgz / tar.xz / txz 镜像文件"
                    )
                }
            } else {
                if (sshConnections.isEmpty()) {
                    Text(
                        text = "暂无可用的 SFTP 通道，请先在「远程服务器」页配置一个 SFTP 连接通道。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = connExpanded,
                        onExpandedChange = { connExpanded = !connExpanded }
                    ) {
                        val selectedName = sshConnections.firstOrNull { it.id == selectedConnId }?.name
                            ?: "选择 SSH 通道"
                        OutlinedTextField(
                            value = selectedName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("SSH 连接通道") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = connExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = connExpanded,
                            onDismissRequest = { connExpanded = false }
                        ) {
                            sshConnections.forEach { conn ->
                                DropdownMenuItem(
                                    text = { Text("${conn.name} (${conn.host}:${conn.port})") },
                                    onClick = {
                                        selectedConnId = conn.id
                                        if (remotePath.isBlank()) {
                                            remotePath = "/home/${conn.username}/workspace"
                                        }
                                        connExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = remotePath,
                        onValueChange = { remotePath = it },
                        label = { Text("远程工作区路径") },
                        placeholder = { Text("/home/user/workspace") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "AI 的 ~/workspace 将映射到此远程路径。命令执行走 SSH exec，文件读写走 SFTP。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.size(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(Spacing.sm))
                Button(
                    onClick = {
                        val profile = buildProfile(
                            mode = mode,
                            name = name,
                            shellPath = shellPath,
                            bindingsText = bindingsText,
                            argsText = argsText,
                            pickedUri = pickedUri,
                            selectedConnId = selectedConnId,
                            remotePath = remotePath
                        )
                        if (profile != null) onConfirm(profile)
                    },
                    enabled = canConfirm(mode, pickedUri, selectedConnId, sshConnections)
                ) { Text(if (initial == null) "添加" else "保存") }
            }
        }
    }
}

/** 据表单状态构造 ContainerProfile；校验不通过返回 null（按钮已 disabled，此处再兜底）。 */
private fun buildProfile(
    mode: ExecutionMode,
    name: String,
    shellPath: String,
    bindingsText: String,
    argsText: String,
    pickedUri: String?,
    selectedConnId: String,
    remotePath: String
): ContainerProfile? {
    return when (mode) {
        ExecutionMode.LOCAL_PROOT -> {
            if (pickedUri == null) return null
            val bindings = bindingsText.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
            val args = argsText.split(' ').map { it.trim() }.filter { it.isNotEmpty() }
            ContainerProfile(
                id = "", // 由调用方覆写
                name = name,
                rootfsSource = RootfsSource.LocalFile(pickedUri),
                shellPath = shellPath.ifBlank { null },
                extraBindings = bindings,
                extraArgs = args,
                isBuiltin = false,
                mode = ExecutionMode.LOCAL_PROOT
            )
        }

        ExecutionMode.REMOTE_SSH -> {
            if (selectedConnId.isBlank()) return null
            ContainerProfile(
                id = "", // 由调用方覆写
                name = name,
                rootfsSource = RootfsSource.RemoteSsh(selectedConnId, remotePath),
                shellPath = null,
                isBuiltin = false,
                mode = ExecutionMode.REMOTE_SSH
            )
        }
    }
}

/** 保存按钮可用条件：本地镜像需选了文件，远程 SSH 需选了通道。 */
private fun canConfirm(
    mode: ExecutionMode,
    pickedUri: String?,
    selectedConnId: String,
    sshConnections: List<RemoteConnection>
): Boolean = when (mode) {
    ExecutionMode.LOCAL_PROOT -> pickedUri != null
    ExecutionMode.REMOTE_SSH -> sshConnections.isNotEmpty() && selectedConnId.isNotBlank()
}
