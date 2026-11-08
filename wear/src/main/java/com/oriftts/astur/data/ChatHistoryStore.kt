package com.oriftts.astur.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.chatHistoryStore by preferencesDataStore(name = "astur_chat_history")

@Serializable
internal data class ChatTurn(
    val question: String,
    val answer: String = "",
    val complete: Boolean = false
) {
    fun messages(): List<ChatMessage> = buildList {
        add(ChatMessage("user", question))
        if (answer.isNotBlank()) add(ChatMessage("assistant", answer))
    }
}

class ChatHistoryRepository(context: Context) {
    private val store = context.applicationContext.chatHistoryStore
    private val key = stringPreferencesKey("turns")

    internal suspend fun current(): List<ChatTurn> = withContext(Dispatchers.IO) {
        decode(store.data.first()[key])
    }

    internal suspend fun append(turn: ChatTurn): Int = withContext(Dispatchers.IO) {
        var index = 0
        store.edit { prefs ->
            val turns = decode(prefs[key])
            index = turns.size
            prefs[key] = Json.encodeToString(turns + turn)
        }
        index
    }

    internal suspend fun update(index: Int, turn: ChatTurn) = withContext(Dispatchers.IO) {
        store.edit { prefs ->
            val turns = decode(prefs[key]).toMutableList()
            turns[index] = turn
            prefs[key] = Json.encodeToString(turns)
        }
    }

    private fun decode(raw: String?): List<ChatTurn> =
        raw?.let { Json.decodeFromString<List<ChatTurn>>(it) }.orEmpty()
}
