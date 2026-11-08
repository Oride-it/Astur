package com.oriftts.astur.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriftts.astur.data.ChatClient
import com.oriftts.astur.data.ChatHistoryRepository
import com.oriftts.astur.data.ChatMessage
import com.oriftts.astur.data.ChatTurn
import com.oriftts.astur.data.SettingsRepository
import com.oriftts.astur.data.StatsRepository
import com.oriftts.astur.data.TokenUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val error: String? = null,
    val inputExpanded: Boolean = false,
    // 当前正在流式输出的思考过程；正文开始后清空（思考收起，展示真实回答）
    val streamingReasoning: String = "",
    val loadingHistory: Boolean = true
)

/**
 * 聊天页状态：
 * - 聊天记录保存在本机，重启后恢复
 * - 流式输出按 120ms 限流刷新，避免高频重组导致卡顿
 * - 自动附加人物设定（system 消息），并采集 Token 用量写入统计
 */
class ChatViewModel(
    private val settings: SettingsRepository,
    private val stats: StatsRepository,
    private val historyStore: ChatHistoryRepository
) : ViewModel() {

    /** 振动反馈开关（供 UI 层在收到新回复时触发轻微振动）。 */
    val haptics: Flow<Boolean> = settings.haptics

    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui.asStateFlow()

    private val _history = MutableStateFlow<List<ChatMessage>>(emptyList())
    val history: StateFlow<List<ChatMessage>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val messages = historyStore.current().flatMap(ChatTurn::messages)
                _history.value = messages
                _ui.update { it.copy(messages = messages) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { it.copy(error = "无法读取聊天记录") }
            } finally {
                _ui.update { it.copy(loadingHistory = false) }
            }
        }
    }

    /** 输入栏展开状态（独立流）：主界面据此启用右滑返回，不随流式输出高频变化。 */
    val inputExpanded: StateFlow<Boolean> = _ui
        .map { it.inputExpanded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onInputChange(value: String) {
        _ui.value = _ui.value.copy(input = value)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    /** 展开底部输入栏 */
    fun expandInput() {
        _ui.value = _ui.value.copy(inputExpanded = true)
    }

    /** 收起底部输入栏（回到圆形按钮） */
    fun collapseInput() {
        _ui.value = _ui.value.copy(inputExpanded = false)
    }

    fun send() {
        val snapshot = _ui.value
        val text = snapshot.input.trim()
        if (text.isEmpty() || snapshot.sending || snapshot.loadingHistory) return
        _ui.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            var collapse = false
            try {
                collapse = sendTurn(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = e.message ?: "请求失败") }
            } finally {
                _ui.update {
                    it.copy(
                        sending = false,
                        inputExpanded = it.inputExpanded && !collapse,
                        streamingReasoning = ""
                    )
                }
            }
        }
    }

    private suspend fun sendTurn(text: String): Boolean {
        val config = settings.current()
        check(config.isComplete) { "请先在模型配置中填好 URL、API Key 和模型" }
        val saveQuota = settings.currentSaveQuota()
        val personaName = stats.currentPersonaName().trim()
        val persona = buildList {
            if (personaName.isNotEmpty()) add("你的名字是$personaName")
            stats.currentPersonaText().trim().takeIf { it.isNotEmpty() }?.let(::add)
            if (saveQuota) add("请直接、简洁地回答当前问题。")
        }.joinToString("\n")

        val turns = historyStore.current()
        val turn = ChatTurn(question = text)
        val index = historyStore.append(turn)
        val previousMessages = turns.flatMap(ChatTurn::messages)
        val visibleMessages = previousMessages + turn.messages()
        _history.value = visibleMessages
        val apiMessages = buildList {
            if (persona.isNotEmpty()) add(ChatMessage("system", persona))
            addAll(recentContext(turns))
            add(ChatMessage("user", text))
        }

        val reply = StringBuilder()
        val thinking = StringBuilder()
        var usage: TokenUsage? = null
        var lastFlushAt = 0L
        fun render() {
            val answer = reply.toString()
            _ui.update {
                it.copy(
                    messages = visibleMessages + ChatMessage("assistant", answer),
                    streamingReasoning = if (answer.isBlank()) thinking.toString() else ""
                )
            }
        }
        fun throttledRender(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastFlushAt >= 120) {
                lastFlushAt = now
                render()
            }
        }

        _ui.update { it.copy(input = "", messages = visibleMessages + ChatMessage("assistant", "")) }
        var error: String? = null
        try {
            ChatClient.streamChat(
                config = config,
                messages = apiMessages,
                onReasoning = { delta ->
                    if (reply.isBlank()) {
                        thinking.append(delta)
                        throttledRender()
                    }
                },
                onDelta = { delta ->
                    val firstContent = reply.isBlank()
                    reply.append(delta)
                    throttledRender(force = firstContent && reply.isNotBlank())
                },
                onUsage = { usage = it },
                maxTokens = if (saveQuota) 2048 else null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "模型请求失败"
        }
        val completed = turn.copy(answer = reply.toString(), complete = error == null)
        try {
            if (completed.answer.isNotBlank()) historyStore.update(index, completed)
            usage?.let { stats.recordUsage(config.model, it) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            error = listOfNotNull(error, "保存本次记录失败").joinToString("。")
        }
        val messages = previousMessages + completed.messages()
        _history.value = messages
        _ui.update {
            it.copy(
                messages = messages,
                input = if (error != null && completed.answer.isBlank()) text else it.input,
                error = error
            )
        }
        return completed.answer.isNotBlank()
    }
}

/** Keep complete turns together and bound request size independently of the local archive. */
internal fun recentContext(turns: List<ChatTurn>): List<ChatMessage> {
    val newestFirst = mutableListOf<ChatTurn>()
    var remaining = 12_000
    for (turn in turns.asReversed()) {
        if (!turn.complete) continue
        val size = turn.question.length + turn.answer.length
        if (size > remaining || newestFirst.size == 20) break
        newestFirst.add(turn)
        remaining -= size
    }
    return newestFirst.asReversed().flatMap(ChatTurn::messages)
}
