package com.aicode.feature.agent.presentation.component

import android.content.ClipData
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.tool.question.UserQuestionAnswer
import com.aicode.feature.agent.presentation.AgentUIMessage
import com.aicode.feature.agent.presentation.AgentUIState
import com.aicode.feature.agent.presentation.AIAgentViewModel
import com.aicode.feature.agent.presentation.hasVisibleContent
import com.aicode.feature.settings.presentation.SettingsViewModel
import com.aicode.feature.workspace.presentation.WorkspaceViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * 流式尾巴的三种状态，用于 [when] 分支分发。
 *
 * 早先用 [androidx.compose.animation.Crossfade] 做淡入，但 Crossfade 按 targetState
 * 缓存 content 子组合——流式期间 targetState 一直不变，文本增长时不会重新调用 content，
 * 导致 [StreamingBubble] 收不到后续文本、停在首句。故改用枚举 + 直接 [when] 分发。
 */
private enum class TailKind { THINKING, STREAMING, COMPACTING, RETRYING, NONE }

/** 悬浮层（横幅/面板/输入框）与最后一条消息的间距。 */
private val FLOATING_LAYER_GAP_DP = 8.dp

/** 内容底部驱动校准的容差（px）：最后内容底部超出安全区超过该值才向下校准，避免亚像素抖动。 */
private const val AUTO_SCROLL_TOLERANCE_PX = 2

