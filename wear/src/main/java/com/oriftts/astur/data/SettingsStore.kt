package com.oriftts.astur.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal val Context.asturDataStore by preferencesDataStore(name = "astur_settings")

/** 一份完整的 API 配置。 */
data class ApiConfig(
    val providerId: String = ModelProvider.DEEPSEEK.id,
    val baseUrl: String = ModelProvider.DEEPSEEK.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = ""
) {
    /** 聊天要求三项齐全。 */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

/**
 * 配置仓库：基于 DataStore Preferences 持久化，重启自动恢复。
 * 所有流的 map 变换经 flowOn 移到后台执行——DataStore 任一键写入都会广播给全部收集者，
 * 若在主线程 map，每次保存/切开关都会让 UI 线程做整包解析，造成全局顿挫。
 */
class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.asturDataStore

    /** 实时配置流。 */
    val config: Flow<ApiConfig> = dataStore.data.map { p ->
        ApiConfig(
            providerId = p[KEY_PROVIDER] ?: ModelProvider.DEEPSEEK.id,
            baseUrl = p[KEY_BASE_URL] ?: ModelProvider.DEEPSEEK.defaultBaseUrl,
            apiKey = p[KEY_API_KEY] ?: "",
            model = p[KEY_MODEL] ?: ""
        )
    }.flowOn(Dispatchers.Default)

    /** 读取当前配置（一次性）。 */
    suspend fun current(): ApiConfig = config.first()

    /** 保存配置；URL 去尾部斜杠、Key/模型去空白。 */
    suspend fun save(config: ApiConfig) {
        dataStore.edit { p ->
            p[KEY_PROVIDER] = config.providerId
            p[KEY_BASE_URL] = config.baseUrl.trim().trimEnd('/')
            p[KEY_API_KEY] = config.apiKey.trim()
            p[KEY_MODEL] = config.model.trim()
        }
    }

    /** 节省额度开关：开启后限制回复长度，减少 Token 消耗（默认关闭）。 */
    val saveQuota: Flow<Boolean> = dataStore.data.map { it[KEY_SAVE_QUOTA] ?: false }
        .flowOn(Dispatchers.Default)

    suspend fun currentSaveQuota(): Boolean = saveQuota.first()

    suspend fun setSaveQuota(value: Boolean) {
        dataStore.edit { it[KEY_SAVE_QUOTA] = value }
    }

    /** 显示时间开关：控制顶部悬浮时间是否显示（默认开启）。 */
    val showTime: Flow<Boolean> = dataStore.data.map { it[KEY_SHOW_TIME] ?: true }
        .flowOn(Dispatchers.Default)

    suspend fun setShowTime(value: Boolean) {
        dataStore.edit { it[KEY_SHOW_TIME] = value }
    }

    /** 保持屏幕常亮：聊天过程中防止自动息屏（默认关闭）。 */
    val keepScreenOn: Flow<Boolean> = dataStore.data.map { it[KEY_KEEP_SCREEN_ON] ?: false }
        .flowOn(Dispatchers.Default)

    suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { it[KEY_KEEP_SCREEN_ON] = value }
    }

    /** 发送振动反馈：收到新回复时轻微振动（默认开启）。 */
    val haptics: Flow<Boolean> = dataStore.data.map { it[KEY_HAPTICS] ?: true }
        .flowOn(Dispatchers.Default)

    suspend fun setHaptics(value: Boolean) {
        dataStore.edit { it[KEY_HAPTICS] = value }
    }

    /** OPPO Watch 模式：开启后提供应用内左缘右滑返回手势（部分 OPPO 手表禁用了系统返回手势）。 */
    val oppoMode: Flow<Boolean> = dataStore.data.map { it[KEY_OPPO_MODE] ?: false }
        .flowOn(Dispatchers.Default)

    suspend fun setOppoMode(value: Boolean) {
        dataStore.edit { it[KEY_OPPO_MODE] = value }
    }

    private companion object {
        val KEY_PROVIDER = stringPreferencesKey("provider")
        val KEY_BASE_URL = stringPreferencesKey("base_url")
        val KEY_API_KEY = stringPreferencesKey("api_key")
        val KEY_MODEL = stringPreferencesKey("model")
        val KEY_SAVE_QUOTA = booleanPreferencesKey("save_quota")
        val KEY_SHOW_TIME = booleanPreferencesKey("show_time")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_OPPO_MODE = booleanPreferencesKey("oppo_mode")
    }
}
