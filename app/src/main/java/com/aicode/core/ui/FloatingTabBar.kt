package com.aicode.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import kotlin.math.abs
import kotlin.math.roundToInt

/** 底部悬浮 tab 栏的单个 tab 项：图标 + 文案。 */
data class FloatingTabItem(
    val icon: ImageVector,
    val label: String
)

/**
 * 底部悬浮 tab 栏：一条胶囊玻璃 bar（半透明 + 边框 + 阴影），内部 tab 竖排图标 + 文字，
 * 宽度自适应内容，覆盖在滚动内容之上。底部渐变蒙版（maskColor 渐隐）遮罩穿过 tab 栏的内容。
 *
 * 选中指示器是独立的椭圆：点击切换时滑动吸附到目标 tab；长按后进入拖动模式，
 * 椭圆实时跟随手指左右移动（跟手），滑过哪个 tab 即切换页面（与点击一致），松手吸附回位。
 */
@Composable
fun FloatingTabBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    items: List<FloatingTabItem>,
    maskColor: Color,
    modifier: Modifier = Modifier,
    /** 内容正在滚动时整体淡出到 40%，停止滚动恢复；用于长列表阅读时降低底栏干扰。默认关闭。 */
    isScrolling: Boolean = false
) {
    // 滚动弱化：胶囊本体透明度动画（蒙版渐变不参与，保持遮内容能力）。
    val contentAlpha by animateFloatAsState(
        targetValue = if (isScrolling) 0.4f else 1f,
        animationSpec = tween(200),
        label = "tabbar-content-alpha"
    )
    // 浅色判断与设置页一致：背景 luminance > 0.5 视为浅色模式。
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val glassBg = if (light) Color.White.copy(alpha = 0.85f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    val glassBorder = MaterialTheme.colorScheme.outlineVariant
    // 各 tab 相对 bar 内容区的 bounds，供椭圆定位与拖动目标判断。
    val tabBounds = remember { mutableStateMapOf<Int, Rect>() }
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
            .fillMaxWidth()
            .height(64.dp)
            .background(
                Brush.verticalGradient(
                    listOf(maskColor.copy(alpha = 0f), maskColor.copy(alpha = 0.98f))
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = contentAlpha }
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
                .pointerInput(items) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { start ->
                            // 以手指为中心定位椭圆左缘，与 onDrag 的 (x - 半宽) 口径一致，避免起始瞬间跳动
                            val w = tabBounds.values.firstOrNull()?.width ?: 0f
                            dragX = start.x - w / 2f
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val x = change.position.x
                            val tabWidth = tabBounds.values.firstOrNull()?.width ?: 0f
                            if (tabWidth > 0f) {
                                val minX = tabBounds.values.minOfOrNull { it.left } ?: 0f
                                val maxX = (tabBounds.values.maxOfOrNull { it.right } ?: tabWidth) - tabWidth
                                dragX = (x - tabWidth / 2f).coerceIn(minX, maxX)
                            }
                            val target = items.indices.minByOrNull { abs((tabBounds[it]?.center?.x ?: x) - x) }
                            if (target != null && target != currentSelected) onSelect(target)
                        },
                        onDragEnd = { dragX = Float.NaN },
                        onDragCancel = { dragX = Float.NaN }
                    )
                }
        ) {
            val tabWidth = tabBounds.values.firstOrNull()?.width ?: 0f
            // 高亮椭圆：随 indicatorX 移动，纵向居中。
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
                items.forEachIndexed { index, item ->
                    val isSelected = index == selected
                    val fg = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    val interactionSource = remember(index) { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .onGloballyPositioned { tabBounds[index] = it.boundsInParent() }
                            .widthIn(min = 88.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onSelect(index) }
                            )
                            .padding(horizontal = Spacing.md, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = fg
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.label,
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
}
