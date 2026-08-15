package com.aicode.feature.git.presentation.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.FloatingTabBar
import com.aicode.core.ui.FloatingTabItem
import com.aicode.feature.settings.presentation.component.settingsLightMode
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.feature.git.domain.model.GitStatus
import com.aicode.feature.git.domain.model.GitTab
import com.aicode.feature.git.presentation.GitViewModel
import compose.icons.FeatherIcons
import compose.icons.feathericons.Activity
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.GitCommit
import compose.icons.feathericons.Key
import compose.icons.feathericons.RefreshCw

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    viewModel: GitViewModel,
    onNavigateToCredentials: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // toast → Snackbar 一次性消费。
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    var showCommitDialog by remember { mutableStateOf(false) }

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
                title = { Text("Git") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCredentials) {
                        Icon(FeatherIcons.Key, contentDescription = stringResource(R.string.git_credentials_and_identity))
                    }
                    IconButton(onClick = { viewModel.refresh() }, enabled = !state.busy) {
                        Icon(FeatherIcons.RefreshCw, contentDescription = stringResource(R.string.git_refresh))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
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
        FloatingTabBar(
            selected = state.tab.ordinal,
            onSelect = { viewModel.setTab(GitTab.entries[it]) },
            items = listOf(
                FloatingTabItem(FeatherIcons.Activity, stringResource(R.string.git_tab_status)),
                FloatingTabItem(FeatherIcons.GitBranch, stringResource(R.string.git_tab_branches)),
                FloatingTabItem(FeatherIcons.GitCommit, stringResource(R.string.git_tab_commits))
            ),
            maskColor = settingsPageBackground(),
            modifier = Modifier.align(Alignment.BottomCenter)
        )
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
