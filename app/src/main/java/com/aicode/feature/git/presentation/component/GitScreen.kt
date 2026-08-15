package com.aicode.feature.git.presentation.component

import androidx.activity.compose.BackHandler
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.presentation.component.settingsLightMode
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.presentation.CredentialViewModel
import com.aicode.feature.credentials.presentation.component.CredentialEditorSheet
import com.aicode.feature.credentials.presentation.component.CredentialListSection
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.git.domain.model.GitTab
import com.aicode.feature.git.presentation.GitViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.GitCommit
import compose.icons.feathericons.Key
import compose.icons.feathericons.Plus
import compose.icons.feathericons.RefreshCw

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    viewModel: GitViewModel,
    credentialViewModel: CredentialViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val credState by credentialViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // toast → Snackbar 一次性消费。
    LaunchedEffect(state.toast, credState.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
        credState.toast?.let {
            snackbarHostState.showSnackbar(it)
            credentialViewModel.consumeToast()
        }
    }

    var showCommitDialog by remember { mutableStateOf(false) }
    var showCredentials by remember { mutableStateOf(false) }
    // 凭据列表态拦截系统返回键：退回 Git 主视图而非退出整个 Git 页。
    BackHandler(enabled = showCredentials) { showCredentials = false }
    // editingCredential != null -> 编辑现有；editingCredential == null && isAddingCredential -> 新增；否则列表态。
    // 编辑/新增态直接在 [Scaffold] 之外独立渲染全屏 [CredentialEditorScreen]（它自带 Scaffold/TopAppBar/BackHandler），
    // 避免与本页 Scaffold 嵌套产生双层顶栏，返回由其自身 BackHandler 接管。
    var editingCredential by remember { mutableStateOf<GitCredential?>(null) }
    var isAddingCredential by remember { mutableStateOf(false) }

    // diff 视图：独立全屏页，不进入下方 GitScreen 的 Scaffold，避免双层顶栏。
    val diffData = state.diffData
    if (diffData != null) {
        DiffViewerScreen(
            diffData = diffData,
            onBack = { viewModel.clearDiff() }
        )
        return
    }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(if (showCredentials) stringResource(R.string.git_credentials_and_identity) else "Git") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = {
                        // 凭据列表态回 Git 页，否则退出 Git 页。编辑/新增态由 [CredentialEditorScreen] 自身 BackHandler 处理，不走此顶栏。
                        if (showCredentials) showCredentials = false else onNavigateBack()
                    }) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (!showCredentials) {
                        IconButton(onClick = { showCredentials = true }) {
                            Icon(FeatherIcons.Key, contentDescription = stringResource(R.string.git_credentials_and_identity))
                        }
                        IconButton(onClick = { viewModel.refresh() }, enabled = !state.busy) {
                            Icon(FeatherIcons.RefreshCw, contentDescription = stringResource(R.string.git_refresh))
                        }
                    } else {
                        // showCredentials 列表态：显示添加凭据。编辑/新增态已 return，渲染顶栏时不会落到此分支。
                        IconButton(onClick = { isAddingCredential = true }) {
                            Icon(FeatherIcons.Plus, contentDescription = stringResource(R.string.credential_add))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
            if (showCredentials) {
                // 每次进入凭据页重新读署名：用户可能在终端改过项目级/全局署名，避免回显陈旧空值。
                LaunchedEffect(Unit) { credentialViewModel.refreshIdentity() }
                CredentialListSection(
                    credentials = credState.credentials,
                    userName = credState.userName,
                    userEmail = credState.userEmail,
                    globalUserName = credState.globalUserName,
                    repoUrl = credState.repoUrl,
                    onEdit = { editingCredential = it },
                    onToggleDefault = { id, isDefault -> credentialViewModel.setDefault(id, isDefault) },
                    onSaveIdentity = { name, email, repoUrl -> credentialViewModel.saveUserIdentity(name, email, repoUrl) }
                )
                return@Column
            }
            when {
                state.diffLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Spacing.sm))
                        Text(stringResource(R.string.git_computing_diff), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.notARepo -> NotARepoState(onInit = viewModel::initRepo)
                else -> AnimatedContent(
                    targetState = state.tab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (slideInHorizontally(animationSpec = tween(240)) { direction * it } +
                            fadeIn(animationSpec = tween(240))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(240)) { -direction * it } +
                                fadeOut(animationSpec = tween(160)))
                    },
                    label = "git-tab-content"
                ) { tab ->
                    when (tab) {
                        GitTab.STATUS -> StatusTab(
                            status = state.status,
                            busy = state.busy,
                            hasRemote = state.hasRemote,
                            hasIdentity = state.hasIdentity,
                            onStage = viewModel::stage,
                            onUnstage = viewModel::unstage,
                            onStageAll = viewModel::stageAll,
                            onUnstageAll = viewModel::unstageAll,
                            onCommit = { showCommitDialog = true },
                            onPull = viewModel::pull,
                            onPush = viewModel::push,
                            onFileDiff = viewModel::loadWorktreeDiff
                        )
                        GitTab.BRANCHES -> BranchesTab(
                            branches = state.branches,
                            tags = state.tags,
                            branchesLoading = state.branchesLoading,
                            branchesLoaded = state.branchesLoaded,
                            checkoutLoading = state.checkoutLoading,
                            onCheckout = viewModel::checkoutBranch,
                            onCreateBranch = viewModel::createBranch,
                            onDeleteBranch = viewModel::deleteBranch,
                            onDeleteRemoteBranch = viewModel::deleteRemoteBranch,
                            onRenameBranch = viewModel::renameBranch,
                            onCreateTag = viewModel::createTag,
                            onDeleteTag = viewModel::deleteTag
                        )
                        GitTab.LOG -> LogTab(
                            graph = state.graph,
                            expandedCommits = state.expandedCommits,
                            commitFiles = state.commitFiles,
                            loadingCommit = state.loadingCommit,
                            graphLoadingMore = state.graphLoadingMore,
                            onToggleCommit = viewModel::toggleCommit,
                            onFileDiff = viewModel::loadCommitFileDiff,
                            onLoadMore = viewModel::loadMoreCommits
                        )
                    }
                }
            }
        }

        // 底部渐变蒙版 + 悬浮 tab 组：内容可滚动到屏幕底部穿过 tab 栏，被渐变遮罩（同主页输入框）。
        if (!showCredentials) {
            val bgColor = settingsPageBackground()
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(bgColor.copy(alpha = 0f), bgColor.copy(alpha = 0.98f))
                        )
                    )
            )
            GitBottomTabBar(
                selected = state.tab,
                onSelect = viewModel::setTab,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
    }

    if (showCommitDialog) {
        CommitDialog(
            onDismiss = { showCommitDialog = false },
            onConfirm = { msg ->
                showCommitDialog = false
                viewModel.commit(msg)
            }
        )
    }

    val editing = editingCredential
    if (editing != null) {
        CredentialEditorSheet(
            initial = editing,
            onDismiss = { editingCredential = null },
            onSave = { credentialViewModel.saveCredential(it); editingCredential = null },
            onDelete = { credentialViewModel.deleteCredential(it); editingCredential = null }
        )
    }

    if (isAddingCredential) {
        CredentialEditorSheet(
            initial = null,
            onDismiss = { isAddingCredential = false },
            onSave = { credentialViewModel.saveCredential(it); isAddingCredential = false }
        )
    }
}

