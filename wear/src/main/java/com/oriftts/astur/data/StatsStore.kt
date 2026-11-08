package com.oriftts.astur.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 一条 Token 消耗记录。 */
@Serializable
data class TokenRecord(
    val timestamp: Long,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * 统计仓库：Token 消耗历史 + 人物设定（系统提示词），持久化到 DataStore。
 * 复用 SettingsStore 中的同一个 DataStore 文件。
 */
class StatsRepository(context: Context) {

    private val dataStore = context.applicationContext.asturDataStore
    private val json = Json { ignoreUnknownKeys = true }

    /** Token 消耗历史按写入顺序保存。 */
    val history: Flow<List<TokenRecord>> = dataStore.data
        .map { it[KEY_HISTORY] }
        .distinctUntilChanged()
        .map(::decodeHistory)
        .flowOn(Dispatchers.Default)

    /** 人物设定·名字（作为 system 消息一部分）。 */
    val personaName: Flow<String> = dataStore.data.map { it[KEY_PERSONA_NAME] ?: "" }
        .flowOn(Dispatchers.Default)

    /** 人物设定·性格描述（作为 system 消息一部分）。 */
    val personaText: Flow<String> = dataStore.data.map { it[KEY_PERSONA_TEXT] ?: "" }
        .flowOn(Dispatchers.Default)

    suspend fun currentPersonaName(): String = personaName.first()

    suspend fun currentPersonaText(): String = personaText.first()

    suspend fun savePersona(name: String, text: String) {
        dataStore.edit {
            it[KEY_PERSONA_NAME] = name.trim()
            it[KEY_PERSONA_TEXT] = text.trim()
        }
    }

    /** 记录一次对话的 Token 用量。 */
    suspend fun recordUsage(model: String, usage: TokenUsage) {
        dataStore.edit { p ->
            val current = decodeHistory(p[KEY_HISTORY])
            val updated = current + TokenRecord(
                timestamp = System.currentTimeMillis(),
                model = model,
                promptTokens = usage.promptTokens,
                completionTokens = usage.completionTokens,
                totalTokens = usage.totalTokens
            )
            p[KEY_HISTORY] = json.encodeToString(
                ListSerializer(TokenRecord.serializer()),
                updated
            )
        }
    }

    private fun decodeHistory(raw: String?): List<TokenRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(TokenRecord.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY_HISTORY = stringPreferencesKey("token_history")
        val KEY_PERSONA_NAME = stringPreferencesKey("persona_name")
        val KEY_PERSONA_TEXT = stringPreferencesKey("persona_text")
    }
}
