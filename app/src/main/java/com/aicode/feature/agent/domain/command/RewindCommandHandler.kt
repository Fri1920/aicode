package com.aicode.feature.agent.domain.command

import javax.inject.Inject

/**
 * /rewind —— 打开 Checkpoint 撤销与检查点控制台
 */
class RewindCommandHandler @Inject constructor() : SlashCommandHandler {
    override val trigger = "/rewind"
    override val label = "检查点撤销"
    override val description = "打开检查点菜单，回滚代码或压缩对话"

    override fun execute(context: SlashCommandContext) {
        context.openRewindConsole()
    }
}