/** 流式结束后尾巴保留时长（ms）：等落库消息接管，避免高度骤减导致视口上跳。 */
private const val STREAMING_TAIL_RETAIN_MS = 150L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatPanel(
    viewModel: AIAgentViewModel,
    onNavigateToTerminal: () -> Unit = {},
    onNavigateToGit: () -> Unit = {},
    settingsViewModel: SettingsViewModel? = null,
    workspaceViewModel: WorkspaceViewModel? = null,
    drawerState: DrawerState,
    currentFile: String? = null,
    selectedCode: String? = null,
    modifier: Modifier = Modifier
) {
    val agentState by viewModel.agentState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val messages = messagesState.messages

    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSession = sessions.find { it.id == currentSessionId }
    val sessionTitle = currentSession?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_new_session_btn)
    val sessionInputTokens = currentSession?.totalInputTokens ?: 0
    val sessionOutputTokens = currentSession?.totalOutputTokens ?: 0
    val sessionLastInputTokens = currentSession?.lastInputTokens ?: 0
    val messagesReady = messagesState.loaded && messagesState.sessionId == currentSessionId
    val runningTool by viewModel.runningTool.collectAsStateWithLifecycle()
    val isCompacting by viewModel.isCompacting.collectAsStateWithLifecycle()
    val retryState by viewModel.retryState.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val pendingPermission by viewModel.pendingToolPermission.collectAsStateWithLifecycle()
    val pendingQuestion by viewModel.pendingUserQuestion.collectAsStateWithLifecycle()
    val queuedRequests by viewModel.queuedRequests.collectAsStateWithLifecycle()
    val targetRewindMessageId by viewModel.targetRewindMessageId.collectAsStateWithLifecycle()
    val providers = (settingsViewModel?.providers?.collectAsStateWithLifecycle()?.value ?: emptyList()).filter { it.isEnabled }
    val modelMetadata = settingsViewModel?.modelMetadata?.collectAsStateWithLifecycle()?.value.orEmpty()
    val sessionProviderModel by viewModel.currentSessionProviderModel.collectAsStateWithLifecycle()
    val defaultProviderId = settingsViewModel?.defaultModelProviderId?.collectAsStateWithLifecycle()?.value ?: ""
    val defaultModelName = settingsViewModel?.defaultModel?.collectAsStateWithLifecycle()?.value ?: ""
    // 未绑定会话回退：新会话默认模型（主页空会话中选择后记忆），未设置则为 null（UI 显示默认图标/顶栏模型名留空）。
    val defaultFallbackProvider = providers
        .find { it.id == defaultProviderId }
        ?.takeIf { it.apiKey.isNotBlank() }
        ?.let { if (defaultModelName.isNotBlank()) it.copy(selectedModel = defaultModelName) else it }
    val activeProvider = run {
        val (boundProviderId, boundModel) = sessionProviderModel
        if (!boundProviderId.isNullOrBlank()) {
            // 与 workflow.resolveProviderConfig 保持一致：绑定 provider 须启用且已填 apiKey，否则回退默认模型
            providers.find { it.id == boundProviderId }?.takeIf { it.apiKey.isNotBlank() }?.let {
                if (!boundModel.isNullOrBlank()) it.copy(selectedModel = boundModel) else it
            } ?: defaultFallbackProvider
        } else {
            defaultFallbackProvider
        }
    }
    val currentWorkspace = workspaceViewModel?.current?.collectAsStateWithLifecycle()?.value
    val projectRoot = currentWorkspace?.path ?: ""
    val currentMode by viewModel.currentSessionMode.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    val inputDraft by viewModel.inputDraft.collectAsStateWithLifecycle()
    LaunchedEffect(inputDraft) {
        if (inputText != inputDraft) inputText = inputDraft
    }
    var pendingAttachments by remember { mutableStateOf<List<PendingUploadAttachment>>(emptyList()) }
    var messageForMenu by remember { mutableStateOf<AgentUIMessage?>(null) }
    var editingMessage by remember { mutableStateOf<AgentUIMessage?>(null) }
    val listState = rememberLazyListState()
    // 贴底滚动留白：首帧测量前用兜底值（约输入框 + 间距），实测悬浮层高度后改为动态值，
    // 横幅/面板/输入框任何形态下最后一条消息都停在悬浮层上方不被遮挡。
    val inputBarBottomReserveDp = 156.dp
    var floatingLayerHeightPx by remember { mutableStateOf(0) }
    val inputBarReservePx = with(LocalDensity.current) {
        (if (floatingLayerHeightPx > 0) floatingLayerHeightPx + FLOATING_LAYER_GAP_DP.toPx()
        else inputBarBottomReserveDp.toPx()).toInt()
    }
    val markdownCache = remember { MarkdownRenderCache() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    val isBusy = agentState is AgentUIState.Loading || agentState is AgentUIState.Streaming
    val activeModel = activeProvider?.effectiveModel.orEmpty()
    val activeModelMetadata = modelMetadata[activeModel]
    val canUploadFiles = projectRoot.isNotBlank() && activeModelMetadata?.supportsTools == true
    val canUploadImages = projectRoot.isNotBlank()
    val reasoningEffort by viewModel.currentSessionReasoningEffort.collectAsStateWithLifecycle()

    LaunchedEffect(activeProvider?.type, activeModel) {
        val provider = activeProvider ?: return@LaunchedEffect
        if (activeModel.isNotBlank()) {
            settingsViewModel?.resolveModelMetadata(provider.id, provider.type, listOf(activeModel))
        }
    }

    fun removePendingAttachment(index: Int) {
        pendingAttachments = pendingAttachments.filterIndexed { i, _ -> i != index }
    }

    fun handlePickedAttachments(uris: List<Uri>, images: Boolean) {
        if (uris.isEmpty()) return
        if (projectRoot.isBlank()) {
            Toast.makeText(context, emptyWorkspaceMessage(context), Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasAttachmentSlots(pendingAttachments.size)) {
            Toast.makeText(context, maxAttachmentMessage(context, MAX_PENDING_ATTACHMENTS), Toast.LENGTH_SHORT).show()
            return
        }
        val selected = selectedAttachments(uris, pendingAttachments.size)
        scope.launch {
            var successCount = 0
            val failures = mutableListOf<String>()
            selected.forEach { uri ->
                runCatching {
                    copyUriToWorkspace(context, uri, projectRoot, includeImageData = images)
                }.onSuccess { uploaded ->
                    pendingAttachments = pendingAttachments + uploaded.toPendingAttachment()
                    successCount += 1
                }.onFailure { error ->
                    failures += (error.message ?: uploadFallbackError(context))
                }
            }

            when {
                successCount > 0 && failures.isEmpty() && uris.size <= remainingAttachmentSlots(pendingAttachments.size - successCount) ->
                    Toast.makeText(context, uploadSuccessMessage(context, successCount), Toast.LENGTH_SHORT).show()
                successCount > 0 ->
                    Toast.makeText(context, partialUploadMessage(context, successCount), Toast.LENGTH_LONG).show()
                failures.isNotEmpty() ->
                    Toast.makeText(context, failures.first(), Toast.LENGTH_LONG).show()
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handlePickedAttachments(uris, images = false)
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        handlePickedAttachments(uris, images = true)
    }

    // 拍照：输出到 cache 临时文件（FileProvider 授权 uri），拍完按图片附件处理。
    var cameraPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = cameraPhotoUri
        cameraPhotoUri = null
        if (success && uri != null) {
            handlePickedAttachments(listOf(uri), images = true)
        }
    }
    fun takePhoto() {
        val photoFile = File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
        }.getOrNull()
        if (uri == null) {
            Toast.makeText(context, unreadableFileMessage(context), Toast.LENGTH_SHORT).show()
            return
        }
        cameraPhotoUri = uri
        takePictureLauncher.launch(uri)
    }

    // 流式结束过渡：streamingText 清空后保留最后文本一小段（落库消息通常在此窗口内接管），
    // 避免尾巴 item 高度骤减导致视口被 clamp 上移、露出历史消息（结束瞬间“闪回”看到用户消息）。
    // 保留期内 StreamingBubble 内部节流会把渲染文本追平到最终文本，与落库消息无缝接力。
    var tailStreamingText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(streamingText) {
        val st = streamingText
        if (st != null && st.hasVisibleContent()) {
            tailStreamingText = st
        } else {
            delay(STREAMING_TAIL_RETAIN_MS)
            tailStreamingText = null
        }
    }

    // 自动滚动跟随
    var positionedSession by remember { mutableStateOf<String?>(null) }
    var followBottom by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf {
            if (!listState.canScrollForward) return@derivedStateOf true
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
                ?: return@derivedStateOf true
            val lastIndex = layout.totalItemsCount - 1
            // 到底 = 最后内容底部停在安全区（悬浮层上沿）附近，而非视口底：
            // 判定更严格，避免用户拖走一小段后仍被判「在底部」而恢复跟随、立即被拉回。
            val safeBottom = layout.viewportEndOffset - inputBarReservePx
            lastVisible.index >= lastIndex &&
                (lastVisible.offset + lastVisible.size) <= safeBottom + AUTO_SCROLL_TOLERANCE_PX
        }
    }

    // 用户开始拖拽：停止跟随。松手时若已到底则恢复跟随（旧逻辑）。
    // 额外：流式输出时内容持续增长，用户可能松手后又被「顶」离底部——
    // 用 snapshotFlow { isAtBottom } 持续监测，只要滑到底部就恢复跟随，
    // 满足「流式中滚到底部自动继续跟随」。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> followBottom = false
                is DragInteraction.Stop, is DragInteraction.Cancel -> {
                    // 松手后延迟判定是否恢复跟随：等惯性滚动稳定，
                    // 避免「松手在底部但惯性上滑」被立即拉回。
                    scope.launch {
                        delay(150)
                        followBottom = isAtBottom
                    }
                }
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { isAtBottom }.collect { atBottom ->
            if (atBottom) followBottom = true
        }
    }

    // 贴底定位（发送消息、切换会话）：直接滚到锚点——scrollToItem 的目标 offset 使
    // 最后一项底部恰好停在悬浮层上方（contentPadding 预留 reserve，滚到该位置即列表
    // 可滚的最底部，无需依赖动画与逐步对齐）。
    val snapToBottom: suspend () -> Unit = {
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            // 滚到列表可滚最底部：scrollToItem 的 offset 会被 clamp 到 maxScroll，
            // 最后一项底部恰好停在 contentPadding 底部（= 悬浮层上沿预留），无需手算项高度。
            listState.scrollToItem(lastIndex, Int.MAX_VALUE)
        }
    }

    val sendMessage: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty() || pendingAttachments.isNotEmpty()) {
            val attachments = pendingAttachments
            val modelRequest = appendAttachmentsToRequest(context, text, attachments)
            val modelSupportsVision = activeModelMetadata?.supportsVision == true
            val images = if (modelSupportsVision) attachments.toAgentImages() else emptyList()
            // 统一走队列：AI 忙时入队（等本轮结束后自动发送下一条），空闲时直接发送。
            // 斜杠命令在 ViewModel 内（agent workflow 之前）分流执行，无需在此区分。
            viewModel.enqueueAgentRequest(
                request = text,
                modelRequest = modelRequest,
                currentFile = currentFile,
                selectedCode = selectedCode,
                projectRoot = projectRoot,
                inputImages = images,
                inputAttachments = attachments.toAgentAttachments()
            )
            inputText = ""
            viewModel.clearInputDraft()
            pendingAttachments = emptyList()
            followBottom = true
            scope.launch {
                kotlinx.coroutines.delay(0)
                snapToBottom()
            }
        }
    }

    // 切换会话：定位到最新内容并恢复跟随（之后由校准循环持续跟随）。
    LaunchedEffect(currentSessionId, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        if (positionedSession != currentSessionId) {
            // 等一帧让 LazyColumn 按新会话完成重组，再滚到锚点（maxScroll）。
            withFrameNanos { }
            snapToBottom()
            positionedSession = currentSessionId
            followBottom = true
        }
    }

    // 锚点式常驻校准循环：锚点 = 最后内容底部恰好停在悬浮层（输入框）上沿。
    // scrollToItem(最后一项, Int.MAX_VALUE) 会被 LazyColumn clamp 到可滚的最底部
    // （contentPadding 底部预留 reserve 保证），即最后一项底部停在悬浮层上沿，
    // 数学上任何时刻都成立——消息足够时，最后一条消息永不落入输入框之下，
    // 且不依赖“最后可见项 == 最后一项”的高度假设（高度跳变时也不会算错目标）。
    // 每帧检查最后可见项：最后内容被增长推下（底部超安全区）或有内容被推出视口下方
    // （最后可见项不是最后一项，即跟丢）时，滚回锚点；md 异步解析的高度跳变也会在
    // 下一帧被检测到，不存在信号与渲染错位。
    // 只向下校准：内容变矮（流式结束、折叠）时保持当前位置，避免「往回滚」与拉锯。
    LaunchedEffect(listState, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            if (!followBottom) continue
            // 无向下滚动空间（内容不满屏或已滚到锚点）：最后内容必然在安全区上方，无需校准。
            if (!listState.canScrollForward) continue
            val layout = listState.layoutInfo
            val lastIndex = layout.totalItemsCount - 1
            if (lastIndex < 0) continue
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            // 最后内容被推出视口下方（最后一项不可见）：跟丢——直接滚回锚点。
            // 用户在别处浏览时 followBottom 已为 false，不会走到这里。
            if (lastVisible == null || lastVisible.index < lastIndex) {
                listState.scrollToItem(lastIndex, Int.MAX_VALUE)
                continue
            }
            val safeBottom = layout.viewportEndOffset - inputBarReservePx
            val lastBottom = lastVisible.offset + lastVisible.size
            if (lastBottom > safeBottom + AUTO_SCROLL_TOLERANCE_PX) {
                listState.scrollToItem(lastIndex, Int.MAX_VALUE)
            }
        }
    }

    // 内容变化信号旁路：文本/思考/消息条数变化时立即校准一次，不等下一帧——
    // 与常驻校准循环互为补充，覆盖「无动画帧」的间隙，杜绝跟丢窗口。
    LaunchedEffect(listState, messagesReady) {
        if (!messagesReady) return@LaunchedEffect
        snapshotFlow {
            Triple(streamingText?.length, streamingReasoning?.length, messages.size)
        }.collect { _ ->
            if (!followBottom) return@collect
            if (!listState.canScrollForward) return@collect
            val layout = listState.layoutInfo
            val lastIndex = layout.totalItemsCount - 1
            if (lastIndex < 0) return@collect
            val lastVisible = layout.visibleItemsInfo.lastOrNull()
            if (lastVisible == null || lastVisible.index < lastIndex) {
                listState.scrollToItem(lastIndex, Int.MAX_VALUE)
                return@collect
            }
            val safeBottom = layout.viewportEndOffset - inputBarReservePx
            val lastBottom = lastVisible.offset + lastVisible.size
            if (lastBottom > safeBottom + AUTO_SCROLL_TOLERANCE_PX) {
                listState.scrollToItem(lastIndex, Int.MAX_VALUE)
            }
        }
    }

    val firstVisibleItemIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    LaunchedEffect(firstVisibleItemIndex, messagesReady, messagesState.hasMore, messagesState.isLoadingMore) {
        if (messagesReady && firstVisibleItemIndex <= 3 && messagesState.hasMore && !messagesState.isLoadingMore) {
            viewModel.loadMoreMessages()
        }
    }

    val executionMode = settingsViewModel?.executionMode?.collectAsStateWithLifecycle()?.value
    val connectionState = settingsViewModel?.connectionState?.collectAsStateWithLifecycle()?.value
    val isRemote = executionMode == com.aicode.feature.settings.data.repository.ExecutionMode.REMOTE_SSH

    val markdownImageTransformer = remember(viewModel.fileAccess) {
        MarkdownImageTransformer(viewModel.fileAccess)
    }

    CompositionLocalProvider(
        LocalMarkdownImageTransformer provides markdownImageTransformer
    ) {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatHeader(
                sessionTitle = sessionTitle,
                modelName = activeProvider?.effectiveModel,
                inputTokens = sessionInputTokens,
                outputTokens = sessionOutputTokens,
                onOpenDrawer = {
                    keyboardController?.hide()
                    scope.launch { drawerState.open() }
                },
                onNewChat = { viewModel.newSession() },
                onNavigateToTerminal = onNavigateToTerminal,
                onNavigateToGit = onNavigateToGit,
                currentMode = currentMode,
                onToggleMode = { viewModel.setSessionMode(it) },
                connectionState = connectionState?.takeIf { isRemote }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 内容层：消息列表延伸到屏幕底部，输入框悬浮其上，滚动时卡片可滑入输入框后面
            Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (!messagesReady) {
                    // 远程模式连接未就绪时显示连接状态占位，避免空白或旧工作区记录闪烁
                    if (isRemote && connectionState != null && connectionState != com.aicode.feature.agent.domain.container.ConnectionState.CONNECTED) {
                        RemoteConnectingPlaceholder(state = connectionState)
                    }
                } else if (messages.isEmpty()) {
                    WelcomeState(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.md,
                            bottom = with(LocalDensity.current) { inputBarReservePx.toDp() }
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        itemsIndexed(messages, key = { _, it -> it.id }, contentType = { _, it -> it.role.name }) { index, message ->
                            val live = runningTool.firstOrNull { it.messageId == message.id }?.text
                            AgentMessageItem(
                                message = message,
                                liveOutput = live,
                                markdownCache = markdownCache,
                                onRewindClick = { viewModel.openRewindMenu(it) },
                                onMoreClick = { messageForMenu = it },
                                onToolToggle = {
                                    // 用户主动展开/收起工具卡片：先暂停自动跟随，避免校准循环把视口拉走造成跳动；
                                    // 用户滚回底部（isAtBottom 监测）时自动恢复跟随。
                                    followBottom = false
                                    // 折叠后卡片可能整体缩出视口上方（长卡片双击折叠）：等一帧按折叠后的布局判断，
                                    // 仅当卡片完全不可见时才滚回顶部让标题可见；仍可见（含贴底）时不做任何主动滚动，
                                    // 避免用折叠前的旧 offset 定位导致「收起时跳动、位置不对」。
                                    scope.launch {
                                        withFrameNanos { }
                                        val layout = listState.layoutInfo
                                        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
                                        if (item == null || item.offset + item.size <= 0) {
                                            listState.animateScrollToItem(index)
                                        }
                                    }
                                }
                            )
                        }
                        val reasoning = streamingReasoning
                        val showReasoning = reasoning != null && reasoning.isNotEmpty()
                        val streaming = tailStreamingText
                        val showStreaming = streaming != null && streaming.hasVisibleContent()
                        val showThinking = !showReasoning && !showStreaming && !isCompacting && isBusy && runningTool.isEmpty() && pendingPermission == null && pendingQuestion == null
                        val showRetrying = retryState != null && isBusy && !isCompacting && !showStreaming && !showReasoning
                        val tailKind = when {
                            showStreaming -> TailKind.STREAMING
                            isCompacting -> TailKind.COMPACTING
                            showRetrying -> TailKind.RETRYING
                            showThinking -> TailKind.THINKING
                            else -> TailKind.NONE
                        }
                        // 尾巴 item：思考气泡与状态尾巴合并进同一个永久挂载的 item，二者都不按状态增删。
                        // 思考开始/结束或流式开始/结束若让 totalItemsCount 突增突减，LazyColumn 会把
                        // firstVisibleItemIndex 向下 clamp → 视口上跳（旧症状2根因）。item 数量恒为 1，
                        // anchor 不会被 clamp：showReasoning 时渲染思考气泡（内部自带折叠），否则为空；
                        // tailKind 为 NONE 时尾巴为空 Box（0 高度）。流结束落库后跟随 effect 会把新消息贴底。
                        item(key = "__active__", contentType = "tail") {
                            Column {
                                if (showReasoning) {
                                    // 流式实时：短文本默认展开边想边看，过长（超 REASONING_COLLAPSE_LINE_LIMIT）时由气泡内部自动折叠，不刷屏
                                    ReasoningBubble(text = reasoning.orEmpty(), initiallyExpanded = true, cache = markdownCache, showTimer = true)
                                }
                                when (tailKind) {
                                    TailKind.THINKING -> ThinkingBubble()
                                    TailKind.STREAMING -> StreamingBubble(text = streaming ?: "", cache = markdownCache)
                                    TailKind.COMPACTING -> CompactionProgressBubble()
                                    TailKind.RETRYING -> {
                                        val rs = retryState
                                        if (rs != null) RetryingBubble(rs.attempt, rs.maxRetries, rs.error) else Box(Modifier)
                                    }
                                    TailKind.NONE -> Box(Modifier)
                                }
                            }
                        }
                    }
                }
            }
            } // 内容层结束

            // 悬浮层：错误气泡 / 面板 / 输入框（蒙版在 ChatInputBar 内部，跟随键盘上移）
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // 实测悬浮层实际高度作为滚动留白，横幅/面板/输入框任何形态都不遮挡最后一条
                    .onGloballyPositioned { if (it.size.height > 0) floatingLayerHeightPx = it.size.height }
            ) {
            StatusBanner(state = agentState)

            AnimatedVisibility(
                visible = pendingPermission != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                pendingPermission?.let { request ->
                    ToolPermissionPanel(
                        request = request,
                        onChoice = { choice -> viewModel.resolveToolPermission(request.id, choice) }
                    )
                }
            }

            AnimatedVisibility(
                visible = pendingQuestion != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                pendingQuestion?.let { question ->
                    AskUserQuestionPanel(
                        question = question,
                        onConfirm = { answer -> viewModel.resolveUserQuestion(question.id, answer) },
                        onSkip = { viewModel.resolveUserQuestion(question.id, UserQuestionAnswer(emptyList())) }
                    )
                }
            }

            val planApproval by viewModel.pendingPlanApproval.collectAsStateWithLifecycle()
            AnimatedVisibility(
                visible = planApproval != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                planApproval?.let { state ->
                    PlanApprovalPanel(
                        state = state,
                        onApprove = { viewModel.approvePlanAndBuild() },
                        onRefine = { viewModel.refinePlan() }
                    )
                }
            }

            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it; viewModel.updateInputDraft(it) },
                onSend = sendMessage,
                onStop = { viewModel.stopAgent() },
                isBusy = isBusy,
                workspaceViewModel = workspaceViewModel,
                hasRunningSessions = { viewModel.hasRunningSessionsInCurrentWorkspace() },
                onSwitchWorkspaceConfirmed = { viewModel.stopAllAndCloseTerminal() },
                activeProvider = activeProvider,
                providers = providers,
                modelMetadata = modelMetadata,
                onSelectModel = { p, m ->
                    viewModel.setSessionProviderModel(p, m)
                },
                currentMode = currentMode,
                onToggleMode = { viewModel.setSessionMode(it) },
                reasoningEffort = reasoningEffort,
                onReasoningEffortChange = { viewModel.setSessionReasoningEffort(it) },
                pendingAttachments = pendingAttachments,
                onRemoveAttachment = ::removePendingAttachment,
                canUploadFiles = canUploadFiles,
                canUploadImages = canUploadImages,
                onUploadFile = { filePicker.launch(arrayOf("*/*")) },
                onUploadImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onTakePhoto = ::takePhoto,
                slashCommands = viewModel.slashCommands,
                queuedRequests = queuedRequests,
                onRemoveQueued = { viewModel.removeQueuedRequest(it) },
                tokenProgress = run {
                    val contextLimit = activeModelMetadata?.contextTokens ?: 0
                    if (contextLimit > 0) {
                        sessionLastInputTokens.toFloat() / contextLimit
                    } else 0f
                },
                modifier = Modifier.fillMaxWidth()
            )
            } // 悬浮层结束

            targetRewindMessageId?.let { targetId ->
                val targetMsg = messages.find { it.id == targetId }
                RewindOptionsBottomSheet(
                    promptSnippet = targetMsg?.content ?: "",
                    onOptionSelected = { option ->
                        viewModel.executeRewindOption(targetId, option) { text ->
                            inputText = text
                        }
                    },
                    onDismissRequest = { viewModel.dismissRewindMenu() }
                )
            }

            messageForMenu?.let { message ->
                val clipboard = LocalClipboard.current
                val copyScope = rememberCoroutineScope()
                MessageActionsBottomSheet(
                    message = message,
                    onDismiss = { messageForMenu = null },
                    onEditClick = { editingMessage = message },
                    onCopyClick = {
                        copyScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("message", message.content)))
                        }
                    },
                    onDeleteClick = { viewModel.deleteMessage(message.id) }
                )
            }

            editingMessage?.let { message ->
                EditMessageDialog(
                    initialText = message.content,
                    onDismiss = { editingMessage = null },
                    onConfirm = { newContent ->
                        viewModel.updateMessageContent(message.id, newContent)
                        editingMessage = null
                    }
                )
            }
        }
    }
    }
}

