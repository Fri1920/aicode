package com.aicode.feature.settings.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aicode.feature.agent.domain.container.ContainerProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.containerDataStore by preferencesDataStore(name = "container_prefs")

/**
 * 持久化当前选中的容器 profile 与用户自定义 profile 列表。
 *
 * DataStore 用法与 [ThemeSettingsRepository] 一致（构造注入即可，无需 DI module）。
 * 无 [ACTIVE_PROFILE_ID_KEY] 时返回 [ContainerProfile.BUILTIN_ID]，等同改动前——默认内置 Alpine。
 */
@Singleton
class ContainerSettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        val ACTIVE_PROFILE_ID_KEY = stringPreferencesKey("active_profile_id")
        val CUSTOM_PROFILES_KEY = stringPreferencesKey("custom_profiles_json")
        /** 远程工作区模式下本地 MCP stdio 等服务的运行容器；无值时默认内置 Alpine。 */
        val DEFAULT_CONTAINER_ID_KEY = stringPreferencesKey("default_container_profile_id")
        /** 首次启动是否已写入内置 Alpine 默认项；置位后用户删光列表不再自动补回。 */
        val INITIALIZED_KEY = booleanPreferencesKey("initialized")
        val profileSerializer = ListSerializer(ContainerProfile.serializer())
        val json = Json { ignoreUnknownKeys = true }
    }

    /** 当前选中的 profile id；无值时默认内置 Alpine。 */
    val activeProfileIdFlow: Flow<String> = context.containerDataStore.data.map { prefs ->
        prefs[ACTIVE_PROFILE_ID_KEY]?.takeIf { it.isNotBlank() } ?: ContainerProfile.BUILTIN_ID
    }

    /** 远程模式下的默认容器 id；无值时默认内置 Alpine。 */
    val defaultContainerIdFlow: Flow<String> = context.containerDataStore.data.map { prefs ->
        prefs[DEFAULT_CONTAINER_ID_KEY]?.takeIf { it.isNotBlank() } ?: ContainerProfile.BUILTIN_ID
    }

    /** 用户自定义 profile 列表（不含内置）。解析失败回退空列表。 */
    val customProfilesFlow: Flow<List<ContainerProfile>> = context.containerDataStore.data.map { prefs ->
        prefs[CUSTOM_PROFILES_KEY]?.let { raw ->
            runCatching { json.decodeFromString(profileSerializer, raw) }.getOrNull()
        } ?: emptyList()
    }

    suspend fun setActiveProfile(id: String) {
        context.containerDataStore.edit { it[ACTIVE_PROFILE_ID_KEY] = id }
    }

    suspend fun setDefaultContainerId(id: String) {
        context.containerDataStore.edit { it[DEFAULT_CONTAINER_ID_KEY] = id }
    }

    /**
     * 首次启动（或旧版本升级）兜底：把内置 Alpine 补入列表（若缺），保证列表始终有内置项。
     * 置位初始化标记后不再自动补回——用户后续删光列表由空态手动恢复。
     * 存量设备已配自定义镜像时同样补入（旧版列表始终含内置项，升级不能让它消失）。
     */
    suspend fun ensureBuiltinDefault() {
        context.containerDataStore.edit { prefs ->
            if (prefs[INITIALIZED_KEY] == true) return@edit
            prefs[INITIALIZED_KEY] = true
            val current = prefs[CUSTOM_PROFILES_KEY]?.let { raw ->
                runCatching { json.decodeFromString(profileSerializer, raw) }.getOrNull()
            } ?: emptyList()
            if (current.none { it.id == ContainerProfile.BUILTIN_ID }) {
                prefs[CUSTOM_PROFILES_KEY] = json.encodeToString(
                    profileSerializer, listOf(ContainerProfile.BUILTIN_ALPINE) + current
                )
            }
        }
    }

    /** 新增或覆盖同名 id 的自定义 profile。 */
    suspend fun upsertCustomProfile(profile: ContainerProfile) {
        context.containerDataStore.edit { prefs ->
            val current = prefs[CUSTOM_PROFILES_KEY]?.let { raw ->
                runCatching { json.decodeFromString(profileSerializer, raw) }.getOrNull()
            } ?: emptyList()
            val merged = (current.filterNot { it.id == profile.id } + profile)
            prefs[CUSTOM_PROFILES_KEY] = json.encodeToString(profileSerializer, merged)
        }
    }

    suspend fun deleteCustomProfile(id: String) {
        context.containerDataStore.edit { prefs ->
            val current = prefs[CUSTOM_PROFILES_KEY]?.let { raw ->
                runCatching { json.decodeFromString(profileSerializer, raw) }.getOrNull()
            } ?: emptyList()
            prefs[CUSTOM_PROFILES_KEY] =
                json.encodeToString(profileSerializer, current.filterNot { it.id == id })
        }
    }
}
