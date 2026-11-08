package com.oriftts.astur.data

/**
 * 预置的模型供应商（全部为 OpenAI 兼容协议）。
 * baseUrl 为各厂商 OpenAI 兼容入口，拼接 /chat/completions 与 /models 使用。
 */
enum class ModelProvider(val id: String, val label: String, val defaultBaseUrl: String) {
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com/v1"),
    GEMINI("gemini", "Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
    OPENAI_COMPAT("openai_compat", "OpenAI 兼容", "https://api.openai.com/v1"),
    GROK("grok", "xAI Grok", "https://api.x.ai/v1"),
    MOONSHOT("moonshot", "Kimi 月之暗面", "https://api.moonshot.cn/v1"),
    ZHIPU("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
    QWEN("qwen", "通义 Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    DOUBAO("doubao", "字节豆包", "https://ark.cn-beijing.volces.com/api/v3"),
    SILICONFLOW("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1"),
    GROQ("groq", "Groq", "https://api.groq.com/openai/v1"),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
    CUSTOM("custom", "自定义", "");

    companion object {
        fun fromId(id: String): ModelProvider =
            entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}
