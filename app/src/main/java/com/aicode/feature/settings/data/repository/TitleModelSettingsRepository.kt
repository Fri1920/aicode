package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.titleModelDataStore by preferencesDataStore(name = "title_model_prefs")

/**
 * 持久化「标题总结专用模型」选择（providerId + model 两字符串）。
 *
 * 新建会话生成标题默认跟随当前聊天模型；当用户在此指定一个专用模型后，
 * 标题生成会临时切换到该专用模型发送请求，发完恢复。providerId 为空（未配置）即视为「跟随当前聊天模型」。
 */
@Singleton
class TitleModelSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val PROVIDER_ID_KEY = stringPreferencesKey("title_provider_id")
        val MODEL_KEY = stringPreferencesKey("title_model")
    }

    val providerIdFlow: Flow<String> = context.titleModelDataStore.data.map { it[PROVIDER_ID_KEY] ?: "" }

    val modelFlow: Flow<String> = context.titleModelDataStore.data.map { it[MODEL_KEY] ?: "" }

    suspend fun setTitleModel(providerId: String, model: String) {
        context.titleModelDataStore.edit {
            it[PROVIDER_ID_KEY] = providerId
            it[MODEL_KEY] = model
        }
    }

    suspend fun clear() {
        context.titleModelDataStore.edit {
            it.remove(PROVIDER_ID_KEY)
            it.remove(MODEL_KEY)
        }
    }

    suspend fun getTitleProviderId(): String = providerIdFlow.first()

    suspend fun getTitleModel(): String = modelFlow.first()
}