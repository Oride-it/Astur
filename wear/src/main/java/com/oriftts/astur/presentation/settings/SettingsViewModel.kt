package com.oriftts.astur.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oriftts.astur.data.ApiConfig
import com.oriftts.astur.data.ChatClient
import com.oriftts.astur.data.ModelProvider
import com.oriftts.astur.data.SettingsRepository
import com.oriftts.astur.data.TestKind
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 单项能力测试状态。 */
enum class TestStatus { IDLE, RUNNING, SUCCESS, FAILED }

data class TestItemUi(
    val status: TestStatus = TestStatus.IDLE,
    val code: Int? = null
)

data class SettingsUiState(
    val provider: ModelProvider = ModelProvider.DEEPSEEK,
    val baseUrl: String = ModelProvider.DEEPSEEK.defaultBaseUrl,
    val apiKey: String = "",
    val model: String = "",
    val models: List<String> = emptyList(),
    val loadingModels: Boolean = false,
    val testResult: String? = null,
    val saved: Boolean = false,
    val saving: Boolean = false,
    val showSaveConfirmation: Boolean = false,
    val saveError: String? = null,
    /** 测试连接长面板：是否展开 */
    val fullTestOpen: Boolean = false,
    /** 各能力测试项状态：key = chat / tool / vision / audio / video */
    val testResults: Map<String, TestItemUi> = emptyMap()
)

/** 测试面板的固定项（key, 显示名）。 */
val capabilityTests: List<Pair<String, String>> = listOf(
    "chat" to "聊天",
    "tool" to "Tool Call",
    "vision" to "图片",
    "audio" to "音频",
    "video" to "视频"
)

/** 模型配置页状态：保存走 DataStore 持久化。 */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.current()
            _ui.value = SettingsUiState(
                provider = ModelProvider.fromId(saved.providerId),
                baseUrl = saved.baseUrl,
                apiKey = saved.apiKey,
                model = saved.model,
                saved = saved.isComplete
            )
        }
    }

    fun selectProvider(provider: ModelProvider) {
        _ui.value = _ui.value.copy(
            provider = provider,
            baseUrl = if (provider == ModelProvider.CUSTOM) _ui.value.baseUrl
            else provider.defaultBaseUrl,
            saved = false
        )
    }

    fun onBaseUrlChange(value: String) {
        _ui.value = _ui.value.copy(baseUrl = value, saved = false)
    }

    fun onApiKeyChange(value: String) {
        _ui.value = _ui.value.copy(apiKey = value, saved = false)
    }

    fun onModelChange(value: String) {
        _ui.value = _ui.value.copy(model = value, saved = false)
    }

    fun selectModel(model: String) {
        onModelChange(model)
    }

    fun dismissSaveConfirmation() {
        _ui.value = _ui.value.copy(showSaveConfirmation = false)
    }

    /** 自动读取模型：GET {baseUrl}/models。 */
    fun fetchModels() {
        if (_ui.value.loadingModels) return
        _ui.value = _ui.value.copy(loadingModels = true, testResult = null)
        viewModelScope.launch {
            try {
                val models = ChatClient.fetchModels(toConfig())
                _ui.value = _ui.value.copy(
                    models = models,
                    loadingModels = false,
                    testResult = if (models.isEmpty()) "接口已连通，但未返回模型"
                    else "读取成功，共 ${models.size} 个模型"
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    models = emptyList(),
                    loadingModels = false,
                    testResult = "读取失败：${e.message}"
                )
            }
        }
    }

    /** 是否有测试正在进行（用于按钮防重入）。 */
    val testRunning: Boolean
        get() = _ui.value.testResults.values.any { it.status == TestStatus.RUNNING }

    /**
     * 测试连接：展开长面板，顺序执行 聊天 / Tool Call / 图片 / 音频 / 视频 五项检测，
     * 每项进行中显示加载动画，完成标注成功或失败（含 HTTP 状态码）。
     */
    fun startFullTest() {
        val current = _ui.value
        if (current.testResults.values.any { it.status == TestStatus.RUNNING }) return
        _ui.value = current.copy(fullTestOpen = true, testResults = emptyMap())
        viewModelScope.launch {
            val config = repository.current()
            for ((key, _) in capabilityTests) {
                _ui.value = _ui.value.copy(
                    testResults = _ui.value.testResults + (key to TestItemUi(TestStatus.RUNNING))
                )
                runCatching { ChatClient.runCapabilityTest(config, TestKind.valueOf(key.uppercase())) }
                    .fold(
                        onSuccess = {
                            _ui.value = _ui.value.copy(
                                testResults = _ui.value.testResults + (key to TestItemUi(TestStatus.SUCCESS))
                            )
                        },
                        onFailure = { e ->
                            _ui.value = _ui.value.copy(
                                testResults = _ui.value.testResults + (key to TestItemUi(
                                    status = TestStatus.FAILED,
                                    code = (e as? com.oriftts.astur.data.ApiException)?.code ?: -1
                                ))
                            )
                        }
                    )
            }
        }
    }

    fun save() {
        val config = toConfig()
        if (_ui.value.saved || _ui.value.saving || !config.isComplete) return
        _ui.value = _ui.value.copy(saving = true, saveError = null)
        viewModelScope.launch {
            try {
                repository.save(config)
                _ui.value = _ui.value.copy(
                    saved = toConfig() == config,
                    showSaveConfirmation = true
                )
            } catch (_: IOException) {
                _ui.value = _ui.value.copy(saveError = "保存失败，请重试")
            } finally {
                _ui.value = _ui.value.copy(saving = false)
            }
        }
    }

    private fun toConfig(): ApiConfig = _ui.value.let {
        ApiConfig(
            providerId = it.provider.id,
            baseUrl = it.baseUrl,
            apiKey = it.apiKey,
            model = it.model
        )
    }
}
