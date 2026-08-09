package com.aicode.feature.settings.presentation.component

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.core.theme.Spacing
import com.aicode.core.util.LogLevel
import com.aicode.R
import com.aicode.feature.agent.domain.mcp.McpServerEntry
import com.aicode.feature.agent.domain.mcp.McpServerConfig
import com.aicode.feature.agent.domain.mcp.McpServerStatus
import com.aicode.feature.backup.presentation.BackupSection
import com.aicode.feature.settings.data.repository.AppThemeMode
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import com.aicode.feature.settings.presentation.SettingsViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Box
import compose.icons.feathericons.Cloud
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Globe
import compose.icons.feathericons.HardDrive
import compose.icons.feathericons.Info
import compose.icons.feathericons.Lock
import compose.icons.feathericons.Moon
import compose.icons.feathericons.Plus
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Save
import compose.icons.feathericons.Server
import compose.icons.feathericons.Shield

/** 设置页内部二级菜单分区。Menu 为首页菜单，其余为各自的二级页。 */
internal enum class SettingsSection(@param:StringRes val titleRes: Int) {
    Menu(R.string.settings_title),
    Providers(R.string.settings_providers),
    ProviderEditor(R.string.settings_provider_editor),
    DefaultModels(R.string.settings_default_models),
    Mcp(R.string.settings_mcp),
    Container(R.string.settings_container),
    Log(R.string.settings_log),
    LogViewer(R.string.settings_log_viewer),
    Permissions(R.string.settings_permissions),
    AppPermissions(R.string.settings_app_permissions),
    RemoteServers(R.string.settings_remote_servers),
    Backup(R.string.settings_backup),
    About(R.string.settings_about)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onStopAllAndCloseTerminal: () -> Unit = {}
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val logLevel by viewModel.logLevel.collectAsStateWithLifecycle()
    val logViewerState by viewModel.logViewerState.collectAsStateWithLifecycle()
    val mcpEntries by viewModel.mcpEntries.collectAsStateWithLifecycle()
    val mcpStatuses by viewModel.mcpStatuses.collectAsStateWithLifecycle()
    val mcpReloading by viewModel.mcpReloading.collectAsStateWithLifecycle()
    val globalRules by viewModel.globalRules.collectAsStateWithLifecycle()
    val projectRules by viewModel.projectRules.collectAsStateWithLifecycle()
    val currentProjectName by viewModel.currentProjectName.collectAsStateWithLifecycle()
    val keepaliveEnabled by viewModel.keepaliveEnabled.collectAsStateWithLifecycle()
    val agentSoundEnabled by viewModel.agentSoundEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val visionProviderId by viewModel.visionProviderId.collectAsStateWithLifecycle()
    val visionModel by viewModel.visionModel.collectAsStateWithLifecycle()
    val compactionProviderId by viewModel.compactionProviderId.collectAsStateWithLifecycle()
    val compactionModel by viewModel.compactionModel.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    val containerProfiles by viewModel.profiles.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val containerOsMap by viewModel.containerOsMap.collectAsStateWithLifecycle()
    val remoteConnections by viewModel.remoteConnections.collectAsStateWithLifecycle()

    val currentLanguageDisplayName = if (languageTag.isNullOrBlank()) {
        stringResource(R.string.language_follow_system)
    } else {
        com.aicode.core.util.LanguageRegistry.languages.firstOrNull { it.tag == languageTag }?.displayName
            ?: stringResource(R.string.language_follow_system)
    }


