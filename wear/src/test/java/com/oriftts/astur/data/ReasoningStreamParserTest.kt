package com.oriftts.astur.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReasoningStreamParserTest {
    @Test
    fun separatesThinkingAcrossEveryPossibleChunkBoundary() = runBlocking {
        val input = "\n<think>先分析问题，再核对结果。</think>\n\n正式回答：42。"
        for (split in 0..input.length) {
            val reasoning = StringBuilder()
            val answer = StringBuilder()
            val parser = ReasoningStreamParser({ answer.append(it) }, { reasoning.append(it) })
            parser.append(input.substring(0, split))
            parser.append(input.substring(split))
            parser.finish()
            assertEquals("split=$split", "先分析问题，再核对结果。", reasoning.toString())
            assertEquals("split=$split", "正式回答：42。", answer.toString())
        }
    }

    @Test
    fun emitsThinkingBeforeAnswerEvenWithSingleCharacterChunks() = runBlocking {
        val events = mutableListOf<Pair<String, String>>()
        val parser = ReasoningStreamParser(
            { events += "answer" to it },
            { events += "thinking" to it }
        )
        "<think>分析</think>\n结果".forEach { parser.append(it.toString()) }
        parser.finish()
        assertEquals(listOf("thinking", "thinking", "answer", "answer"), events.map { it.first })
        assertEquals("分析结果", events.joinToString("") { it.second })
    }

    @Test
    fun preservesNormalTextAndLessThanSigns() = runBlocking {
        val answer = StringBuilder()
        val reasoning = StringBuilder()
        val parser = ReasoningStreamParser({ answer.append(it) }, { reasoning.append(it) })
        val text = "2 < 3，<title>不是思考标签</title>\n第二行 <thi"
        text.forEach { parser.append(it.toString()) }
        parser.finish()
        assertEquals(text, answer.toString())
        assertEquals("", reasoning.toString())
    }

    @Test
    fun unfinishedThinkingNeverBecomesAnswer() = runBlocking {
        val answer = StringBuilder()
        val reasoning = StringBuilder()
        val parser = ReasoningStreamParser({ answer.append(it) }, { reasoning.append(it) })
        parser.append("<think>连接中途断开，只有思考内容")
        parser.finish()
        assertEquals("", answer.toString())
        assertEquals("连接中途断开，只有思考内容", reasoning.toString())
    }
}
