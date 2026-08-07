package com.aicode.feature.settings.presentation.component

import android.content.ClipData
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.data.remote.ModelTestResult
import com.aicode.feature.settings.domain.model.ModelMetadata
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.ArrowDown
import compose.icons.feathericons.ArrowUp
import compose.icons.feathericons.Check
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.X
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ModelMetadataTags(metadata: ModelMetadata?) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        metadata?.let {
            if (it.supportsVision) {
                ModelTag(text = "Image")
            }
            if (it.supportsTools) {
                ModelTag(text = "Tools")
            }
            val input = it.inputTokens?.takeIf { tokens -> tokens > 0 }
                ?: it.contextTokens.takeIf { tokens -> tokens > 0 }
            if (input != null) {
                ModelTag(icon = FeatherIcons.ArrowUp, text = formatTokenLimit(input))
            }
            it.outputTokens?.takeIf { tokens -> tokens > 0 }?.let { output ->
                ModelTag(icon = FeatherIcons.ArrowDown, text = formatTokenLimit(output))
            }
        }
    }
}

@Composable
private fun ModelTag(text: String? = null, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (icon != null && text != null) Spacer(Modifier.width(4.dp))
            if (text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderModelRow(
    model: String,
    metadata: ModelMetadata?,
    testing: Boolean,
    result: ModelTestResult?,
    onTest: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit = {}
) {
    var showErrorDetail by remember { mutableStateOf(false) }

    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val rowBackground = if (light) Color.White else MaterialTheme.colorScheme.surface

    // 左滑删除（参考 MCP 列表行）
    val density = LocalDensity.current
    val revealPx = remember(density) { with(density) { -112.dp.toPx() } }
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val revealedWidthDp = with(density) { (-offsetX.value).toDp().coerceAtLeast(0.dp) }
    val maxButtonWidth = 104.dp
    val buttonWidth = if (revealedWidthDp > 8.dp) (revealedWidthDp - 8.dp).coerceAtMost(maxButtonWidth) else 0.dp
    val progress = (buttonWidth / maxButtonWidth).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
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
                                onRemove()
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

        // 2. 表层行（含主内容 + 测试结果），支持手势回弹与滑动展开
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
                .clickable {
                    if (offsetX.value < -10f) {
                        coroutineScope.launch {
                            offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                        }
                    } else {
                        onEdit()
                    }
                }
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelLogoIcon(modelName = model, size = 24.dp)
                Spacer(Modifier.width(Spacing.md))

                // Center Content (Name & Tags)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    ModelMetadataTags(metadata)
                }

                Spacer(Modifier.width(Spacing.sm))

                // Right Action: 测试
                Box(
                    modifier = Modifier.width(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onTest, contentPadding = PaddingValues(horizontal = Spacing.sm)) {
                            Text(stringResource(R.string.provider_test), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Test Result
            result?.let { r ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = Spacing.sm, start = 36.dp)
                        .then(
                            if (!r.success) Modifier.clickable { showErrorDetail = true } else Modifier
                        )
                ) {
                    Icon(
                        if (r.success) FeatherIcons.Check else FeatherIcons.AlertCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    val displayMsg = if (r.success) {
                        r.message
                    } else {
                        val codeMatch = Regex("""(?i)(HTTP\s*\d{3}|code[:\s]+[a-zA-Z0-9_]+)""").find(r.message)
                        if (codeMatch != null) codeMatch.value
                        else r.message.lines().firstOrNull()?.let { if (it.length > 20) it.take(20) + "..." else it } ?: "Error"
                    }
                    Text(
                        text = displayMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showErrorDetail && result != null && !result.success) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val clipboard = LocalClipboard.current
        var copied by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(copied) {
            if (copied) {
                kotlinx.coroutines.delay(1500)
                copied = false
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showErrorDetail = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.lg)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Error Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    IconButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("error", result.message)))
                            copied = true
                        }
                    }) {
                        Icon(
                            if (copied) FeatherIcons.Check else FeatherIcons.Copy,
                            contentDescription = "Copy Error",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(Radius.sm))
                        .padding(Spacing.sm)
                )
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

private fun formatTokenLimit(tokens: Int): String =
    when {
        tokens >= 1_000_000 && tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}M"
        tokens >= 1_000_000 -> "${tokens / 1_000_000.0}M".trimDecimal()
        tokens >= 1_000 && tokens % 1_000 == 0 -> "${tokens / 1_000}K"
        tokens >= 1_000 -> "${tokens / 1_000.0}K".trimDecimal()
        else -> tokens.toString()
    }

private fun String.trimDecimal(): String =
    replace(Regex("(\\.\\d)\\d+"), "$1").removeSuffix(".0")

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FetchModelRow(
    model: String,
    metadata: ModelMetadata?,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAdd() }
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModelLogoIcon(modelName = model, size = 20.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(model, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            ModelMetadataTags(metadata)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(FeatherIcons.Plus, contentDescription = stringResource(R.string.common_add), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
