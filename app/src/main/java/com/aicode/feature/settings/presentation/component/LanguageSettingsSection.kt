package com.aicode.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.util.LanguageRegistry
import com.aicode.R

/**
 * 语言设置二级页：单选列表，选中后即时生效（通过 AppCompatDelegate 切换资源 locale）。
 *
 * @param currentTag 当前语言 tag，null 表示跟随系统。
 * @param onSelect 选中回调，参数为 tag（[LanguageRegistry.FOLLOW_SYSTEM] 表示跟随系统）。
 */
@Composable
internal fun LanguageSettingsSection(
    currentTag: String?,
    onSelect: (String?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // 跟随系统
        LanguageOptionRow(
            displayName = stringResource(R.string.language_follow_system),
            selected = currentTag.isNullOrBlank(),
            onClick = { onSelect(null) }
        )
        // 已支持的语言
        LanguageRegistry.languages.forEach { lang ->
            LanguageOptionRow(
                displayName = lang.displayName,
                selected = currentTag == lang.tag,
                onClick = { onSelect(lang.tag) }
            )
        }
    }
}

@Composable
private fun LanguageOptionRow(
    displayName: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