    var section by remember { mutableStateOf(SettingsSection.Menu) }
    var logReturnSection by remember { mutableStateOf(SettingsSection.Menu) }
    var editingProvider by remember { mutableStateOf<AIProviderConfig?>(null) }
    var showMcpDialog by remember { mutableStateOf(false) }
    var editingMcp by remember { mutableStateOf<McpServerEntry?>(null) }
    var showContainerAddSheet by remember { mutableStateOf(false) }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }

    // 处于二级页时，系统返回键先回到上一层；首页时交还给上层导航。
    BackHandler(enabled = section != SettingsSection.Menu) {
        when (section) {
            SettingsSection.ProviderEditor -> section = SettingsSection.Providers
            SettingsSection.LogViewer -> section = logReturnSection
            else -> section = SettingsSection.Menu
        }
    }

    // 提供商编辑为独立全屏页，直接渲染（不嵌套 Scaffold）
    if (section == SettingsSection.ProviderEditor) {
        ProviderEditorScreen(
            viewModel = viewModel,
            initialProvider = editingProvider,
            onNavigateBack = { section = SettingsSection.Providers },
            onSave = { provider ->
                viewModel.saveProvider(provider)
            },
            onDelete = { id ->
                viewModel.deleteProvider(id)
                section = SettingsSection.Providers
            }
        )
        return
    }

    if (section == SettingsSection.RemoteServers) {
        com.aicode.feature.workspace.presentation.remote.RemoteServerScreen(
            onNavigateBack = { section = SettingsSection.Menu }
        )
        return
    }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(section.titleRes)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        if (section == SettingsSection.Menu) {
                            onNavigateBack()
                        } else if (section == SettingsSection.LogViewer) {
                            section = logReturnSection
                        } else {
                            section = SettingsSection.Menu
                        }
                    }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    when (section) {
                        SettingsSection.Providers -> IconButton(onClick = {
                            editingProvider = null
                            section = SettingsSection.ProviderEditor
                        }) {
                            Icon(FeatherIcons.Plus, contentDescription = stringResource(R.string.settings_add_provider))
                        }
                        SettingsSection.Mcp -> {
                            IconButton(onClick = { viewModel.reloadMcp() }) {
                                if (mcpReloading) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        FeatherIcons.RefreshCw,
                                        contentDescription = stringResource(R.string.settings_reconnect),
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                editingMcp = null
                                showMcpDialog = true
                            }) {
                                Icon(
                                    FeatherIcons.Plus,
                                    contentDescription = stringResource(R.string.settings_add_mcp_server),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        SettingsSection.Container -> IconButton(onClick = { showContainerAddSheet = true }) {
                            Icon(FeatherIcons.Plus, contentDescription = stringResource(R.string.container_add_image))
                        }
                        SettingsSection.LogViewer -> {
                            IconButton(onClick = { viewModel.refreshLogs() }) {
                                Icon(FeatherIcons.RefreshCw, contentDescription = stringResource(R.string.settings_refresh_logs))
                            }
                        }
                        else -> {}
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (section) {
                SettingsSection.Menu -> SettingsMenu(
                    themeMode = themeMode,
                    currentLanguageDisplayName = currentLanguageDisplayName,
                    onOpenThemeSheet = { showThemeSheet = true },
                    onOpenLanguageSheet = { showLanguageSheet = true },
                    onOpen = {
                        if (it == SettingsSection.LogViewer) {
                            logReturnSection = SettingsSection.Menu
                            viewModel.refreshLogs(filterServerName = null)
                        }
                        section = it
                    }
                )
                SettingsSection.Providers -> ProvidersSection(
                    providers = providers,
                    onEdit = {
                        editingProvider = it
                        section = SettingsSection.ProviderEditor
                    }
                )
                SettingsSection.DefaultModels -> DefaultModelsSection(
                    providers = providers,
                    visionProviderId = visionProviderId,
                    visionModel = visionModel,
                    compactionProviderId = compactionProviderId,
                    compactionModel = compactionModel,
                    modelMetadata = modelMetadata,
                    onLoadMetadata = { viewModel.loadAllModelMetadata() },
                    onSelectVisionModel = { pid, m -> viewModel.setVisionModel(pid, m) },
                    onClearVisionModel = { viewModel.clearVisionModel() },
                    onSelectCompactionModel = { pid, m -> viewModel.setCompactionModel(pid, m) },
                    onClearCompactionModel = { viewModel.clearCompactionModel() }
                )
                SettingsSection.Mcp -> McpSection(
                    entries = mcpEntries,
                    statuses = mcpStatuses,
                    reloading = mcpReloading,
                    onReload = { viewModel.reloadMcp() },
                    onToggle = { name, enabled, scope -> viewModel.setMcpServerEnabled(name, enabled, scope) },
                    onEdit = {
                        editingMcp = it
                        showMcpDialog = true
                    },
                    onDelete = { name, scope -> viewModel.deleteMcpServer(name, scope) }
                )
                SettingsSection.Container -> ContainerSection(
                    profiles = containerProfiles,
                    activeProfileId = activeProfileId,
                    osMap = containerOsMap,
                    showAddSheetExternal = showContainerAddSheet,
                    onDismissAddSheet = { showContainerAddSheet = false },
                    onSelect = { viewModel.setActiveContainerProfile(it) },
                    onSaveCustom = { viewModel.saveCustomContainerProfile(it) },
                    onEditCustom = { viewModel.editCustomContainerProfile(it) },
                    onDeleteProfile = { viewModel.deleteContainerProfile(it) },
                    onSwitchConfirmed = onStopAllAndCloseTerminal,
                    onResetProfile = { viewModel.resetContainer(it) },
                    onRestoreBuiltin = { viewModel.restoreBuiltinAlpine() },
                    remoteConnections = remoteConnections
                )
                SettingsSection.Log -> LogSection(
                    current = logLevel,
                    onSelect = { viewModel.setLogLevel(it) }
                )
                SettingsSection.LogViewer -> LogViewerSection(
                    state = logViewerState,
                    onSelectFile = { viewModel.selectLogFile(it) }
                )
                SettingsSection.Permissions -> PermissionsSection(
                    projectName = currentProjectName,
                    projectRules = projectRules,
                    globalRules = globalRules,
                    onDeleteProject = { viewModel.deleteProjectRule(it) },
                    onPromote = { viewModel.promoteRuleToGlobal(it) },
                    onDeleteGlobal = { viewModel.deleteGlobalRule(it) }
                )
                SettingsSection.AppPermissions -> AppPermissionsSection(
                    keepaliveEnabled = keepaliveEnabled,
                    onToggleKeepalive = { viewModel.setKeepaliveEnabled(it) },
                    agentSoundEnabled = agentSoundEnabled,
                    onToggleAgentSound = { viewModel.setAgentSoundEnabled(it) }
                )
                SettingsSection.Backup -> {
                    val backupViewModel: com.aicode.feature.backup.presentation.BackupViewModel =
                        androidx.hilt.navigation.compose.hiltViewModel()
                    BackupSection(viewModel = backupViewModel)
                }
                SettingsSection.ProviderEditor -> {} // 已在上方 early return 处理
                SettingsSection.RemoteServers -> {} // 已在上方 early return 处理
                SettingsSection.About -> AboutSection()
            }
        }
    }

    if (showMcpDialog) {
        McpServerEditDialog(
            initial = editingMcp?.server,
            initialScope = editingMcp?.scope,
            tools = viewModel.getMcpServerTools(editingMcp?.server?.name),
            onRefreshTools = { editingMcp?.let { viewModel.reloadMcpServer(it.server.name) } },
            onOpenLogs = editingMcp?.let { existing ->
                {
                    showMcpDialog = false
                    logReturnSection = SettingsSection.Mcp
                    viewModel.refreshLogs(filterServerName = existing.server.name)
                    section = SettingsSection.LogViewer
                }
            },
            onDismiss = { showMcpDialog = false },
            onSave = { config, scope ->
                viewModel.upsertMcpServer(editingMcp?.server?.name, editingMcp?.scope, config, scope)
                showMcpDialog = false
            }
        )
    }

    if (showThemeSheet) {
        ThemeSelectionSheet(
            selected = themeMode,
            onSelected = { viewModel.setThemeMode(it) },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showLanguageSheet) {
        LanguageSelectionSheet(
            currentTag = languageTag,
            onSelect = { viewModel.setLanguage(it) },
            onDismiss = { showLanguageSheet = false }
        )
    }
}

/** 设置首页：每个分区一个可点击的二级菜单入口。 */
@Composable
internal fun SettingsMenu(
    themeMode: AppThemeMode,
    currentLanguageDisplayName: String,
    onOpenThemeSheet: () -> Unit,
    onOpenLanguageSheet: () -> Unit,
    onOpen: (SettingsSection) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // ── AI 配置 ──
        SettingsGroupHeader(text = stringResource(R.string.settings_category_ai))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Cloud,
                title = stringResource(SettingsSection.Providers.titleRes),
                onClick = { onOpen(SettingsSection.Providers) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Cpu,
                title = stringResource(SettingsSection.DefaultModels.titleRes),
                onClick = { onOpen(SettingsSection.DefaultModels) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Box,
                title = stringResource(SettingsSection.Mcp.titleRes),
                onClick = { onOpen(SettingsSection.Mcp) }
            )
        }

        // ── 运行环境 ──
        SettingsGroupHeader(text = stringResource(R.string.settings_category_environment))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.HardDrive,
                title = stringResource(SettingsSection.Container.titleRes),
                onClick = { onOpen(SettingsSection.Container) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Server,
                title = stringResource(SettingsSection.RemoteServers.titleRes),
                onClick = { onOpen(SettingsSection.RemoteServers) }
            )
        }

        // ── 工具与权限 ──
        SettingsGroupHeader(text = stringResource(R.string.settings_category_tools))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Lock,
                title = stringResource(SettingsSection.Permissions.titleRes),
                onClick = { onOpen(SettingsSection.Permissions) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Shield,
                title = stringResource(SettingsSection.AppPermissions.titleRes),
                onClick = { onOpen(SettingsSection.AppPermissions) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.FileText,
                title = stringResource(SettingsSection.Log.titleRes),
                onClick = { onOpen(SettingsSection.Log) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.FileText,
                title = stringResource(SettingsSection.LogViewer.titleRes),
                onClick = { onOpen(SettingsSection.LogViewer) }
            )
        }

        // ── 外观与语言 ──
        SettingsGroupHeader(text = stringResource(R.string.settings_category_appearance))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Moon,
                title = stringResource(R.string.settings_theme_title),
                onClick = onOpenThemeSheet,
                trailing = {
                    Text(
                        text = stringResource(themeMode.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                            Color(0xFF8E9094)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Globe,
                title = stringResource(R.string.settings_language),
                onClick = onOpenLanguageSheet,
                trailing = {
                    Text(
                        text = currentLanguageDisplayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                            Color(0xFF8E9094)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            )
        }

        // ── 系统 ──
        SettingsGroupHeader(text = stringResource(R.string.settings_category_system))
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Save,
                title = stringResource(SettingsSection.Backup.titleRes),
                onClick = { onOpen(SettingsSection.Backup) }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Info,
                title = stringResource(SettingsSection.About.titleRes),
                onClick = { onOpen(SettingsSection.About) }
            )
        }
    }
}
