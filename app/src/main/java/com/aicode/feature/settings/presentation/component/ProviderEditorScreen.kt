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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.data.remote.ModelTestResult
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import com.aicode.feature.settings.domain.model.ProviderType
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
    var isEnabled by remember { mutableStateOf(initialProvider?.isEnabled ?: true) }
    var type by remember { mutableStateOf(initialProvider?.type ?: ProviderType.OPENAI) }
    val providerId = remember { initialProvider?.id ?: System.currentTimeMillis().toString() }
    val models = remember { mutableStateListOf<String>().apply { addAll(initialProvider?.models ?: emptyList()) } }
    val modelCapabilities = remember {
        mutableStateMapOf<String, ModelMetadata>().apply {
            putAll(initialProvider?.modelCapabilities ?: emptyMap())
        }
    }
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
        viewModel.resolveModelMetadata(type, modelSnapshot)
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
        isActive = initialProvider?.isActive ?: false,
        models = models.toList(),
        selectedModel = initialProvider?.selectedModel ?: "",
        useResponseApi = useResponseApi,
        modelCapabilities = modelCapabilities.toMap()
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
        bottomBar = {
            NavigationBar(
                containerColor = settingsPageBackground(),
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    icon = { Icon(FeatherIcons.Sliders, contentDescription = stringResource(R.string.provider_config)) },
                    label = { Text(stringResource(R.string.provider_config)) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(FeatherIcons.Cpu, contentDescription = stringResource(R.string.common_model)) },
                    label = { Text(stringResource(R.string.common_model)) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
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
                                    metadata = effectiveModelMetadata(model, modelCapabilities, modelMetadata),
                                    testing = model in testing,
                                    result = testResults[model],
                                    onTest = { viewModel.testModel(currentConfig(), model) },
                                    onRemove = {
                                        models.remove(model)
                                        modelCapabilities.remove(model)
                                        saveCurrent()
                                    }
                                )
                            }
                        }
                    }
                }
            }
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
        AddModelSheet(
            existingModels = models,
            onAddModel = { model, meta ->
                if (model !in models) {
                    models.add(model)
                    modelCapabilities[model] = meta
                    saveCurrent()
                }
            },
            onDismiss = { showAddModelSheet = false }
        )
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
    onAddModel: (String, ModelMetadata) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modelName by remember { mutableStateOf("") }
    var supportsVision by remember { mutableStateOf(false) }
    var supportsTools by remember { mutableStateOf(false) }
    var inputTokens by remember { mutableStateOf("") }
    var outputTokens by remember { mutableStateOf("") }
    val trimmedModel = modelName.trim()
    val duplicate = existingModels.any { it == trimmedModel }
    val canAdd = trimmedModel.isNotEmpty() && !duplicate

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
                .padding(bottom = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = stringResource(R.string.provider_add_model),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text(stringResource(R.string.provider_model_name)) },
                placeholder = { Text(stringResource(R.string.provider_model_name_hint)) },
                singleLine = true,
                isError = duplicate,
                modifier = Modifier.fillMaxWidth()
            )
            if (duplicate) {
                Text(
                    text = stringResource(R.string.provider_model_already_added),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.provider_model_supports_vision),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = supportsVision,
                    onCheckedChange = { supportsVision = it }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.provider_model_supports_tools),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = supportsTools,
                    onCheckedChange = { supportsTools = it }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value = inputTokens,
                    onValueChange = { inputTokens = it },
                    label = { Text(stringResource(R.string.provider_model_context_input)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = outputTokens,
                    onValueChange = { outputTokens = it },
                    label = { Text(stringResource(R.string.provider_model_context_output)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    enabled = canAdd,
                    onClick = {
                        val input = inputTokens.trim().toIntOrNull()
                        val output = outputTokens.trim().toIntOrNull()
                        val meta = ModelMetadata(
                            id = trimmedModel,
                            contextTokens = input ?: 0,
                            inputTokens = input,
                            outputTokens = output,
                            supportsVision = supportsVision,
                            supportsTools = supportsTools
                        )
                        onAddModel(trimmedModel, meta)
                        onDismiss()
                    }
                ) {
                    Icon(FeatherIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.common_add))
                }
            }
        }
    }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val collapsedBrands = remember { mutableStateMapOf<String, Boolean>() }
    
    LaunchedEffect(Unit) {
        // Wait for bottom sheet animation to smooth out before firing network request
        delay(300)
        onFetchModels()
    }
    
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = true,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight * 0.85f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.lg)
                        .padding(bottom = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.provider_filter_models_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50)
                    )

                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        when (fetchState) {
                            is FetchState.Loading -> {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(Spacing.md))
                                    Text(stringResource(R.string.provider_fetching), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is FetchState.Error -> {
                                Text(
                                    stringResource(R.string.provider_fetch_failed, fetchState.message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            is FetchState.Success -> {
                                val newOnes = fetchState.models.filter { it !in existingModels && it.contains(searchQuery, ignoreCase = true) }
                                if (newOnes.isEmpty()) {
                                    Text(stringResource(R.string.provider_no_matching_models), style = MaterialTheme.typography.bodyMedium)
                                } else {
                                    // 按品牌分组，分类 header 可折叠。"other" 分组永远在最后，其他按显示名称排序。
                                    val grouped = newOnes.groupBy { m -> modelBrandKey(m) }
                                        .toSortedMap(compareBy<String> { it == "other" }.thenBy { brandDisplayName(context, it) })

                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        grouped.forEach { (brandKey, models) ->
                                            item(key = "header_$brandKey") {
                                                val expanded = collapsedBrands[brandKey] != true
                                                val brandName = brandDisplayName(context, brandKey)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 44.dp)
                                                        .clickable { collapsedBrands[brandKey] = expanded }
                                                        .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "$brandName (${models.size})",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                        contentDescription = if (expanded) stringResource(R.string.provider_collapse_brand, brandName) else stringResource(R.string.provider_expand_brand, brandName),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            if (collapsedBrands[brandKey] != true) {
                                                items(models, key = { "${brandKey}_$it" }) { m ->
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
                            else -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.provider_please_wait), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
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

/** 合并手动配置与自动解析的模型能力，手动配置优先（窗口未填时保留自动值）。 */
private fun effectiveModelMetadata(
    model: String,
    manual: Map<String, ModelMetadata>,
    auto: Map<String, ModelMetadata>
): ModelMetadata? {
    val m = manual[model] ?: return auto[model]
    val a = auto[model] ?: return m
    return a.copy(
        supportsVision = m.supportsVision,
        supportsTools = m.supportsTools,
        contextTokens = m.contextTokens.takeIf { it > 0 } ?: a.contextTokens,
        inputTokens = m.inputTokens ?: a.inputTokens,
        outputTokens = m.outputTokens ?: a.outputTokens
    )
}