package com.aicode.feature.agent.presentation.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ProviderBalanceItem
import com.aicode.feature.settings.domain.model.ProviderBalanceState
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronUp

/** 预设指标颜色板（绿、蓝、紫、橙、粉、青） */
private val DEFAULT_PALETTE = listOf(
    Color(0xFF10B981), // 绿色
    Color(0xFF3B82F6), // 蓝色
    Color(0xFF8B5CF6), // 紫色
    Color(0xFFF59E0B), // 橙色
    Color(0xFFEC4899), // 粉色
    Color(0xFF06B6D4)  // 青色
)

private fun resolveItemColor(item: ProviderBalanceItem, index: Int): Color {
    if (!item.colorHex.isNullOrBlank()) {
        val parsed = runCatching { Color(android.graphics.Color.parseColor(item.colorHex)) }.getOrNull()
        if (parsed != null) return parsed
    }
    return DEFAULT_PALETTE[index % DEFAULT_PALETTE.size]
}

/**
 * 位于聊天输入框上方的套餐余量/余额栏。
 * 兼容订阅制进度条模板与余额制消费模板，支持 1~3 个指标自适应排布。
 */
@Composable
fun ProviderBalanceBar(
    provider: AIProviderConfig,
    state: ProviderBalanceState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val trackBgColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val cardBgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xs)
            .clip(RoundedCornerShape(Radius.lg))
            .border(1.dp, borderColor, RoundedCornerShape(Radius.lg))
            .animateContentSize(animationSpec = tween(220)),
        shape = RoundedCornerShape(Radius.lg),
        color = cardBgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = 10.dp)
        ) {
            when (state) {
                is ProviderBalanceState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Text(
                                text = "正在获取余量...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ProviderBalanceState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.xs))
                                .clickable { onRefresh() }
                                .padding(horizontal = Spacing.xs, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = FeatherIcons.AlertCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "获取失败，点击重试",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                is ProviderBalanceState.Success -> {
                    val rawItems = state.result.items
                    // 最多自适应展示前 3 个指标卡片
                    val items = rawItems.take(3)

                    if (items.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "暂无指标数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        if (!isExpanded) {
                            // ── 收起状态（图 1 / 余额收起态） ──
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isExpanded = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 提供商名称（纯文本，不可点击）
                                Text(
                                    text = provider.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(end = Spacing.md)
                                )

                                // 中间自适应 1~3 个紧凑指标
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items.forEachIndexed { index, item ->
                                        val color = resolveItemColor(item, index)

                                        if (item.hasProgress) {
                                            // 进度条型（如 5h 80%）
                                            Column(
                                                modifier = Modifier.weight(1f, fill = false),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = item.label,
                                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = item.displayValue,
                                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = color
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(CircleShape)
                                                        .background(trackBgColor)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(fraction = ((item.percent ?: 0f) / 100f).coerceIn(0f, 1f))
                                                            .fillMaxHeight()
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                }
                                            }
                                        } else {
                                            // 数值/状态型（如 余额 $12.45、🟢 余额充足）
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (item.statusDot) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                }
                                                val compactLabel = if (item.compactText.isNotBlank()) item.compactText
                                                else if (item.label.isNotBlank() && item.displayValue.isNotBlank()) "${item.label} ${item.displayValue}"
                                                else item.label.ifBlank { item.displayValue }

                                                Text(
                                                    text = compactLabel,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                                    fontWeight = if (item.value.isNotBlank()) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (item.value.isNotBlank() && !item.statusDot) color else MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.width(Spacing.sm))

                                // 右侧展开按钮（收起状态下显示向下箭头）
                                Icon(
                                    imageVector = FeatherIcons.ChevronDown,
                                    contentDescription = "展开详情",
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .clickable { isExpanded = true },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            // ── 展开状态（图 2 / 余额展开态） ──
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                // 顶部：提供商名称 + 收起按钮
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isExpanded = false },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = provider.name,
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    // 右侧收起按钮（展开状态下显示向上箭头）
                                    Icon(
                                        imageVector = FeatherIcons.ChevronUp,
                                        contentDescription = "折叠",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .clickable { isExpanded = false },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // 详细卡片列表：自适应 1~3 列均匀排布，中间带竖线分隔
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    items.forEachIndexed { index, item ->
                                        if (index > 0) {
                                            // 垂直分割线
                                            Box(
                                                modifier = Modifier
                                                    .padding(horizontal = Spacing.sm)
                                                    .width(1.dp)
                                                    .height(48.dp)
                                                    .background(borderColor)
                                            )
                                        }

                                        val color = resolveItemColor(item, index)
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // 顶部小标签/标题
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = item.label,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (item.suffix.isNotBlank()) {
                                                    Text(
                                                        text = item.suffix,
                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            if (item.hasProgress) {
                                                // 进度条主内容：百分比数字 + 6dp 圆角进度条
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = item.displayValue,
                                                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                                                        fontWeight = FontWeight.Bold,
                                                        color = color
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(trackBgColor)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(fraction = ((item.percent ?: 0f) / 100f).coerceIn(0f, 1f))
                                                            .fillMaxHeight()
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(color)
                                                    )
                                                }
                                            } else {
                                                // 数值主内容：大字金额/数值（如 $12.45, $7.55）
                                                Text(
                                                    text = item.displayValue,
                                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // 底部副信息文本（如 "≈ ¥89.32 CNY" / "今日消费 $0.83" / "4.0 / 5.0 小时"）
                                            val subText = item.displaySubText
                                            if (subText.isNotBlank()) {
                                                Text(
                                                    text = subText,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                ProviderBalanceState.Idle -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRefresh() }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "点击查询余量",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
