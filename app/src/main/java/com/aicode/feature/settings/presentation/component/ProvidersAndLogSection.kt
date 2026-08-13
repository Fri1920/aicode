package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.domain.model.AIProviderConfig
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import androidx.compose.ui.res.stringResource
import com.aicode.R

/** 提供商二级页：列表 + 空态提示。新增/编辑由顶栏「+」与点击触发 [ProviderEditorScreen]。 */
@Composable
internal fun ProvidersSection(
    providers: List<AIProviderConfig>,
    onEdit: (AIProviderConfig) -> Unit
) {
    if (providers.isEmpty()) {
        EmptyHint(stringResource(R.string.providers_empty))
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl)
    ) {
        SettingsGroup {
            providers.forEachIndexed { index, provider ->
                if (index > 0) {
                    SettingsDivider()
                }
                ProviderItem(
                    provider = provider,
                    onEdit = { onEdit(provider) }
                )
            }
        }
    }
}

/** 居中空态提示。 */
@Composable
internal fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 提供商行：品牌 logo + 名称 + 状态徽章 + 右箭头，整行点击进入编辑。 */
@Composable
fun ProviderItem(
    provider: AIProviderConfig,
    onEdit: () -> Unit
) {
    val statusColor = if (provider.isEnabled) Color(0xFF22C55E) else Color(0xFFF59E0B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(horizontal = Spacing.lg, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProviderLogoIcon(
            provider = provider,
            size = 24.dp,
            modifier = Modifier.padding(end = Spacing.md)
        )
        Text(
            text = provider.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                Color(0xFF0F0F0F)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = RoundedCornerShape(Radius.pill),
            color = statusColor.copy(alpha = 0.15f)
        ) {
            Text(
                text = stringResource(if (provider.isEnabled) R.string.common_enabled else R.string.common_disabled),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = statusColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            imageVector = FeatherIcons.ChevronRight,
            contentDescription = null,
            tint = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
                Color(0xFFC7C7CC)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(18.dp)
        )
    }
}
