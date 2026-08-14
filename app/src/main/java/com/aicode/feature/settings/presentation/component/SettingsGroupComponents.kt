package com.aicode.feature.settings.presentation.component

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.R
import com.aicode.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Search
import compose.icons.feathericons.Trash2
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * 设置页分组组件：浅灰背景 + 白色分组圆角卡片 + 组内缩进分隔线 + 黑色线条图标。
 *
 * 用法：
 * ```
 * SettingsGroupHeader("分组标题")
 * SettingsGroup {
 *     SettingsRow(icon, title, onClick = { ... })
 *     SettingsDivider()
 *     SettingsRow(icon, title, trailing = { Switch(...) })
 * }
 * ```
 */

/** 当前是否浅色模式（据此切换浅灰/主题深色配色）。 */
@Composable
internal fun settingsLightMode(): Boolean =
    MaterialTheme.colorScheme.background.luminance() > 0.5f

/** 可折叠分组标题：点击展开/收起，右侧 chevron 指示状态。与工具授权页的分组一致。 */
@Composable
internal fun CollapsibleGroupHeader(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = FeatherIcons.ChevronRight,
            contentDescription = null,
            tint = if (settingsLightMode()) Color(0xFFC7C7CC) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .rotate(if (expanded) 90f else 0f)
        )
    }
}

/** 设置页背景：浅色模式用浅灰 #F8F8F8，深色模式沿用主题背景。 */
@Composable
internal fun settingsPageBackground(): Color =
    if (settingsLightMode()) Color(0xFFF8F8F8) else MaterialTheme.colorScheme.background

/** 分组小标题：卡片上方灰色小字，左对齐。 */
@Composable
internal fun SettingsGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        fontWeight = FontWeight.Normal,
        color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.md, top = Spacing.lg, bottom = Spacing.sm)
    )
}

/** 白色分组圆角卡片容器：内部按行排布，行间用 [SettingsDivider] 分隔。 */
@Composable
internal fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (settingsLightMode()) Color.White else MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column(content = content)
    }
}

/**
 * 分组内单行：左侧线条图标 + 标题，右侧可选尾随组件（右箭头/开关/值）。
 *
 * @param icon 左侧图标，null 则标题与无图标行对齐。
 * @param trailing 右侧尾随内容（如 [androidx.compose.material3.Switch]、chevron）。
 * @param onClick null 表示无点击行为（如开关行）；非 null 时行尾自动显示右箭头。
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector? = null,
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    val modifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null && enabled) it.clickable { onClick() } else it }
        .padding(horizontal = Spacing.lg, vertical = 11.dp)
    Row(
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (settingsLightMode()) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(Spacing.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = if (settingsLightMode()) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (settingsLightMode()) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke(this)
        if (onClick != null && enabled) {
            Spacer(Modifier.width(Spacing.xs))
            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = if (settingsLightMode()) Color(0xFFC7C7CC) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 分组内行间分隔线：左右两端均缩进对齐行内容，末行不显示。 */
@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        thickness = 0.5.dp,
        color = if (settingsLightMode()) Color(0xFFE5E5EA) else MaterialTheme.colorScheme.outlineVariant
    )
}

/** iOS 风格搜索框：浅灰胶囊背景、无边框，与设置页分组风格一致。 */
@Composable
internal fun ModelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val light = settingsLightMode()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = if (light) Color(0xFFE9E9EB) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                FeatherIcons.Search,
                contentDescription = null,
                tint = if (light) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (light) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = if (light) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 左滑删除行容器：底层红色删除按钮随左滑露出（带缩放/透明度渐变），
 * 表层内容整体左滑，滑开时点击表层收回。与 MCP 页的滑动删除交互一致。
 *
 * @param onDelete 点按删除按钮后触发（先收回再回调）。
 * @param onClick 表层未滑开时的点击回调（如打开详情）。
 */
@Composable
internal fun SwipeToDeleteRow(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable RowScope.() -> Unit
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
        // 底层删除按钮（固定在右端，随滑动露出）
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

        // 表层分组行（支持手势回弹与滑动展开）
        Row(
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
                .clickable {
                    if (offsetX.value < -10f) {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    } else {
                        onClick()
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}