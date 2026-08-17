package com.aicode.feature.settings.domain.service

import com.aicode.core.util.FileLogger
import com.aicode.feature.agent.domain.container.CommandEngine
import com.aicode.feature.agent.domain.container.ContainerInstaller
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ProviderBalanceItem
import com.aicode.feature.settings.domain.model.ProviderBalanceResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderBalanceRunner @Inject constructor(
    private val commandEngine: CommandEngine,
    private val containerInstaller: ContainerInstaller
) {
    companion object {
        private const val TAG = "ProviderBalanceRunner"
        const val DEFAULT_BALANCE_SCRIPT = "demo_balance.py"
        const val DEFAULT_SUBSCRIPTION_SCRIPT = "demo_subscription.py"
        private const val SCRIPT_TIMEOUT_MS = 15_000L
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * 解析标准 JSON 输出为 [ProviderBalanceResult]。
         */
        fun parseBalanceJson(rawOutput: String): ProviderBalanceResult {
            if (rawOutput.isBlank()) {
                return ProviderBalanceResult(emptyList(), rawOutput)
            }

            // 截取第一个 JSON 结构对象或数组
            val jsonSnippet = extractJsonSnippet(rawOutput)
                ?: throw IllegalArgumentException("输出中未找到有效的 JSON 结构: $rawOutput")

            val items = mutableListOf<ProviderBalanceItem>()
            val parsedElement = json.parseToJsonElement(jsonSnippet)

            if (parsedElement is JsonObject) {
                val itemsArray = parsedElement["items"] as? JsonArray
                    ?: parsedElement["data"] as? JsonArray
                    ?: parsedElement["list"] as? JsonArray
                    ?: parsedElement["balances"] as? JsonArray

                if (itemsArray != null) {
                    parseArray(itemsArray, items)
                } else {
                    parseItem(parsedElement)?.let { items.add(it) }
                }
            } else if (parsedElement is JsonArray) {
                parseArray(parsedElement, items)
            }

            return ProviderBalanceResult(items = items, rawOutput = rawOutput)
        }

        private fun parseArray(jsonArray: JsonArray, outList: MutableList<ProviderBalanceItem>) {
            for (element in jsonArray) {
                val obj = element as? JsonObject ?: continue
                parseItem(obj)?.let { outList.add(it) }
            }
        }

        private fun parseItem(obj: JsonObject): ProviderBalanceItem? {
            val label = (obj["label"] ?: obj["title"] ?: obj["name"])?.primitiveStringOrNull()?.trim().orEmpty()
            if (label.isEmpty()) return null

            val value = (obj["value"] ?: obj["amount"] ?: obj["text"] ?: obj["balance"])?.primitiveStringOrNull().orEmpty()
            val suffix = (obj["suffix"] ?: obj["subTitle"])?.primitiveStringOrNull() ?: ""

            var percent = (obj["percent"] ?: obj["percentage"] ?: obj["progress"])?.primitiveDoubleOrNull()?.toFloat()
            if (percent != null && percent in 0.0001f..1.0f) {
                percent *= 100f
            }
            percent = percent?.coerceIn(0f, 100f)

            val used = (obj["used"] ?: obj["current"])?.primitiveDoubleOrNull()
            val total = (obj["total"] ?: obj["max"] ?: obj["limit"])?.primitiveDoubleOrNull()
            val unit = obj["unit"]?.primitiveStringOrNull().orEmpty()

            val subText = (obj["subText"] ?: obj["sub_text"] ?: obj["info"] ?: obj["description"] ?: obj["detail"])?.primitiveStringOrNull().orEmpty()
            val statusDot = (obj["statusDot"] ?: obj["status_dot"] ?: obj["dot"])?.primitiveBooleanOrNull() ?: false
            val colorHex = (obj["color"] ?: obj["colorHex"])?.primitiveStringOrNull()?.takeIf { it.isNotBlank() }
            val compactText = (obj["compactText"] ?: obj["compact_text"])?.primitiveStringOrNull().orEmpty()

            return ProviderBalanceItem(
                label = label,
                value = value,
                suffix = suffix,
                subText = subText,
                percent = percent,
                used = used,
                total = total,
                unit = unit,
                statusDot = statusDot,
                colorHex = colorHex,
                compactText = compactText
            )
        }

        private fun JsonElement.primitiveStringOrNull(): String? {
            return (this as? JsonPrimitive)?.content
        }

        private fun JsonElement.primitiveDoubleOrNull(): Double? {
            return (this as? JsonPrimitive)?.doubleOrNull
        }

        private fun JsonElement.primitiveBooleanOrNull(): Boolean? {
            return (this as? JsonPrimitive)?.booleanOrNull
        }

        private fun extractJsonSnippet(text: String): String? {
            val firstObj = text.indexOf('{')
            val firstArr = text.indexOf('[')

            val start = when {
                firstObj >= 0 && firstArr >= 0 -> minOf(firstObj, firstArr)
                firstObj >= 0 -> firstObj
                firstArr >= 0 -> firstArr
                else -> return null
            }

            val lastObj = text.lastIndexOf('}')
            val lastArr = text.lastIndexOf(']')
            val end = maxOf(lastObj, lastArr)

            if (end <= start) return null
            return text.substring(start, end + 1).trim()
        }
    }

    /**
     * 获取 ~/.aicode/scripts 目录下的所有可用脚本文件名列表。
     */
    fun listAvailableScripts(): List<String> {
        val scriptsDir = File(containerInstaller.aicodeDir, "scripts")
        if (!scriptsDir.exists()) {
            scriptsDir.mkdirs()
        }
        return scriptsDir.listFiles { file ->
            file.isFile && !file.name.startsWith(".")
        }?.map { it.name }?.sorted() ?: emptyList()
    }

    /**
     * 执行提供商的套餐余量脚本并解析返回结果。
     */
    suspend fun runScript(
        provider: AIProviderConfig,
        scriptPathOverride: String? = null
    ): Result<ProviderBalanceResult> = runCatching {
        val rawPath = (scriptPathOverride ?: provider.balanceScriptPath).trim()
        if (rawPath.isBlank()) {
            return@runCatching ProviderBalanceResult()
        }

        // 解析容器内路径
        val targetPath = resolveContainerScriptPath(rawPath)

        // 构造环境变量与执行命令
        val envPrefix = buildEnvPrefix(provider)
        val execCmd = buildExecCommand(targetPath)
        val fullCommand = "$envPrefix $execCmd"

        FileLogger.i(TAG, "执行套餐余量脚本 provider=${provider.name} targetPath=$targetPath")
        val result = commandEngine.runCommandSyncUnbounded(fullCommand, timeoutMs = SCRIPT_TIMEOUT_MS)
        val output = result.output.trim()

        if (result.exitCode != null && result.exitCode != 0) {
            FileLogger.w(TAG, "套餐余量脚本执行非零退出 code=${result.exitCode} output=$output")
            throw IllegalStateException("脚本退出码: ${result.exitCode}\n$output")
        }

        parseBalanceJson(output)
    }

    private fun resolveContainerScriptPath(path: String): String {
        return when {
            path.startsWith("/") -> path
            path.startsWith("~/") -> path.replaceFirst("~", "/root")
            path.startsWith(".aicode/scripts/") -> "/root/$path"
            path.startsWith("scripts/") -> "/root/.aicode/$path"
            else -> "/root/.aicode/scripts/$path"
        }
    }

    private fun buildEnvPrefix(provider: AIProviderConfig): String {
        fun escape(value: String): String {
            return "'" + value.replace("'", "'\\''") + "'"
        }
        return listOf(
            "AICODE_PROVIDER_ID=${escape(provider.id)}",
            "AICODE_PROVIDER_NAME=${escape(provider.name)}",
            "AICODE_PROVIDER_TYPE=${escape(provider.type.name)}",
            "AICODE_PROVIDER_API_KEY=${escape(provider.apiKey)}",
            "AICODE_PROVIDER_BASE_URL=${escape(provider.baseUrl)}",
            "AICODE_PROVIDER_DEFAULT_MODEL=${escape(provider.defaultModel)}",
            "AICODE_PROVIDER_SELECTED_MODEL=${escape(provider.selectedModel)}"
        ).joinToString(" ")
    }

    private fun buildExecCommand(targetPath: String): String {
        val lower = targetPath.lowercase()
        return when {
            lower.endsWith(".py") -> "python3 \"$targetPath\""
            lower.endsWith(".js") -> "node \"$targetPath\""
            lower.endsWith(".sh") || lower.endsWith(".bash") -> "bash \"$targetPath\""
            else -> "if [ -x \"$targetPath\" ]; then \"$targetPath\"; else bash \"$targetPath\"; fi"
        }
    }
}
