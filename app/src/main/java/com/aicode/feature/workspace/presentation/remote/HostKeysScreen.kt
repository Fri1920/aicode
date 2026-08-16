package com.aicode.feature.workspace.presentation.remote

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.core.theme.Spacing
import com.aicode.feature.settings.presentation.component.SettingsDivider
import com.aicode.feature.settings.presentation.component.SettingsGroup
import com.aicode.feature.settings.presentation.component.settingsLightMode
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import com.aicode.R
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ChevronRight

/** SSH 主机密钥独立页面：列表展示已保存指纹，点击查看详情，左滑删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostKeysScreen(
    hostKeys: Map<String, String>,
    onRemove: (String, Int) -> Unit,
    onNavigateBack: () -> Unit
) {
    var detailAddress by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = { Text(stringResource(R.string.ssh_host_key_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (hostKeys.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.ssh_host_key_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.lg)
                        .padding(bottom = Spacing.lg)
                ) {
                    SettingsGroup {
                        hostKeys.toSortedMap().entries.toList().forEachIndexed { index, (address, fingerprint) ->
                            if (index > 0) {
                                SettingsDivider()
                            }
                            val separator = address.lastIndexOf(':')
                            val host = address.substring(0, separator.coerceAtLeast(0))
                            val port = address.substring(separator + 1).toIntOrNull()
                            SwipeToDeleteRow(
                                onDelete = { if (port != null) onRemove(host, port) },
                                onClick = { detailAddress = address }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Spacing.lg, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = address,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Normal,
                                            color = if (settingsLightMode()) androidx.compose.ui.graphics.Color(0xFF0F0F0F)
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = fingerprint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (settingsLightMode()) androidx.compose.ui.graphics.Color(0xFF8E8E93)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(Spacing.xs))
                                    Icon(
                                        imageVector = FeatherIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = if (settingsLightMode()) androidx.compose.ui.graphics.Color(0xFFC7C7CC)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailAddress?.let { address ->
        val separator = address.lastIndexOf(':')
        val host = address.substring(0, separator.coerceAtLeast(0))
        val port = address.substring(separator + 1).toIntOrNull()
        if (port != null) {
            AlertDialog(
                onDismissRequest = { detailAddress = null },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(address) },
                text = {
                    Text(
                        stringResource(
                            R.string.ssh_host_key_detail,
                            hostKeys[address].orEmpty()
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { detailAddress = null }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            )
        }
    }
}
