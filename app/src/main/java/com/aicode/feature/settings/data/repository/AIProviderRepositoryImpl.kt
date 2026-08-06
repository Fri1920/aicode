package com.aicode.feature.settings.data.repository

import com.aicode.core.util.FileLogger
import com.aicode.feature.settings.data.local.dao.AIProviderDao
import com.aicode.feature.settings.data.local.entity.AIProviderEntity
import com.aicode.feature.settings.domain.model.AIProviderConfig
import com.aicode.feature.settings.domain.model.ModelMetadata
import com.aicode.feature.settings.domain.model.ProviderType
import com.aicode.feature.settings.domain.repository.AIProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIProviderRepositoryImpl @Inject constructor(
    private val aiProviderDao: AIProviderDao
) : AIProviderRepository {

    private companion object {
        const val TAG = "AIProviderRepo"
    }

    override fun getAllProviders(): Flow<List<AIProviderConfig>> {
        return aiProviderDao.getAllProviders().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override fun getActiveProvider(): Flow<AIProviderConfig?> {
        return aiProviderDao.getActiveProvider().map { it?.toDomainModel() }
    }

    override suspend fun getActiveProviderSync(): AIProviderConfig? {
        return aiProviderDao.getActiveProviderSync()?.toDomainModel()
    }

    override suspend fun getProviderById(id: String): AIProviderConfig? {
        return aiProviderDao.getProviderById(id)?.toDomainModel()
    }

    override suspend fun saveProvider(provider: AIProviderConfig) {
        FileLogger.i(TAG, "保存提供商 id=${provider.id} name=${provider.name} active=${provider.isActive}")
        val entity = provider.toEntity()
        if (provider.isActive) {
            aiProviderDao.deactivateAllProviders()
        }
        aiProviderDao.insertProvider(entity)
    }

    override suspend fun deleteProvider(id: String) {
        FileLogger.i(TAG, "删除提供商 id=$id")
        aiProviderDao.deleteProvider(id)
    }

    override suspend fun setActiveProvider(id: String) {
        FileLogger.i(TAG, "切换启用提供商 id=$id")
        aiProviderDao.deactivateAllProviders()
        aiProviderDao.activateProvider(id)
    }

    override suspend fun setSelectedModel(id: String, model: String) {
        FileLogger.i(TAG, "切换模型 provider=$id model=$model")
        aiProviderDao.setSelectedModel(id, model)
    }

    override suspend fun updateModels(id: String, models: List<String>) {
        FileLogger.d(TAG, "更新模型列表 provider=$id 共 ${models.size} 个")
        aiProviderDao.setModels(id, models.joinToString("\n"))
    }

    override suspend fun setProviderEnabled(id: String, isEnabled: Boolean) {
        FileLogger.i(TAG, "设置提供商状态 provider=$id isEnabled=$isEnabled")
        aiProviderDao.setProviderEnabled(id, isEnabled)
    }

    override suspend fun ensureActiveProvider() {
        // 已有激活项则无需处理。
        if (aiProviderDao.getActiveProviderSync() != null) return
        val first = aiProviderDao.getAllProviders().first().firstOrNull() ?: return
        FileLogger.i(TAG, "无激活提供商，自动激活首个: ${first.id} (${first.name})")
        aiProviderDao.deactivateAllProviders()
        aiProviderDao.activateProvider(first.id)
    }

    private fun AIProviderEntity.toDomainModel(): AIProviderConfig {
        val capabilities = mutableMapOf<String, ModelMetadata>()
        val modelList = models.split("\n").mapNotNull { line ->
            val (name, meta) = decodeModelLine(line)
            if (name.isEmpty()) return@mapNotNull null
            if (meta != null) capabilities[name] = meta
            name
        }
        return AIProviderConfig(
            id = id,
            name = name,
            type = try { ProviderType.valueOf(type) } catch (e: Exception) { ProviderType.OPENAI },
            apiKey = apiKey,
            baseUrl = baseUrl,
            defaultModel = defaultModel,
            isActive = isActive,
            models = modelList,
            selectedModel = selectedModel.ifBlank { defaultModel },
            isEnabled = isEnabled,
            useFullUrl = useFullUrl,
            useResponseApi = useResponseApi,
            modelCapabilities = capabilities
        )
    }

    private fun AIProviderConfig.toEntity(): AIProviderEntity {
        return AIProviderEntity(
            id = id,
            name = name,
            type = type.name,
            apiKey = apiKey,
            baseUrl = baseUrl,
            useFullUrl = useFullUrl,
            defaultModel = defaultModel,
            isActive = isActive,
            models = models.joinToString("\n") { encodeModelLine(it, modelCapabilities[it]) },
            selectedModel = selectedModel,
            isEnabled = isEnabled,
            useResponseApi = useResponseApi
        )
    }

    /** 解析单行模型：`name|vision|tools|input|output`；旧数据（纯名字）返回 null 能力。 */
    private fun decodeModelLine(line: String): Pair<String, ModelMetadata?> {
        val parts = line.split("|")
        val name = parts[0].trim()
        if (parts.size < 5) return name to null
        val vision = parts[1] == "1"
        val tools = parts[2] == "1"
        val input = parts[3].toIntOrNull()
        val output = parts[4].toIntOrNull()
        return name to ModelMetadata(
            id = name,
            contextTokens = input ?: 0,
            inputTokens = input,
            outputTokens = output,
            supportsVision = vision,
            supportsTools = tools
        )
    }

    /** 编码单行模型：无能力时保持纯名字以兼容旧数据。 */
    private fun encodeModelLine(name: String, meta: ModelMetadata?): String {
        if (meta == null) return name
        return "$name|${if (meta.supportsVision) 1 else 0}|${if (meta.supportsTools) 1 else 0}|${meta.inputTokens ?: ""}|${meta.outputTokens ?: ""}"
    }
}
