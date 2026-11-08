package com.oriftts.astur.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** 对话消息（OpenAI 兼容格式）。 */
@Serializable
data class ChatMessage(val role: String, val content: String)

/** 一次对话的 Token 用量。 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/** 能力测试类别。 */
enum class TestKind { CHAT, TOOL_CALL, VISION, AUDIO, VIDEO }

/** 请求失败时抛出，code 为 HTTP 状态码（网络异常为 -1）。 */
class ApiException(val code: Int = -1, message: String) : Exception(message)

@Serializable
private data class StreamOptions(val include_usage: Boolean = true)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val stream_options: StreamOptions? = null,
    val max_tokens: Int? = null
)

@Serializable
private data class ChatResponse(
    val choices: List<ChatChoice> = emptyList(),
    val usage: UsageInfo? = null,
    val error: JsonElement? = null
)

@Serializable
private data class ChatChoice(
    val delta: ReplyText? = null,
    val message: ReplyText? = null,
    val finish_reason: String? = null
)

@Serializable
private data class ReplyText(
    val content: String? = null,
    // 思考过程：DeepSeek/智谱/通义等用 reasoning_content，OpenRouter 用 reasoning
    val reasoning_content: String? = null,
    val reasoning: String? = null
)

@Serializable
private data class UsageInfo(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

@Serializable
private data class ModelsResponse(val data: List<ModelItem> = emptyList())

@Serializable
private data class ModelItem(val id: String = "")

/**
 * OpenAI 兼容 API 客户端：模型列表拉取 + SSE 流式对话 + Token 用量采集 + 能力检测。
 */
object ChatClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private fun url(config: ApiConfig, path: String): String =
        config.baseUrl.trimEnd('/') + path

    /** 拉取模型列表（GET {baseUrl}/models）。 */
    suspend fun fetchModels(config: ApiConfig): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url(config, "/models"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, "[${resp.code}] ${resp.message.ifBlank { "请求失败" }} ${body.take(200)}")
            }
            json.decodeFromString<ModelsResponse>(body)
                .data
                .map { it.id }
                .filter { it.isNotBlank() }
        }
    }

    /**
     * 流式对话（POST {baseUrl}/chat/completions，SSE）。
     * @param onDelta 每收到一段增量正文回调一次
     * @param onReasoning 每收到一段思考过程（reasoning）增量回调一次
     * @param onUsage 流结束时回调本次对话的 Token 用量（需服务端支持 stream_options.include_usage）
     * @param maxTokens 限制回复最大 Token 数（节省额度用），null 不限制
     */
    suspend fun streamChat(
        config: ApiConfig,
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit = {},
        onUsage: suspend (TokenUsage) -> Unit = {},
        maxTokens: Int? = null
    ): Unit = withContext(Dispatchers.IO) {
        streamInternal(config, messages, onDelta, onReasoning, onUsage, includeUsage = true, maxTokens = maxTokens)
    }

    private suspend fun streamInternal(
        config: ApiConfig,
        messages: List<ChatMessage>,
        onDelta: suspend (String) -> Unit,
        onReasoning: suspend (String) -> Unit,
        onUsage: suspend (TokenUsage) -> Unit,
        includeUsage: Boolean,
        maxTokens: Int? = null
    ): Unit = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            ChatRequest(
                model = config.model,
                messages = messages,
                stream = true,
                stream_options = if (includeUsage) StreamOptions(include_usage = true) else null,
                max_tokens = maxTokens
            )
        )
        val request = Request.Builder()
            .url(url(config, "/chat/completions"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "text/event-stream, application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                // 少数兼容端不认识 stream_options，降级重试一次
                if (includeUsage && resp.code in listOf(400, 404, 415, 422)) {
                    streamInternal(config, messages, onDelta, onReasoning, onUsage, includeUsage = false, maxTokens = maxTokens)
                    return@withContext
                }
                val body = resp.body?.string().orEmpty()
                throw ApiException(resp.code, "[${resp.code}] ${resp.message.ifBlank { "请求失败" }} ${body.take(200)}")
            }
            val source = resp.body?.source() ?: throw ApiException(resp.code, "响应为空")
            var hasAnswer = false
            var finishReason: String? = null
            var usage: TokenUsage? = null
            val contentParser = ReasoningStreamParser(
                onContent = { delta ->
                    if (delta.isNotBlank()) hasAnswer = true
                    onDelta(delta)
                },
                onReasoning = onReasoning
            )
            suspend fun readResponse(data: String) {
                val response = try {
                    json.decodeFromString<ChatResponse>(data)
                } catch (_: IllegalArgumentException) {
                    throw ApiException(message = "模型返回了无法解析的回复")
                }
                val error = response.error
                if (error != null && error != JsonNull) {
                    val detail = (error as? JsonObject)?.get("message") ?: error
                    val message = (detail as? JsonPrimitive)?.contentOrNull
                    throw ApiException(message = message?.take(200) ?: "模型接口返回错误")
                }
                val choice = response.choices.firstOrNull()
                choice?.finish_reason?.takeIf { it.isNotBlank() }?.let { finishReason = it }
                (choice?.delta ?: choice?.message)?.let { reply ->
                    val reasoning = reply.reasoning_content?.takeIf { it.isNotEmpty() } ?: reply.reasoning
                    if (!reasoning.isNullOrEmpty()) onReasoning(reasoning)
                    if (!reply.content.isNullOrEmpty()) contentParser.append(reply.content)
                }
                response.usage?.let { counts ->
                    val prompt = counts.prompt_tokens ?: 0
                    val completion = counts.completion_tokens ?: 0
                    val total = counts.total_tokens ?: (prompt + completion)
                    if (total > 0) usage = TokenUsage(prompt, completion, total)
                }
            }

            try {
                var line = source.readUtf8Line()
                while (line != null && line.isBlank()) line = source.readUtf8Line()
                if (line?.trimStart()?.startsWith("{") == true) {
                    readResponse(line + "\n" + source.readUtf8())
                } else {
                    var done = false
                    while (line != null) {
                        ensureActive()
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data == "[DONE]") {
                                done = true
                                break
                            }
                            if (data.isNotEmpty()) readResponse(data)
                        }
                        line = source.readUtf8Line()
                    }
                    if (!done && finishReason == null && hasAnswer) {
                        throw ApiException(message = "回复连接提前结束")
                    }
                }
                contentParser.finish()
                when (finishReason) {
                    "length" -> throw ApiException(message = "回复达到模型输出上限，可关闭节省额度后重试")
                    "content_filter" -> throw ApiException(message = "模型因内容过滤中止了回复")
                    "tool_calls", "function_call" ->
                        throw ApiException(message = "模型要求调用工具，请尝试支持直接对话的模型")
                }
                if (!hasAnswer) throw ApiException(message = "模型未返回正式回答，请重试")
            } finally {
                usage?.let { onUsage(it) }
            }
        }
    }

    // ──────────────── 能力检测（测试连接长面板） ────────────────

    /** 发送 JSON 并返回 (HTTP 状态码, 响应体)。 */
    private fun postJson(config: ApiConfig, path: String, payload: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url(config, path))
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { resp ->
            return resp.code to resp.body?.string().orEmpty()
        }
    }

    private fun requireOk(code: Int, body: String) {
        if (code != 200) throw ApiException(code, "HTTP $code ${body.take(120)}")
    }

    /**
     * 运行单项能力检测，成功返回详情描述，失败抛出 ApiException（含 HTTP 状态码）。
     */
    suspend fun runCapabilityTest(config: ApiConfig, kind: TestKind): String =
        withContext(Dispatchers.IO) {
            when (kind) {
                TestKind.CHAT -> {
                    val payload = buildJsonObject {
                        put("model", config.model)
                        putJsonArray("messages") {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", "ping")
                            })
                        }
                        put("max_tokens", 8)
                        put("stream", false)
                    }
                    val (code, body) = postJson(config, "/chat/completions", payload.toString())
                    requireOk(code, body)
                    val hasChoices = runCatching {
                        json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.isNotEmpty() == true
                    }.getOrDefault(false)
                    if (hasChoices) "对话回复正常" else "响应异常"
                }

                TestKind.TOOL_CALL -> {
                    val payload = buildJsonObject {
                        put("model", config.model)
                        putJsonArray("messages") {
                            add(buildJsonObject {
                                put("role", "user")
                                put("content", "北京今天天气怎么样？请调用工具查询。")
                            })
                        }
                        putJsonArray("tools") {
                            add(buildJsonObject {
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", "get_weather")
                                    put("description", "查询指定城市的天气")
                                    put("parameters", buildJsonObject {
                                        put("type", "object")
                                        put("properties", buildJsonObject {
                                            put("city", buildJsonObject {
                                                put("type", "string")
                                                put("description", "城市名")
                                            })
                                        })
                                    })
                                })
                            })
                        }
                        put("tool_choice", "auto")
                        put("max_tokens", 100)
                        put("stream", false)
                    }
                    val (code, body) = postJson(config, "/chat/completions", payload.toString())
                    requireOk(code, body)
                    val calledTool = runCatching {
                        json.parseToJsonElement(body).jsonObject["choices"]
                            ?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("message")?.jsonObject?.get("tool_calls") != null
                    }.getOrDefault(false)
                    if (calledTool) "已触发工具调用" else "响应正常（未触发调用）"
                }

                TestKind.VISION -> {
                    // 1x1 红色像素 PNG，检测模型是否支持图片输入
                    val tinyPng = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
                    val payload = buildJsonObject {
                        put("model", config.model)
                        putJsonArray("messages") {
                            add(buildJsonObject {
                                put("role", "user")
                                putJsonArray("content") {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", "这张图片是什么颜色？回答一个词即可。")
                                    })
                                    add(buildJsonObject {
                                        put("type", "image_url")
                                        put("image_url", buildJsonObject { put("url", tinyPng) })
                                    })
                                }
                            })
                        }
                        put("max_tokens", 10)
                        put("stream", false)
                    }
                    val (code, body) = postJson(config, "/chat/completions", payload.toString())
                    requireOk(code, body)
                    "支持图片输入"
                }

                TestKind.AUDIO -> {
                    val payload = buildJsonObject {
                        put("model", "tts-1")
                        put("input", "你好")
                        put("voice", "alloy")
                    }
                    val (code, body) = postJson(config, "/audio/speech", payload.toString())
                    requireOk(code, body)
                    "语音合成可用"
                }

                TestKind.VIDEO -> {
                    val payload = buildJsonObject {
                        put("model", "sora-2")
                        put("prompt", "一只猫在雪地里奔跑")
                        put("seconds", "4")
                        put("size", "720x1280")
                    }
                    val (code, body) = postJson(config, "/videos", payload.toString())
                    requireOk(code, body)
                    "视频生成接口可用"
                }
            }
        }
}
