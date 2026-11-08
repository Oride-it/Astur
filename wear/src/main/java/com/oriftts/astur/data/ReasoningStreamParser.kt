package com.oriftts.astur.data

/** 分离兼容端放在 content 中的 <think> 内容，标签可以跨任意 SSE 分片。 */
internal class ReasoningStreamParser(
    private val onContent: suspend (String) -> Unit,
    private val onReasoning: suspend (String) -> Unit
) {
    private val pending = StringBuilder()
    private var inThinking = false
    private var contentStarted = false
    private val tags = listOf("<think>", "</think>")

    suspend fun append(text: String) {
        pending.append(text)
        drain(final = false)
    }

    suspend fun finish() = drain(final = true)

    private suspend fun emit(text: String) {
        if (text.isEmpty()) return
        if (inThinking) {
            onReasoning(text)
        } else {
            // 标签前后的换行不能提前结束思考阶段。
            val content = if (contentStarted) text else text.trimStart()
            if (content.isNotEmpty()) {
                contentStarted = true
                onContent(content)
            }
        }
    }

    private suspend fun drain(final: Boolean) {
        while (pending.isNotEmpty()) {
            val tagStart = pending.indexOf("<")
            if (tagStart < 0) {
                emit(pending.toString())
                pending.clear()
            } else if (tagStart > 0) {
                emit(pending.substring(0, tagStart))
                pending.delete(0, tagStart)
            } else {
                val text = pending.toString()
                val tag = tags.firstOrNull { text.startsWith(it) }
                when {
                    tag != null -> {
                        inThinking = tag == "<think>"
                        pending.delete(0, tag.length)
                    }
                    !final && tags.any { it.startsWith(text) } -> return
                    else -> {
                        emit("<")
                        pending.deleteCharAt(0)
                    }
                }
            }
        }
    }
}
