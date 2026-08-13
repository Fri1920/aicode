package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.visionModelDataStore by preferencesDataStore(name = "vision_model_prefs")

/**
 * 持久化「识图专用模型」选择（providerId + model 两字符串）。
 *
 * 识图（viewImage）默认跟随当前聊天模型；当用户在此指定一个支持 vision 的模型后，
 * 若当前聊天模型不支持图片输入，识图那一轮会临时切换到该专用模型发送，发完恢复聊天模型。
 * DataStore 用法与 [KeepaliveSettingsRepository] / [LogSettingsRepository] 一致。
 */
@Singleton
class VisionModelSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) : ModelSelectionSettingsRepository(
    context.visionModelDataStore, "vision_provider_id", "vision_model"
) {

    /** 写入识图专用模型（设空字符串即等同 [clear]）。 */
    suspend fun setVisionModel(providerId: String, model: String) = setSelection(providerId, model)

    /** 清空配置——回退到「跟随当前聊天模型」。 */
    suspend fun clear() = clearSelection()

    /** 读取一次当前识图专用 providerId（冷读用）。 */
    suspend fun getVisionProviderId(): String = readProviderId()

    /** 读取一次当前识图专用 model（冷读用）。 */
    suspend fun getVisionModel(): String = readModel()
}
