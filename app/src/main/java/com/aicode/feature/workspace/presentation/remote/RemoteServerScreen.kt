package com.aicode.feature.workspace.presentation.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.feature.workspace.domain.model.RemoteConnection
import com.aicode.feature.workspace.domain.model.RemoteMount
import com.aicode.R
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Plus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteServerScreen(
    viewModel: RemoteServerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var showAddConnectionDialog by remember { mutableStateOf(false) }
    var showAddMountDialog by remember { mutableStateOf(false) }
    var connectionToEdit by remember { mutableStateOf<RemoteConnection?>(null) }
    var mountToEdit by remember { mutableStateOf<RemoteMount?>(null) }

    val syncUseGitIgnore by viewModel.syncUseGitIgnore.collectAsStateWithLifecycle()
    val maxSyncBatchSize by viewModel.maxSyncBatchSize.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = settingsPageBackground(),
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    title = { Text(stringResource(R.string.remote_workspace_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                        }
                    },
                    actions = {
                        if (selectedTab == 0 || selectedTab == 1) {
                            IconButton(onClick = {
                                if (selectedTab == 0) {
                                    connectionToEdit = null
                                    showAddConnectionDialog = true
                                } else {
                                    mountToEdit = null
                                    showAddMountDialog = true
                                }
                            }) {
                                Icon(FeatherIcons.Plus, contentDescription = stringResource(R.string.common_add))
                            }
                        }
                    }
                )
                SegmentTabs(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    tabs = listOf(
                        stringResource(R.string.remote_tab_connections),
                        stringResource(R.string.remote_tab_mounts),
                        stringResource(R.string.remote_tab_ftp),
                        stringResource(R.string.remote_tab_sync)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (selectedTab) {
                0 -> {
                    if (uiState.connections.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.remote_no_connections),
                            desc = stringResource(R.string.remote_no_connections_desc),
                            onAdd = {
                                connectionToEdit = null
                                showAddConnectionDialog = true
                            }
                        )
                    } else {
                        SettingsList {
                            uiState.connections.forEachIndexed { index, conn ->
                                if (index > 0) {
                                    SettingsDivider()
                                }
                                RemoteConnectionCard(
                                    conn = conn,
                                    onEdit = {
                                        connectionToEdit = it
                                        showAddConnectionDialog = true
                                    },
                                    onDelete = { viewModel.deleteConnection(it.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (uiState.mounts.isEmpty()) {
                        EmptyState(
                            title = stringResource(R.string.remote_no_workspaces),
                            desc = stringResource(R.string.remote_no_workspaces_desc),
                            onAdd = {
                                mountToEdit = null
                                showAddMountDialog = true
                            }
                        )
                    } else {
                        SettingsList {
                            uiState.mounts.forEachIndexed { index, mount ->
                                if (index > 0) {
                                    SettingsDivider()
                                }
                                RemoteMountCard(
                                    mount = mount,
                                    isFailed = mount.id in uiState.failedMountIds,
                                    onEdit = {
                                        mountToEdit = it
                                        showAddMountDialog = true
                                    },
                                    onDelete = { viewModel.deleteMount(it.id) },
                                    onUpload = { viewModel.forceUploadMount(it.id) },
                                    onDownload = { viewModel.forceDownloadMount(it.id) },
                                    onConnect = { viewModel.connectMount(it.id) },
                                    onDisconnect = { viewModel.disconnectMount(it.id) }
                                )
                            }
                        }
                    }
                }
                2 -> WiFiFtpServerSection(viewModel)
                3 -> SyncSettingsSection(
                    useGitIgnore = syncUseGitIgnore,
                    maxSyncBatchSize = maxSyncBatchSize,
                    onUseGitIgnoreChange = { viewModel.setSyncUseGitIgnore(it) },
                    onMaxSyncBatchSizeChange = { viewModel.setMaxSyncBatchSize(it) }
                )
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text(stringResource(R.string.common_close))
                        }
                    }
                ) {
                    Text(error)
                }
            }
        }
    }

    if (showAddConnectionDialog) {
        AddRemoteConnectionDialog(
            initialConnection = connectionToEdit,
            onDismiss = { showAddConnectionDialog = false },
            onAdd = { name, host, port, username, password, protocol ->
                val editing = connectionToEdit
                if (editing != null) {
                    viewModel.updateConnection(editing.id, name, host, port, username, password, protocol)
                } else {
                    viewModel.addConnection(name, host, port, username, password, protocol)
                }
                showAddConnectionDialog = false
            },
            onTestConnection = { host, port, username, password, protocol, onResult ->
                viewModel.testConnection(host, port, username, password, protocol, onResult)
            }
        )
    }

    if (showAddMountDialog) {
        if (uiState.connections.isEmpty()) {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                onDismissRequest = { showAddMountDialog = false },
                title = { Text(stringResource(R.string.remote_hint_title)) },
                text = { Text(stringResource(R.string.remote_add_channel_first)) },
                confirmButton = {
                    TextButton(onClick = { showAddMountDialog = false; selectedTab = 0 }) {
                        Text(stringResource(R.string.remote_go_add))
                    }
                }
            )
        } else {
            AddRemoteMountDialog(
                initialMount = mountToEdit,
                connections = uiState.connections,
                workspaces = uiState.workspaces,
                onDismiss = { showAddMountDialog = false },
                onAdd = { connectionId, remotePath, localWorkspacePath, autoConnect ->
                    val editing = mountToEdit
                    if (editing != null) {
                        viewModel.updateMount(editing.id, connectionId, remotePath, localWorkspacePath, autoConnect)
                    } else {
                        viewModel.addMount(connectionId, remotePath, localWorkspacePath, autoConnect)
                    }
                    showAddMountDialog = false
                },
                onListDirectories = { connectionId, path, onResult ->
                    viewModel.listRemoteDirectories(connectionId, path, onResult)
                }
            )
        }
    }
}

/** iOS 风格分段控件：圆角浅灰容器 + 白色选中块，样式与容器镜像弹窗的分段选择器一致。 */
@Composable
internal fun SegmentTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    tabs: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                RoundedCornerShape(12.dp)
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        tabs.forEachIndexed { index, title ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** 居中空态：标题 + 描述 + 添加按钮，样式与容器镜像空态一致。 */
@Composable
private fun EmptyState(
    title: String,
    desc: String,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.lg)
        )
        Button(onClick = onAdd) {
            Text(stringResource(R.string.common_add))
        }
    }
}

/** 分组列表容器：垂直滚动 + 白色圆角分组，与容器镜像页列表一致。 */
@Composable
private fun SettingsList(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
    ) {
        SettingsGroup(content = content)
    }
}