@Composable
internal fun StatusMetric(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = if (settingsLightMode()) Color(0xFFF2F2F7) else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(Radius.md),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm)) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.lg, end = Spacing.lg, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Normal,
            color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 状态码 → 配色（容器色 + 前景色）。
 *
 * 与多数 Git 客户端约定一致：新增=绿、修改=琥珀、删除=红、重命名/复制=蓝、未跟踪=灰、
 * 冲突=紫红、类型变更=青。仅取首字符判定，porcelain 的 X/Y 两列统一映射。
 */
private fun statusColor(code: String): Pair<Color, Color> = when (code.firstOrNull()) {
    'A' -> Color(0xFF16A34A) to Color(0xFFFFFFFF)            // 新增
    'M' -> Color(0xFFD97706) to Color(0xFFFFFFFF)            // 修改
    'D' -> Color(0xFFDC2626) to Color(0xFFFFFFFF)            // 删除
    'R', 'C' -> Color(0xFF2563EB) to Color(0xFFFFFFFF)       // 重命名/复制
    '?' -> Color(0xFF94A3B8) to Color(0xFFFFFFFF)            // 未跟踪
    'U' -> Color(0xFF9333EA) to Color(0xFFFFFFFF)            // 冲突
    'T' -> Color(0xFF0891B2) to Color(0xFFFFFFFF)            // 类型变更
    else -> Color(0xFF64748B) to Color(0xFFFFFFFF)           // 兜底
}

@Composable
internal fun StatusChip(text: String) {
    val (bg, fg) = statusColor(text)
    Surface(
        color = bg,
        shape = RoundedCornerShape(Radius.pill),
        modifier = Modifier.size(width = 32.dp, height = 20.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text.take(2),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = fg
            )
        }
    }
}

