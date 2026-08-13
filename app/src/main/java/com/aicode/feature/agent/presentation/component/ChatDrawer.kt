package com.aicode.feature.agent.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.model.ChatSession
import com.aicode.feature.agent.presentation.AgentUIState
import compose.icons.FeatherIcons
import compose.icons.feathericons.Download
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.Settings
import compose.icons.feathericons.Trash2
import androidx.compose.ui.res.stringResource
import com.aicode.R
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

/**
 * 侧边栏内容：顶部「历史记录」标题，中部按最后回复时间分组的会话列表，底部「设置」入口。
 * 由 AIChatPanel 的 ModalNavigationDrawer 承载，支持左上角按钮点击或右滑打开。
 */
@Composable
fun ChatDrawerContent(
    sessions: List<ChatSession>,
    currentSessionId: String?,
    agentStates: Map<String, AgentUIState>,
    onSelect: (ChatSession) -> Unit,
    onDelete: (ChatSession) -> Unit,
    onRename: (ChatSession, String) -> Unit,
    onExport: (ChatSession) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<ChatSession?>(null) }
    var pendingRename by remember { mutableStateOf<ChatSession?>(null) }
    var menuSession by remember { mutableStateOf<ChatSession?>(null) }
    val listState = rememberLazyListState()

    // 点击会话/重开侧边栏保持原滚动位置；仅当同一会话的最后回复时间变化（发消息/收到回复）时滚回顶部。
    var lastTouched by remember { mutableStateOf<Pair<String?, Long?>?>(null) }
    val currentUpdatedAt = sessions.firstOrNull { it.id == currentSessionId }?.updatedAt
    LaunchedEffect(currentSessionId, currentUpdatedAt) {
        val cur = currentSessionId to currentUpdatedAt
        val prev = lastTouched
        lastTouched = cur
        if (prev != null && prev.first == cur.first && prev.second != cur.second) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.lg)
    ) {
        Text(
            text = stringResource(R.string.chat_history),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (sessions.isEmpty()) {
                Text(
                    stringResource(R.string.chat_no_sessions_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
                )
            } else {
                val entries = remember(sessions) {
                    buildSessionEntries(sessions, System.currentTimeMillis())
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    items(entries, key = { it.key }) { entry ->
                        when (entry) {
                            is SessionListEntry.Header -> SessionGroupHeader(
                                label = sessionGroupLabel(entry.groupKey, entry.anchorSession)
                            )
                            is SessionListEntry.Item -> {
                                val session = entry.session
                                val state = agentStates[session.id]
                                val isExecuting = state is AgentUIState.Loading || state is AgentUIState.Streaming
                                ChatSessionRow(
                                    session = session,
                                    selected = session.id == currentSessionId,
                                    isExecuting = isExecuting,
                                    onClick = { onSelect(session) },
                                    onLongClick = { menuSession = session }
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(vertical = Spacing.sm)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .clickable { onNavigateToSettings() }
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                FeatherIcons.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.chat_settings),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    pendingDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.chat_delete_session)) },
            text = { Text(stringResource(R.string.chat_delete_session_confirm, session.title)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(session)
                    pendingDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    menuSession?.let { session ->
        SessionActionSheet(
            session = session,
            onRename = {
                menuSession = null
                pendingRename = session
            },
            onExport = {
                menuSession = null
                onExport(session)
            },
            onDelete = {
                menuSession = null
                pendingDelete = session
            },
            onDismiss = { menuSession = null }
        )
    }

    pendingRename?.let { session ->
        var renameText by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.chat_rename_session)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.chat_session_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRename(session, renameText)
                        pendingRename = null
                    },
                    enabled = renameText.isNotBlank() && renameText != session.title
                ) { Text(stringResource(R.string.common_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

/**
 * 会话行长按弹出的功能菜单：重命名 / 删除。底部 sheet 样式参照 git 分支的 RefActionSheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionActionSheet(
    session: ChatSession,
    onRename: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
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
                text = session.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )
            SheetActionRow(
                icon = FeatherIcons.Edit2,
                label = stringResource(R.string.common_rename),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    onRename()
                }
            )
            SheetActionRow(
                icon = FeatherIcons.Download,
                label = stringResource(R.string.chat_export_session),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = {
                    onDismiss()
                    onExport()
                }
            )
            SheetActionRow(
                icon = FeatherIcons.Trash2,
                label = stringResource(R.string.common_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = {
                    onDismiss()
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint
            )
            Spacer(Modifier.width(Spacing.lg))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = tint
            )
        }
    }
}

/** 侧边栏会话列表条目：分组标题与会话行。 */
internal sealed interface SessionListEntry {
    val key: String

    data class Header(
        override val key: String,
        val groupKey: String,
        val anchorSession: ChatSession
    ) : SessionListEntry

    data class Item(
        override val key: String,
        val session: ChatSession
    ) : SessionListEntry
}

/**
 * 按最后回复时间（updatedAt）降序的会话列表插入分组标题：今天 / 昨天 / 7天内 / 30天内 / 更早按月。
 */
internal fun buildSessionEntries(sessions: List<ChatSession>, now: Long): List<SessionListEntry> {
    val entries = mutableListOf<SessionListEntry>()
    var lastGroup: String? = null
    for (session in sessions) {
        val groupKey = sessionGroupKey(session.updatedAt, now)
        if (groupKey != lastGroup) {
            entries += SessionListEntry.Header("header-$groupKey", groupKey, session)
            lastGroup = groupKey
        }
        entries += SessionListEntry.Item(session.id, session)
    }
    return entries
}

/** 返回会话所属分组 key；月份分组为 ISO 年月（如 2026-05），其余为固定字面量。 */
internal fun sessionGroupKey(updatedAt: Long, now: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(day, today)
    return when {
        days <= 0L -> "today"
        days == 1L -> "yesterday"
        days <= 7L -> "7d"
        days <= 30L -> "30d"
        else -> YearMonth.from(day).toString()
    }
}

@Composable
private fun SessionGroupHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
    )
}

@Composable
private fun sessionGroupLabel(groupKey: String, anchorSession: ChatSession): String = when (groupKey) {
    "today" -> stringResource(R.string.session_group_today)
    "yesterday" -> stringResource(R.string.session_group_yesterday)
    "7d" -> stringResource(R.string.session_group_last_7_days)
    "30d" -> stringResource(R.string.session_group_last_30_days)
    else -> SimpleDateFormat(
        stringResource(R.string.session_group_month_format),
        Locale.getDefault()
    ).format(Date(anchorSession.updatedAt))
}
