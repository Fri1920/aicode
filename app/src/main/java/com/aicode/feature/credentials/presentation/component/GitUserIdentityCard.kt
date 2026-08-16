package com.aicode.feature.credentials.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.SettingsGroupHeader
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check

/**
 * git 提交署名(user.name / user.email)与仓库地址配置，设置页分组风格：
 * 分组标题 + 白底圆角分组卡片，输入框样式与容器/镜像编辑弹窗一致。
 *
 * 文本框以 DataStore 持久值初始化；点「保存」时回调 onSave，由 ViewModel 写 DataStore 并同步
 * `git config --global`。容器 HOME=/root 全局共享，写入即对所有工作区生效。
 * [globalHint] 显示容器内 git 实际配置的 user.name，供用户确认同步是否生效。
 */
@Composable
internal fun GitUserIdentityCard(
    initialName: String,
    initialEmail: String,
    initialRepoUrl: String,
    globalHint: String,
    onSave: (name: String, email: String, repoUrl: String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    var repoUrl by remember(initialRepoUrl) { mutableStateOf(initialRepoUrl) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SettingsGroupHeader(stringResource(R.string.git_identity_title))
        SettingsGroup {
            IdentityField(
                value = name,
                onValueChange = { name = it },
                label = "user.name"
            )
            SettingsDivider()
            IdentityField(
                value = email,
                onValueChange = { email = it },
                label = "user.email"
            )
        }
        SettingsGroupHeader(stringResource(R.string.git_repo_url_title))
        SettingsGroup {
            IdentityField(
                value = repoUrl,
                onValueChange = { repoUrl = it },
                label = stringResource(R.string.git_remote_url)
            )
        }
        Button(
            onClick = { onSave(name.trim(), email.trim(), repoUrl.trim()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.common_save),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

/** 与容器/镜像编辑弹窗一致的输入框：圆角 12dp + 定制边框与底色。 */
@Composable
private fun IdentityField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = 4.dp)
    )
}
