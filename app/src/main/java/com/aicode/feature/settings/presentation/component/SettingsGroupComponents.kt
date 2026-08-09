package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.core.theme.Spacing
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Search

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
    icon: ImageVector?,
    title: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    subtitle: String? = null
) {
    val modifier = Modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable { onClick() } else it }
        .padding(horizontal = Spacing.lg, vertical = 11.dp)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
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
        if (onClick != null) {
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