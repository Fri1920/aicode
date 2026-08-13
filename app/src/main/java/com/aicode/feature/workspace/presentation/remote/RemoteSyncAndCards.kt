package com.aicode.feature.workspace.presentation.remote

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.settingsLightMode
import com.aicode.feature.workspace.domain.model.RemoteConnection
import com.aicode.feature.workspace.domain.model.RemoteMount
import com.aicode.feature.workspace.domain.model.RemoteProtocol
import com.aicode.R
import compose.icons.FeatherIcons
import compose.icons.feathericons.Cloud
import compose.icons.feathericons.Edit3
import compose.icons.feathericons.Folder
import compose.icons.feathericons.HardDrive
import compose.icons.feathericons.Trash2
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SyncSettingsSection(
    useGitIgnore: Boolean,
    maxSyncBatchSize: Int,
    onUseGitIgnoreChange: (Boolean) -> Unit,
    onMaxSyncBatchSizeChange: (Int) -> Unit
) {
    var maxBatchSizeText by remember(maxSyncBatchSize) { mutableStateOf(maxSyncBatchSize.toString()) }
    var editUseGitIgnore by remember(useGitIgnore) { mutableStateOf(useGitIgnore) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
    ) {
        SettingsGroup {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sync_follow_gitignore),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (settingsLightMode()) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.sync_gitignore_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(
                    checked = editUseGitIgnore,
                    onCheckedChange = { editUseGitIgnore = it }
                )
            }
            SettingsDivider()
            Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.sync_max_batch_size),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (settingsLightMode()) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.sync_batch_size_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = maxBatchSizeText,
                    onValueChange = { maxBatchSizeText = it.filter { char -> char.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.sync_max_batch_count)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        Button(
            onClick = {
                onUseGitIgnoreChange(editUseGitIgnore)
                onMaxSyncBatchSizeChange(maxBatchSizeText.toIntOrNull() ?: 50)
                android.widget.Toast.makeText(context, context.getString(R.string.sync_saved), android.widget.Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.sync_save_settings))
        }
    }
}

/**
 * 分组行通用左滑删除包装：底层红色删除按钮固定在右端，表层行内容随 [Animatable] 偏移滑动，
 * 手势回弹与点击协调方式与容器镜像列表一致。
 */
@Composable
private fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val light = settingsLightMode()
    val rowBackground = if (light) Color.White else MaterialTheme.colorScheme.surface

    val density = LocalDensity.current
    val revealPx = remember(density) { with(density) { -112.dp.toPx() } }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val revealedWidthDp = with(density) { (-offsetX.value).toDp().coerceAtLeast(0.dp) }
    val maxButtonWidth = 104.dp
    val buttonWidth = if (revealedWidthDp > 8.dp) (revealedWidthDp - 8.dp).coerceAtMost(maxButtonWidth) else 0.dp
    val progress = (buttonWidth / maxButtonWidth).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxWidth()) {
        // 1. 底层删除按钮（固定在右端，随滑动露出，带缩放与透明度渐变）
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (buttonWidth > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(buttonWidth)
                        .graphicsLayer {
                            alpha = (progress * 1.2f).coerceIn(0f, 1f)
                            scaleX = (0.4f + 0.6f * progress).coerceIn(0f, 1f)
                            scaleY = (0.7f + 0.3f * progress).coerceIn(0f, 1f)
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444))
                        .border(1.dp, Color(0xFFF87171), RoundedCornerShape(10.dp))
                        .clickable {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                                onDelete()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.requiredWidth(104.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Trash2,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.common_delete),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 2. 表层分组行（支持手势回弹与滑动展开）
        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(rowBackground)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            coroutineScope.launch { offsetX.stop() }
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value < revealPx / 2) {
                                    offsetX.animateTo(
                                        targetValue = revealPx,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                } else {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f)
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(revealPx * 1.15f, 0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
                .then(
                    if (onClick != null) {
                        Modifier.clickable {
                            if (offsetX.value < -10f) {
                                coroutineScope.launch {
                                    offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                }
                            } else {
                                onClick()
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = Spacing.lg, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

/** 行内通用编辑按钮：与容器镜像列表一致的小尺寸线条图标。 */
@Composable
private fun EditRowButton(onEdit: () -> Unit) {
    IconButton(onClick = onEdit) {
        Icon(
            imageVector = FeatherIcons.Edit3,
            contentDescription = stringResource(R.string.common_edit),
            tint = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 行内通用图标方块：40dp 圆角浅底容器 + 居中 22dp 线条图标。 */
@Composable
private fun RowIconBox(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(22.dp)
                .align(Alignment.Center)
        )
    }
}

/** 连接通道行：图标方块 + 名称 + 协议地址副标题 + 右侧编辑，整行点击编辑，左滑删除。 */
@Composable
fun RemoteConnectionCard(
    conn: RemoteConnection,
    onEdit: (RemoteConnection) -> Unit,
    onDelete: (RemoteConnection) -> Unit
) {
    val isLocal = conn.protocol == RemoteProtocol.LOCAL
    SwipeToDeleteRow(
        onDelete = { onDelete(conn) },
        onClick = { onEdit(conn) }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIconBox(if (isLocal) FeatherIcons.HardDrive else FeatherIcons.Cloud)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conn.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = if (settingsLightMode()) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = if (isLocal) "LOCAL://${conn.host}" else "${conn.protocol}://${conn.username}@${conn.host}:${conn.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            EditRowButton(onEdit = { onEdit(conn) })
        }
    }
}

/** 挂载行：图标方块 + 通道名 + 状态徽章 + 路径副标题 + 右侧编辑，底部连接/同步操作区，左滑删除。 */
@Composable
fun RemoteMountCard(
    mount: RemoteMount,
    isFailed: Boolean = false,
    onEdit: (RemoteMount) -> Unit,
    onDelete: (RemoteMount) -> Unit,
    onUpload: (RemoteMount) -> Unit,
    onDownload: (RemoteMount) -> Unit,
    onConnect: (RemoteMount) -> Unit,
    onDisconnect: (RemoteMount) -> Unit
) {
    val isLocal = mount.connection?.protocol == RemoteProtocol.LOCAL
    SwipeToDeleteRow(onDelete = { onDelete(mount) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIconBox(FeatherIcons.Folder)
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mount.connection?.name ?: stringResource(R.string.sync_unknown_connection),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = if (settingsLightMode()) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val (statusText, statusColor) = when {
                        mount.isActive -> stringResource(R.string.status_connected) to Color(0xFF22C55E)
                        isFailed -> stringResource(R.string.status_connection_failed) to MaterialTheme.colorScheme.error
                        else -> stringResource(R.string.status_disconnected) to Color(0xFFF59E0B)
                    }
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Surface(
                        shape = RoundedCornerShape(Radius.pill),
                        color = statusColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.sync_path_mapping, mount.localMountPath, mount.remotePath),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            EditRowButton(onEdit = { onEdit(mount) })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (mount.isActive) {
                TextButton(onClick = { onDisconnect(mount) }) {
                    Text(stringResource(R.string.sync_disconnect))
                }
                TextButton(onClick = { onUpload(mount) }) {
                    Text(if (isLocal) stringResource(R.string.sync_all) else stringResource(R.string.sync_upload_all))
                }
                if (!isLocal) {
                    TextButton(onClick = { onDownload(mount) }) {
                        Text(stringResource(R.string.sync_download_all))
                    }
                }
            } else {
                Button(onClick = { onConnect(mount) }) {
                    Text(stringResource(R.string.sync_connect_and_sync))
                }
            }
        }
    }
}
