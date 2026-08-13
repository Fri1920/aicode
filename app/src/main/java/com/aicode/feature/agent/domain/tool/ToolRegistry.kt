package com.aicode.feature.agent.domain.tool

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Singleton

@Singleton
class ToolRegistry {
    private val tools = ConcurrentHashMap<String, AgentTool>()

    fun register(name: String, tool: AgentTool) {
        tools[name] = tool
    }

    fun getTool(name: String): AgentTool? {
        return tools[name]
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun getToolNames(): Set<String> {
        return tools.keys.toSet()
    }

    /**
     * 返回可用工具列表。PLAN 模式下仍返回全部工具定义（让 AI 知道这些工具存在，可在计划中引用），
     * 写操作工具由 [com.aicode.feature.agent.domain.permission.ToolPermissionPolicyEngine] 在运行时拦截并返回 PLAN_MODE_REJECTED。
     */
    fun getAvailableTools(): List<AgentTool> {
        return tools.values.toList()
    }

    fun hasTool(name: String): Boolean {
        return tools.containsKey(name)
    }
}
