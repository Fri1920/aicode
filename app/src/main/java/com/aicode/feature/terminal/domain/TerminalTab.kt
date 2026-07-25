package com.aicode.feature.terminal.domain

import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * 终端标签的运行状态。Finished 保留在列表里不移除，供用户/AI 回看输出。
 */
sealed interface RunState {
    data object Running : RunState
    data class Finished(val exitCode: Int) : RunState
}

/**
 * 后台命令结束时 emit 的事件，供 ViewModel 订阅后通知 AI。
 */
data class TabFinishedEvent(
    val tabId: String,
    val command: String?,
    val exitCode: Int,
    /** 发起该后台命令的会话 id；回调据此路由回原会话，而非用户当前所在会话。 */
    val sourceSessionId: String?
)

/**
 * 一个终端标签：会话 + 渲染视图 + 元数据。
 *
 * [view] 由 Compose 在创建 [TerminalView] 后回填；切换标签时复用同一会话、重新挂载视图。
 * [client] 的 viewProvider 始终读 [view]，故无论视图如何重建都能把输出刷到当前挂载的视图。
 *
 * 本地与远程终端模式共用此类型——区别仅在 [session] 背后的 [com.termux.terminal.SessionBackend]
 * （本地 SubprocessBackend fork 进程，远程 SSH shell 流）。
 */
class TerminalTab(
    val id: String,
    title: String,
    val session: TerminalSession,
    val isBackground: Boolean,
    val command: String?,
    val notifyOnExit: Boolean = false,
    /** 发起该后台命令的会话 id；交互标签为 null。回调据此路由回原会话。 */
    val sourceSessionId: String? = null,
    runState: RunState
) {
    var title: String = title
        internal set

    @Volatile
    var view: TerminalView? = null

    var runState: RunState = runState
        internal set
}

/**
 * 终端标签摘要，供 AI 的 terminal 工具列出标签时使用（不携带 session/view 等运行时对象）。
 */
data class TabInfo(
    val id: String,
    val title: String,
    val isBackground: Boolean,
    val running: Boolean,
    val command: String?
)
