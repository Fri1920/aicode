package com.aicode.feature.settings.presentation.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalClipboard
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.FloatingTabBar
import com.aicode.core.ui.FloatingTabItem
import com.aicode.feature.settings.data.local.CustomModelMetadataStore
import com.aicode.feature.settings.data.remote.ModelTestResult
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.model.mergeModelMetadata
import com.aicode.feature.settings.presentation.FetchState
import com.aicode.feature.settings.presentation.SettingsViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Check
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.DownloadCloud
import compose.icons.feathericons.Eye
import compose.icons.feathericons.EyeOff
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Sliders
import compose.icons.feathericons.Trash2
import androidx.compose.ui.res.stringResource
import com.aicode.R


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProviderEditorScreen(
    viewModel: SettingsViewModel,
    initialProvider: AIProviderConfig?,
    onNavigateBack: () -> Unit,
    onSave: (AIProviderConfig) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialProvider?.name ?: "") }
    var apiKey by remember { mutableStateOf(initialProvider?.apiKey ?: "") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(initialProvider?.baseUrl ?: "") }
    var useFullUrl by remember { mutableStateOf(initialProvider?.useFullUrl ?: false) }
    var useResponseApi by remember { mutableStateOf(initialProvider?.useResponseApi ?: false) }
    var anthropicCacheBreakpoints by remember { mutableStateOf(initialProvider?.anthropicCacheBreakpoints ?: true) }
    var openaiChatCacheKey by remember { mutableStateOf(initialProvider?.openaiChatCacheKey ?: false) }
    var isEnabled by remember { mutableStateOf(initialProvider?.isEnabled ?: true) }
    var type by remember { mutableStateOf(initialProvider?.type ?: ProviderType.OPENAI) }
    val providerId = remember { initialProvider?.id ?: System.currentTimeMillis().toString() }
    val models = remember { mutableStateListOf<String>().apply { addAll(initialProvider?.models ?: emptyList()) } }
    val customMetadataStore = remember { CustomModelMetadataStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var customMetadata by remember { mutableStateOf<Map<String, ModelMetadata>>(emptyMap()) }
    var editingModel by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showTypeSheet by remember { mutableStateOf(false) }
    var showAddModelSheet by remember { mutableStateOf(false) }
    var showFetchDialog by remember { mutableStateOf(false) }
    var fetchDialogKey by remember { mutableIntStateOf(0) }

    val fetchState by viewModel.fetchState.collectAsStateWithLifecycle()
    val testResults by viewModel.testResults.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val modelMetadata by viewModel.modelMetadata.collectAsStateWithLifecycle()
    val modelSnapshot = models.toList()

    DisposableEffect(Unit) {
        viewModel.resetFetchState()
        viewModel.clearTestResults()
        onDispose {
            viewModel.resetFetchState()
            viewModel.clearTestResults()
        }
    }

    LaunchedEffect(type, modelSnapshot) {
        viewModel.resolveModelMetadata(providerId, type, modelSnapshot)
    }

    LaunchedEffect(providerId, modelSnapshot) {
        customMetadata = customMetadataStore.all()
    }

    fun currentConfig() = AIProviderConfig(
        id = providerId,
        name = name.ifEmpty { context.getString(R.string.provider_new) },
        type = type,
        apiKey = apiKey,
        baseUrl = baseUrl.ifBlank { defaultProviderBaseUrl(type) },
        useFullUrl = useFullUrl,
        isEnabled = isEnabled,
        defaultModel = initialProvider?.defaultModel ?: "",
        models = models.toList(),
        selectedModel = initialProvider?.selectedModel ?: "",
        useResponseApi = useResponseApi,
        anthropicCacheBreakpoints = anthropicCacheBreakpoints,
        openaiChatCacheKey = openaiChatCacheKey
    )

    // 新建场景下判断用户是否填写了实质内容：名称、API Key、Base URL 任一非空白，或已添加模型。
    // 全空白时退出不应落库，否则会存入一条名为“新提供商”的空记录。
    fun hasSubstantiveInput(): Boolean =
        initialProvider != null ||
            name.isNotBlank() ||
            apiKey.isNotBlank() ||
            baseUrl.isNotBlank() ||
            models.isNotEmpty()

    fun saveCurrent() {
        if (!hasSubstantiveInput()) return
        onSave(currentConfig())
    }

    fun saveAndNavigateBack() {
        saveCurrent()
        onNavigateBack()
    }

    BackHandler {
        saveAndNavigateBack()
    }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(if (initialProvider == null) stringResource(R.string.provider_add) else stringResource(R.string.provider_edit)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = { saveAndNavigateBack() }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (initialProvider != null) {
                        IconButton(onClick = { onDelete(initialProvider.id) }) {
                            Icon(
                                FeatherIcons.Trash2,
                                contentDescription = stringResource(R.string.provider_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = {
                        selectedTab = 1
                        showAddModelSheet = true
                    }) {
                        Icon(FeatherIcons.Plus, contentDescription = stringResource(R.string.provider_add_model))
                    }
                }
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // ── 基本信息 ──
                    SettingsGroupHeader(text = stringResource(R.string.provider_section_basic))
                    SettingsGroup {
                        ProviderTextFieldRow(
                            label = stringResource(R.string.common_name),
                            value = name,
                            onValueChange = { name = it }
                        )
                        SettingsDivider()
                        ProviderTextFieldRow(
                            label = "API Key",
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            visualTransformation = if (apiKeyVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailing = {
                                Icon(
                                    imageVector = if (apiKeyVisible) FeatherIcons.EyeOff else FeatherIcons.Eye,
                                    contentDescription = stringResource(
                                        if (apiKeyVisible) R.string.provider_hide_api_key else R.string.provider_show_api_key
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { apiKeyVisible = !apiKeyVisible }
                                        .padding(2.dp)
                                )
                            }
                        )
                        SettingsDivider()
                        ProviderTextFieldRow(
                            label = "Base URL",
                            value = baseUrl,
                            onValueChange = { baseUrl = it }
                        )
                        SettingsDivider()
                        SettingsRow(
                            icon = null,
                            title = stringResource(R.string.provider_section_type),
                            onClick = { showTypeSheet = true },
                            trailing = {
                                Text(
                                    text = providerTypeLabel(type),
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

                    // ── 选项 ──
                    SettingsGroupHeader(text = stringResource(R.string.provider_section_options))
                    SettingsGroup {
                        ProviderSwitchRow(
                            title = stringResource(R.string.common_enabled),
                            checked = isEnabled,
                            onCheckedChange = { isEnabled = it }
                        )
                        SettingsDivider()
                        ProviderSwitchRow(
                            title = stringResource(R.string.provider_full_url),
                            subtitle = stringResource(R.string.provider_full_url_desc),
                            checked = useFullUrl,
                            onCheckedChange = { useFullUrl = it }
                        )
                        if (type == ProviderType.OPENAI) {
                            SettingsDivider()
                            ProviderSwitchRow(
                                title = stringResource(R.string.provider_response_api),
                                checked = useResponseApi,
                                onCheckedChange = { useResponseApi = it }
                            )
                            SettingsDivider()
                            ProviderSwitchRow(
                                title = stringResource(R.string.provider_cache_openai_chat_title),
                                subtitle = stringResource(R.string.provider_cache_openai_chat_subtitle),
                                checked = openaiChatCacheKey,
                                onCheckedChange = { openaiChatCacheKey = it }
                            )
                        }
                        if (type == ProviderType.ANTHROPIC) {
                            SettingsDivider()
                            ProviderSwitchRow(
                                title = stringResource(R.string.provider_cache_anthropic_title),
                                subtitle = stringResource(R.string.provider_cache_anthropic_subtitle),
                                checked = anthropicCacheBreakpoints,
                                onCheckedChange = { anthropicCacheBreakpoints = it }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    // ── 模型管理 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.provider_models_count, models.size),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                                Color(0xFF8E8E93)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                fetchDialogKey++
                                showFetchDialog = true
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(FeatherIcons.DownloadCloud, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.xs))
                            Text(stringResource(R.string.provider_fetch_models))
                        }
                    }
                    if (models.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Text(
                                    text = stringResource(R.string.provider_no_models),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.provider_no_models_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        SettingsGroup {
                            models.forEachIndexed { index, model ->
                                if (index > 0) {
                                    SettingsDivider()
                                }
                                ProviderModelRow(
                                    model = model,
                                    metadata = mergeModelMetadata(model, modelMetadata[model], customMetadata["$providerId:$model"]),
                                    testing = model in testing,
                                    result = testResults[model],
                                    onTest = { viewModel.testModel(currentConfig(), model) },
                                    onEdit = {
                                        editingModel = model
                                        showAddModelSheet = true
                                    },
                                    onRemove = {
                                        models.remove(model)
                                        scope.launch {
                                            customMetadataStore.remove(providerId, model)
                                            customMetadata = customMetadataStore.all()
                                        }
                                        saveCurrent()
                                    }
                                )
                            }
                        }
                    }
                }
            }
            }

            FloatingTabBar(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                items = listOf(
                    FloatingTabItem(FeatherIcons.Sliders, stringResource(R.string.provider_config)),
                    FloatingTabItem(FeatherIcons.Cpu, stringResource(R.string.common_model))
                ),
                maskColor = settingsPageBackground(),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showTypeSheet) {
        ProviderTypeSelectionSheet(
            selected = type,
            onSelected = { type = it },
            onDismiss = { showTypeSheet = false }
        )
    }

    if (showAddModelSheet) {
        key(editingModel) {
            AddModelSheet(
                existingModels = models,
                title = if (editingModel != null) {
                    stringResource(R.string.provider_edit_model)
                } else {
                    stringResource(R.string.provider_add_model)
                },
                confirmLabel = if (editingModel != null) {
                    stringResource(R.string.common_save)
                } else {
                    stringResource(R.string.common_add)
                },
                initial = editingModel?.let { mergeModelMetadata(it, modelMetadata[it], customMetadata["$providerId:$it"]) },
                onSave = { model, meta ->
                    val editing = editingModel
                    if (editing != null && model != editing) {
                        val idx = models.indexOf(editing)
                        if (idx >= 0) models[idx] = model else models.add(model)
                    } else if (model !in models) {
                        models.add(model)
                    }
                    scope.launch {
                        if (editing != null && model != editing) {
                            customMetadataStore.remove(providerId, editing)
                        }
                        customMetadataStore.put(providerId, model, meta)
                        customMetadata = customMetadataStore.all()
                    }
                    saveCurrent()
                    editingModel = null
                    showAddModelSheet = false
                },
                onDismiss = {
                    editingModel = null
                    showAddModelSheet = false
                }
            )
        }
    }

    // 模型拉取结果弹窗
    if (showFetchDialog) {
        key(fetchDialogKey) {
            FetchModelsDialog(
                fetchState = fetchState,
                modelMetadata = modelMetadata,
                existingModels = models,
                onFetchModels = { viewModel.fetchModels(currentConfig()) },
                onAddModel = { m ->
                    if (m !in models) {
                        models.add(m)
                        saveCurrent()
                    }
                },
                onDismiss = {
                    showFetchDialog = false
                    viewModel.resetFetchState()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddModelSheet(
    existingModels: List<String>,
    title: String,
    confirmLabel: String,
    initial: ModelMetadata?,
    onSave: (String, ModelMetadata) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var modelName by remember { mutableStateOf(initial?.id ?: "") }
    var supportsVision by remember { mutableStateOf(initial?.supportsVision ?: false) }
    var supportsImageOutput by remember { mutableStateOf(initial?.supportsImageOutput ?: false) }
    var supportsTools by remember { mutableStateOf(initial?.supportsTools ?: false) }
    var supportsReasoning by remember { mutableStateOf(initial?.supportsReasoning ?: false) }
    var inputTokens by remember { mutableStateOf((initial?.inputTokens ?: initial?.contextTokens?.takeIf { it > 0 })?.toString() ?: "") }
    var outputTokens by remember { mutableStateOf(initial?.outputTokens?.toString() ?: "") }
    val trimmedModel = modelName.trim()
    val duplicate = existingModels.any { it == trimmedModel && it != initial?.id }
    val canSave = trimmedModel.isNotEmpty() && !duplicate

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = settingsPageBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )

            // 模型名称 + 上下文窗口（上下结构，同一卡片）
            SettingsGroup {
                ProviderTextFieldRow(
                    label = stringResource(R.string.provider_model_name),
                    value = modelName,
                    onValueChange = { modelName = it }
                )
                if (duplicate) {
                    Text(
                        text = stringResource(R.string.provider_model_already_added),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg)
                            .padding(bottom = Spacing.sm)
                    )
                }
                SettingsDivider()
                ProviderTextFieldRow(
                    label = stringResource(R.string.provider_model_context_input),
                    value = inputTokens,
                    onValueChange = { inputTokens = it }
                )
                SettingsDivider()
                ProviderTextFieldRow(
                    label = stringResource(R.string.provider_model_context_output),
                    value = outputTokens,
                    onValueChange = { outputTokens = it }
                )
            }

            SectionLabel(stringResource(R.string.provider_model_input_mode))
            MultiSegmentRow(
                firstLabel = stringResource(R.string.provider_model_mode_text),
                secondLabel = stringResource(R.string.provider_model_mode_image),
                firstChecked = true,
                secondChecked = supportsVision,
                onFirstChange = {},
                onSecondChange = { supportsVision = it }
            )

            SectionLabel(stringResource(R.string.provider_model_output_mode))
            MultiSegmentRow(
                firstLabel = stringResource(R.string.provider_model_mode_text),
                secondLabel = stringResource(R.string.provider_model_mode_image),
                firstChecked = true,
                secondChecked = supportsImageOutput,
                onFirstChange = {},
                onSecondChange = { supportsImageOutput = it }
            )

            SectionLabel(stringResource(R.string.provider_model_capabilities))
            MultiSegmentRow(
                firstLabel = stringResource(R.string.provider_model_capability_tools),
                secondLabel = stringResource(R.string.provider_model_capability_reasoning),
                firstChecked = supportsTools,
                secondChecked = supportsReasoning,
                onFirstChange = { supportsTools = it },
                onSecondChange = { supportsReasoning = it }
            )

            Button(
                onClick = {
                    val input = inputTokens.trim().toIntOrNull()
                    val output = outputTokens.trim().toIntOrNull()
                    val meta = ModelMetadata(
                        id = trimmedModel,
                        displayName = trimmedModel,
                        contextTokens = input ?: 0,
                        inputTokens = input,
                        outputTokens = output,
                        supportsVision = supportsVision,
                        supportsImageOutput = supportsImageOutput,
                        supportsTools = supportsTools,
                        supportsReasoning = supportsReasoning
                    )
                    onSave(trimmedModel, meta)
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Icon(FeatherIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text(confirmLabel)
            }
        }
    }
}

/** 双栏多选分段选择器（可独立选中），选中项以主题色高亮并带勾选。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MultiSegmentRow(
    firstLabel: String,
    secondLabel: String,
    firstChecked: Boolean,
    secondChecked: Boolean,
    onFirstChange: (Boolean) -> Unit,
    onSecondChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    MultiChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
    ) {
        SegmentedButton(
            checked = firstChecked,
            onCheckedChange = onFirstChange,
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = segmentedSelectedColors(),
            icon = { if (firstChecked) SelectedCheck() }
        ) {
            Text(firstLabel)
        }
        SegmentedButton(
            checked = secondChecked,
            onCheckedChange = onSecondChange,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = segmentedSelectedColors(),
            icon = { if (secondChecked) SelectedCheck() }
        ) {
            Text(secondLabel)
        }
    }
}

/** 分段选中态配色：容器用主题色（蓝），文字与勾选用其上的 onPrimary 白色。 */
@Composable
private fun segmentedSelectedColors(): SegmentedButtonColors =
    SegmentedButtonDefaults.colors(
        activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
        activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )

/** 分段组小标题：灰色小字、紧凑间距，直接铺在弹窗背景上（无卡片）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        fontWeight = FontWeight.Normal,
        color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.xs)
    )
}

@Composable
private fun SelectedCheck() {
    Icon(
        imageVector = FeatherIcons.Check,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onPrimaryContainer
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FetchModelsDialog(
    fetchState: FetchState,
    modelMetadata: Map<String, ModelMetadata>,
    existingModels: List<String>,
    onFetchModels: () -> Unit,
    onAddModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        // Wait for bottom sheet animation to smooth out before firing network request
        delay(300)
        onFetchModels()
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = settingsPageBackground()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.provider_fetch_models),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
            )

            ModelSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.provider_filter_models_hint),
                modifier = Modifier.padding(horizontal = Spacing.lg)
            )

            when (fetchState) {
                is FetchState.Loading -> {
                    FetchModelsSkeleton()
                }
                is FetchState.Error -> {
                    SettingsGroup {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 360.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.provider_fetch_failed, fetchState.message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                is FetchState.Success -> {
                    val newModels = fetchState.models.filter { it !in existingModels && it.contains(searchQuery, ignoreCase = true) }
                    if (newModels.isEmpty()) {
                        SettingsGroup {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 360.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(R.string.provider_no_matching_models), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        // 按品牌分组，每个分类一个独立卡片。"other" 分组永远在最后，其他按显示名称排序。
                        val grouped = newModels.groupBy { m -> modelBrandKey(m) }
                            .toSortedMap(compareBy<String> { it == "other" }.thenBy { brandDisplayName(context, it) })

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 360.dp, max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            grouped.forEach { (brandKey, models) ->
                                item(key = "header_$brandKey") {
                                    SettingsGroupHeader("${brandDisplayName(context, brandKey)} (${models.size})")
                                }
                                item(key = "card_$brandKey") {
                                    SettingsGroup {
                                        models.forEachIndexed { index, m ->
                                            if (index > 0) {
                                                SettingsDivider()
                                            }
                                            FetchModelRow(
                                                model = m,
                                                metadata = modelMetadata[m],
                                                onAdd = { onAddModel(m) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    SettingsGroup {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 360.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.provider_please_wait), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** 拉取模型加载骨架屏：模拟品牌标题 + 模型行占位块，避免加载时空白/转圈。 */
@Composable
private fun FetchModelsSkeleton() {
    val block = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        Color(0xFFE5E5EA)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        repeat(3) {
            SkeletonBlock(
                width = 80.dp,
                height = 14.dp,
                color = block,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
            SettingsGroup {
                repeat(3) { idx ->
                    if (idx > 0) {
                        SettingsDivider()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SkeletonBlock(width = 24.dp, height = 24.dp, color = block, shape = RoundedCornerShape(8.dp))
                        Spacer(Modifier.width(Spacing.md))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SkeletonBlock(width = 120.dp, height = 14.dp, color = block)
                            SkeletonBlock(width = 80.dp, height = 10.dp, color = block)
                        }
                        SkeletonBlock(width = 48.dp, height = 24.dp, color = block, shape = RoundedCornerShape(50))
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    width: Dp,
    height: Dp,
    color: Color,
    shape: Shape = RoundedCornerShape(4.dp),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(color)
    )
}

internal fun defaultProviderBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/"
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/"
    else -> "https://api.openai.com/"
}

/** 分组内输入行：上方小标题 + 全宽输入框，可选密文转换与尾随操作。 */
@Composable
private fun ProviderTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                Color(0xFF8E8E93)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(bottom = Spacing.xs)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                        Color(0xFF0F0F0F)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                singleLine = true,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            )
            trailing?.invoke()
        }
    }
}

/** 分组内开关行：标题 + 可选副标题 + 右侧 Switch。 */
@Composable
private fun ProviderSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                    Color(0xFF0F0F0F)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/** 提供商类型选择底部弹窗，样式与主题选择弹窗一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderTypeSelectionSheet(
    selected: ProviderType,
    onSelected: (ProviderType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.provider_section_type),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            ProviderType.entries.forEach { providerType ->
                val isSelected = providerType == selected
                Surface(
                    onClick = {
                        onDismiss()
                        onSelected(providerType)
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = providerTypeLabel(providerType),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = FeatherIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun providerTypeLabel(type: ProviderType): String = when (type) {
    ProviderType.OPENAI -> "OpenAI"
    ProviderType.ANTHROPIC -> "Anthropic"
    ProviderType.GEMINI -> "Gemini"
}