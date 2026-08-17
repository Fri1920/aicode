package com.aicode.feature.settings.domain.model

/**
 * 套餐余量/余额单项指标数据。
 *
 * 既能支持订阅制（进度条模式：label="5h", suffix="余量", percent=80, used=4.0, total=5.0, unit="小时"），
 * 也能支持余额制（数值/金额模式：label="当前余额", value="$12.45", subText="≈ ¥89.32 CNY"）。
 */
data class ProviderBalanceItem(
    /** 标题/标签，例如 "5h", "当前余额", "本月消费", "累计充值", "余额"。 */
    val label: String,
    /** 主内容文本（如 "$12.45", "$7.55", "80%"）。若为空且有 percent 时自动展示 "$percent%"。 */
    val value: String = "",
    /** 后缀/辅助标记（如 "余量", "CNY"）。 */
    val suffix: String = "",
    /** 底部副文本（如 "≈ ¥89.32 CNY", "今日消费 $0.83", "4.0 / 5.0 小时"）。 */
    val subText: String = "",
    /** 百分比进度（0f..100f），若存在则在卡片中间渲染进度条。 */
    val percent: Float? = null,
    /** 已用量/剩余量数值（用于自动拼接 subText）。 */
    val used: Double? = null,
    /** 总量数值（用于自动拼接 subText）。 */
    val total: Double? = null,
    /** 单位（如 "小时", "天", "tokens"）。 */
    val unit: String = "",
    /** 是否显示状态小圆点（例如余额充足绿色小圆点）。 */
    val statusDot: Boolean = false,
    /** 自定义高亮颜色代码（如 "#10B981", "#3B82F6", "#8B5CF6"）。 */
    val colorHex: String? = null,
    /** 收起状态下的紧凑文本（若为空则自动根据 label 与 value/percent 生成）。 */
    val compactText: String = ""
) {
    /** 格式化后的主显示值 */
    val displayValue: String
        get() {
            if (value.isNotBlank()) return value
            if (percent != null) return "${percent.toInt()}%"
            if (used != null) {
                return if (used % 1.0 == 0.0) used.toLong().toString() else "%.2f".format(used)
            }
            return ""
        }

    /** 格式化后的底部副信息文本 */
    val displaySubText: String
        get() {
            if (subText.isNotBlank()) return subText
            if (used != null && total != null) {
                val formattedUsed = if (used % 1.0 == 0.0) used.toLong().toString() else "%.1f".format(used)
                val formattedTotal = if (total % 1.0 == 0.0) total.toLong().toString() else "%.1f".format(total)
                return if (unit.isNotBlank()) "$formattedUsed / $formattedTotal $unit" else "$formattedUsed / $formattedTotal"
            }
            return ""
        }

    /** 是否有进度条 */
    val hasProgress: Boolean
        get() = percent != null && percent in 0f..100f
}

/**
 * 套餐余量查询结果。
 */
data class ProviderBalanceResult(
    val items: List<ProviderBalanceItem> = emptyList(),
    val rawOutput: String = ""
)

/**
 * 套餐余量状态。
 */
sealed interface ProviderBalanceState {
    data object Idle : ProviderBalanceState
    data object Loading : ProviderBalanceState
    data class Success(val result: ProviderBalanceResult) : ProviderBalanceState
    data class Error(val message: String, val rawOutput: String = "") : ProviderBalanceState
}
