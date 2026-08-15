package com.aicode.feature.credentials.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.core.ui.FloatingTabBar
import com.aicode.core.ui.FloatingTabItem
import com.aicode.feature.credentials.domain.model.GitCredential
import com.aicode.feature.credentials.presentation.CredentialViewModel
import com.aicode.feature.settings.presentation.component.settingsPageBackground
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.Edit2
import compose.icons.feathericons.Key
import compose.icons.feathericons.Plus
import compose.icons.feathericons.User

/**
 * 凭据与署名独立页：底部悬浮 tab 切换「署名」与「凭据」两个标签页。
 * 顶栏「+」新增凭据，编辑/新增态用 [CredentialEditorSheet] 弹出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialScreen(
    viewModel: CredentialViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 每次进入重新读署名：用户可能在终端改过项目级/全局署名，避免回显陈旧空值。
    LaunchedEffect(Unit) { viewModel.refreshIdentity() }

    // toast → Snackbar 一次性消费。
    LaunchedEffect(state.toast) {
        state.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeToast()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    // editingCredential != null -> 编辑现有；editingCredential == null && isAddingCredential -> 新增；否则列表态。
    var editingCredential by remember { mutableStateOf<GitCredential?>(null) }
    var isAddingCredential by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_credentials_and_identity)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { isAddingCredential = true }) {
                        Icon(FeatherIcons.Plus, contentDescription = stringResource(R.string.credential_add))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.lg)
                    ) {
                        GitUserIdentityCard(
                            initialName = state.userName,
                            initialEmail = state.userEmail,
                            initialRepoUrl = state.repoUrl,
                            globalHint = state.globalUserName,
                            onSave = viewModel::saveUserIdentity
                        )
                    }
                    1 -> if (state.credentials.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(Spacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.credential_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(Spacing.lg),
                            verticalArrangement = Arrangement.spacedBy(Spacing.md)
                        ) {
                            items(state.credentials, key = { it.id }) { cred ->
                                CredentialItem(
                                    credential = cred,
                                    onEdit = { editingCredential = cred }
                                )
                            }
                        }
                    }
                }
            }

            FloatingTabBar(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                items = listOf(
                    FloatingTabItem(FeatherIcons.User, stringResource(R.string.git_tab_identity)),
                    FloatingTabItem(FeatherIcons.Key, stringResource(R.string.git_tab_credentials))
                ),
                maskColor = settingsPageBackground(),
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    val editing = editingCredential
    if (editing != null) {
        CredentialEditorSheet(
            initial = editing,
            onDismiss = { editingCredential = null },
            onSave = { viewModel.saveCredential(it); editingCredential = null },
            onDelete = { viewModel.deleteCredential(it); editingCredential = null }
        )
    }

    if (isAddingCredential) {
        CredentialEditorSheet(
            initial = null,
            onDismiss = { isAddingCredential = false },
            onSave = { viewModel.saveCredential(it); isAddingCredential = false }
        )
    }
}

@Composable
private fun CredentialItem(
    credential: GitCredential,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = credential.label.ifBlank { "${credential.host} · ${credential.username}" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${credential.host} · ${credential.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    FeatherIcons.Edit2,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
