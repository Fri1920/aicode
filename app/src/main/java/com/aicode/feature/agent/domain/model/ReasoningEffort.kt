package com.aicode.feature.agent.domain.model

/**
 * 思考强度：映射到 OpenAI o 系模型的 reasoning_effort 参数（low/medium/high）。
 * Anthropic（thinking）与 Gemini 暂不支持，传入时被适配器忽略。
 */
enum class ReasoningEffort(val apiValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high")
}
