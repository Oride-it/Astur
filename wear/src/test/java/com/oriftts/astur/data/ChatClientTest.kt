package com.oriftts.astur.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatClientTest {
    private fun delta(vararg fields: Pair<String, String>) =
        "data: {\"choices\":[{\"delta\":${buildJsonObject { fields.forEach { put(it.first, it.second) } }}}]}\n\n"

    @Test
    fun streamsReasoningSeparatelyAndStopsAtDone() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                delta("reasoning_content" to "先分析。") +
                    delta("reasoning_content" to "", "reasoning" to "再核对。") +
                    delta("content" to "<thi") + delta("content" to "nk>补充检查。</th") +
                    delta("content" to "ink>\n正式回答") +
                    "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":9,\"total_tokens\":14}}\n\n" +
                    "data: [DONE]\n\n" + delta("content" to "不应读取")
            ))
            val answer = StringBuilder()
            val reasoning = StringBuilder()
            var usage: TokenUsage? = null
            ChatClient.streamChat(
                config = ApiConfig(baseUrl = server.url("/v1").toString(), apiKey = "test-key", model = "test-model"),
                messages = listOf(ChatMessage("user", "测试")),
                onDelta = { answer.append(it) },
                onReasoning = { reasoning.append(it) },
                onUsage = { usage = it }
            )
            assertEquals("正式回答", answer.toString())
            assertEquals("先分析。再核对。补充检查。", reasoning.toString())
            assertEquals(TokenUsage(5, 9, 14), usage)
            val request = server.takeRequest()
            assertEquals("/v1/chat/completions", request.path)
        }
    }

    @Test
    fun retriesWithoutStreamOptionsForCompatibleServers() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("unsupported stream_options"))
            server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                .setBody(delta("content" to "正常回答") + "data: [DONE]\n\n"))
            val answer = StringBuilder()
            ChatClient.streamChat(
                config = ApiConfig(baseUrl = server.url("/v1").toString(), apiKey = "test-key", model = "test-model"),
                messages = listOf(ChatMessage("user", "测试")),
                onDelta = { answer.append(it) }
            )
            assertEquals("正常回答", answer.toString())
            server.takeRequest()
            val retry = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertFalse(retry.containsKey("stream_options"))
        }
    }

    @Test
    fun readsJsonAnswerAndReportsUsageOnce() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(
                """
                {
                  "choices": [{"message": {"reasoning_content": "核对", "content": "正式回答"}, "finish_reason": "stop"}],
                  "usage": {"prompt_tokens": 5, "completion_tokens": 9}
                }
                """.trimIndent()
            ))
            val answer = StringBuilder()
            val reasoning = StringBuilder()
            val usage = mutableListOf<TokenUsage>()
            ChatClient.streamChat(
                config = ApiConfig(baseUrl = server.url("/v1").toString(), apiKey = "test-key", model = "test-model"),
                messages = listOf(ChatMessage("user", "测试")),
                onDelta = { answer.append(it) },
                onReasoning = { reasoning.append(it) },
                onUsage = { usage.add(it) }
            )
            assertEquals("正式回答", answer.toString())
            assertEquals("核对", reasoning.toString())
            assertEquals(listOf(TokenUsage(5, 9, 14)), usage)
        }
    }

    @Test
    fun reportsLengthLimitEvenWhenOnlyReasoningWasReturned() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                delta("reasoning_content" to "思考中") +
                    "data: {\"choices\":[{\"finish_reason\":\"length\"}]}\n\n" +
                    "data: [DONE]\n\n"
            ))
            val answer = StringBuilder()
            val error = runCatching {
                ChatClient.streamChat(
                    config = ApiConfig(baseUrl = server.url("/v1").toString(), apiKey = "test-key", model = "test-model"),
                    messages = listOf(ChatMessage("user", "测试")),
                    onDelta = { answer.append(it) }
                )
            }.exceptionOrNull()
            assertEquals("", answer.toString())
            assertTrue(error is ApiException && error.message.orEmpty().contains("输出上限"))
        }
    }

    @Test
    fun surfacesProviderErrorAfterPartialAnswer() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(
                delta("content" to "部分回复") +
                    "data: {\"error\":{\"message\":\"接口额度不足\"}}\n\n"
            ))
            val answer = StringBuilder()
            val error = runCatching {
                ChatClient.streamChat(
                    config = ApiConfig(baseUrl = server.url("/v1").toString(), apiKey = "test-key", model = "test-model"),
                    messages = listOf(ChatMessage("user", "测试")),
                    onDelta = { answer.append(it) }
                )
            }.exceptionOrNull()
            assertEquals("部分回复", answer.toString())
            assertEquals("接口额度不足", error?.message)
        }
    }
}