@Composable
internal fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 非仓库态：文案 + 「初始化 Git 仓库」按钮（跑 `git init`，成功后自动刷新进仓库态）。 */
@Composable
private fun NotARepoState(onInit: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                stringResource(R.string.git_not_a_repo),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(R.string.git_init_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onInit) {
                Icon(FeatherIcons.GitBranch, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.git_init_repo))
            }
        }
    }
}

@Composable
private fun CommitDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var message by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_tab_commits)) },
        text = {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text(stringResource(R.string.git_commit_message)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (message.isNotBlank()) onConfirm(message.trim()) },
                enabled = message.isNotBlank()
            ) { Text(stringResource(R.string.git_tab_commits)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/**
 * 底部悬浮 tab 栏：一条胶囊玻璃 bar（半透明 + 边框 + 阴影），内部三个竖排 tab
 * 宽度自适应内容（图标 + 文字），覆盖在滚动内容之上，底部渐变蒙版（同主页输入框）遮罩。
 *
 * 选中指示器是独立的蓝色椭圆：点击切换时滑动吸附到目标 tab；长按后进入拖动模式，
 * 椭圆实时跟随手指左右移动（跟手），滑过哪个 tab 即切换页面（与点击一致），松手吸附回位。
 */
@Composable
private fun GitBottomTabBar(
    selected: GitTab,
    onSelect: (GitTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val light = settingsLightMode()
    // 参考主页输入框：半透明 surface + 边框的悬浮卡片。
    val glassBg = if (light) Color.White.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val glassBorder = MaterialTheme.colorScheme.outlineVariant
    val tabs = GitTab.entries
    // 各 tab 相对 bar 内容区的 bounds，供椭圆定位与拖动目标判断。
    val tabBounds = remember { mutableStateMapOf<GitTab, Rect>() }
    val currentSelected by rememberUpdatedState(selected)
    val density = LocalDensity.current
    // 拖动中的手指 x（px），Float.NaN 表示非拖动态。
    var dragX by remember { mutableFloatStateOf(Float.NaN) }
    // 椭圆左边缘 x：非拖动时吸附到选中 tab（滑动动画），拖动时实时跟手（0ms 动画）。
    val indicatorTarget = if (dragX.isNaN()) tabBounds[selected]?.left ?: 0f else dragX
    val indicatorX by animateFloatAsState(
        targetValue = indicatorTarget,
        animationSpec = if (dragX.isNaN()) tween(220) else tween(0),
        label = "indicator-x"
    )

    Box(
        modifier = modifier
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(Radius.pill),
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.10f)
            )
            .clip(RoundedCornerShape(Radius.pill))
            .background(glassBg)
            .border(1.dp, glassBorder, RoundedCornerShape(Radius.pill))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start -> dragX = start.x },
                    onDrag = { change, _ ->
                        change.consume()
                        val x = change.position.x
                        val tabWidth = tabBounds.values.firstOrNull()?.width ?: 0f
                        if (tabWidth > 0f) {
                            val minX = tabBounds.values.minOfOrNull { it.left } ?: 0f
                            val maxX = (tabBounds.values.maxOfOrNull { it.right } ?: tabWidth) - tabWidth
                            dragX = (x - tabWidth / 2f).coerceIn(minX, maxX)
                        }
                        val target = tabs.minByOrNull { abs((tabBounds[it]?.center?.x ?: x) - x) }
                        if (target != null && target != currentSelected) onSelect(target)
                    },
                    onDragEnd = { dragX = Float.NaN },
                    onDragCancel = { dragX = Float.NaN }
                )
            }
    ) {
        val tabWidth = tabBounds.values.firstOrNull()?.width ?: 0f
        // 蓝色高亮椭圆：随 indicatorX 移动，纵向居中。
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(indicatorX.roundToInt(), 0) }
                .width(with(density) { tabWidth.toDp() })
                .height(44.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(MaterialTheme.colorScheme.primaryContainer)
        )
        Row(
            modifier = Modifier,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val fg = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                val interactionSource = remember(tab) { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .onGloballyPositioned { tabBounds[tab] = it.boundsInParent() }
                        .widthIn(min = 88.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelect(tab) }
                        )
                        .padding(horizontal = Spacing.md, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (tab) {
                            GitTab.STATUS -> FeatherIcons.Activity
                            GitTab.BRANCHES -> FeatherIcons.GitBranch
                            GitTab.LOG -> FeatherIcons.GitCommit
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = fg
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when (tab) {
                            GitTab.STATUS -> stringResource(R.string.git_tab_status)
                            GitTab.BRANCHES -> stringResource(R.string.git_tab_branches)
                            GitTab.LOG -> stringResource(R.string.git_tab_commits)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = fg,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
