package com.aicode.feature.settings.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicode.R
import com.aicode.core.theme.Radius
import com.aicode.core.theme.Spacing
import com.aicode.feature.agent.domain.skill.SkillScope
import com.aicode.feature.settings.presentation.SkillUiEntry
import compose.icons.FeatherIcons
import compose.icons.feathericons.Book
import compose.icons.feathericons.ChevronRight

/**
 * 技能二级页：与「工具授权」一致的折叠分组列表——「当前项目 / 全局」两组各自可折叠，
 * 每行一个技能（图标 + 名称 + 描述），左滑删除，点击行进入详情。
 */
@Composable
internal fun SkillsSection(
    projectName: String?,
    entries: List<SkillUiEntry>,
    onDelete: (SkillUiEntry) -> Unit,
    onOpenDetail: (SkillUiEntry) -> Unit
) {
    val projectSkills = entries.filter { it.scope == SkillScope.PROJECT }
    val globalSkills = entries.filter { it.scope == SkillScope.GLOBAL }

    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(Radius.lg)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        FeatherIcons.Book,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.skills_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.skills_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var projectExpanded by rememberSaveable { mutableStateOf(true) }
    var globalExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        CollapsibleGroupHeader(
            text = if (projectName != null) {
                stringResource(R.string.perm_current_project, projectName)
            } else {
                stringResource(R.string.perm_current_project_none)
            },
            expanded = projectExpanded,
            onToggle = { projectExpanded = !projectExpanded }
        )
        AnimatedVisibility(visible = projectExpanded) {
            SettingsGroup {
                if (projectSkills.isEmpty()) {
                    SkillEmptyHint(stringResource(R.string.skills_no_project_skills))
                } else {
                    projectSkills.forEachIndexed { index, entry ->
                        if (index > 0) SettingsDivider()
                        SkillRow(
                            entry = entry,
                            onDelete = { onDelete(entry) },
                            onClick = { onOpenDetail(entry) }
                        )
                    }
                }
            }
        }

        CollapsibleGroupHeader(
            text = stringResource(R.string.perm_global),
            expanded = globalExpanded,
            onToggle = { globalExpanded = !globalExpanded }
        )
        AnimatedVisibility(visible = globalExpanded) {
            SettingsGroup {
                if (globalSkills.isEmpty()) {
                    SkillEmptyHint(stringResource(R.string.skills_no_global_skills))
                } else {
                    globalSkills.forEachIndexed { index, entry ->
                        if (index > 0) SettingsDivider()
                        SkillRow(
                            entry = entry,
                            onDelete = { onDelete(entry) },
                            onClick = { onOpenDetail(entry) }
                        )
                    }
                }
            }
        }
    }
}

/** 单个技能行：图标 + 名称/描述 + 右箭头；左滑删除，点击行进入详情。 */
@Composable
private fun SkillRow(
    entry: SkillUiEntry,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val light = settingsLightMode()
    val rowBackground = if (light) Color.White else MaterialTheme.colorScheme.surface

    SwipeToDeleteRow(onDelete = onDelete, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(rowBackground)
                .padding(start = Spacing.lg, end = Spacing.xs, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FeatherIcons.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                // 启停状态点：启用为主色调，禁用为灰色（与 MCP 行图标右下角一致）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(1.dp)
                        .size(8.dp)
                        .background(
                            color = if (entry.disabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(Radius.pill)
                        )
                        .border(1.5.dp, rowBackground, RoundedCornerShape(Radius.pill))
                )
            }

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = if (light) Color(0xFF0F0F0F) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.description.ifBlank { stringResource(R.string.mcp_no_description) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (light) Color(0xFF8E8E93) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Icon(
                imageVector = FeatherIcons.ChevronRight,
                contentDescription = null,
                tint = if (light) Color(0xFFC7C7CC) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 分组内空状态：一行灰字，与行内容对齐。 */
@Composable
private fun SkillEmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = 12.dp)
    )
}
