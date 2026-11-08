package com.oriftts.astur.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.confirmationDialogCurvedText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.oriftts.astur.data.ModelProvider
import com.oriftts.astur.presentation.theme.AsturTheme

/** 状态版入口：从 ViewModel 收集状态后交给无状态核心。 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.ui.collectAsState()
    val curvedTextStyle = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(
        visible = state.showSaveConfirmation,
        onDismissRequest = viewModel::dismissSaveConfirmation,
        curvedText = { confirmationDialogCurvedText("已配置", curvedTextStyle) }
    )
    SettingsScreen(
        state = state,
        onSelectProvider = viewModel::selectProvider,
        onBaseUrlChange = viewModel::onBaseUrlChange,
        onApiKeyChange = viewModel::onApiKeyChange,
        onModelChange = viewModel::onModelChange,
        onSelectModel = viewModel::selectModel,
        onFetchModels = viewModel::fetchModels,
        onStartFullTest = viewModel::startFullTest,
        onSave = viewModel::save
    )
}

/** 无状态核心 UI：可在预览器中直接渲染。 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSelectProvider: (ModelProvider) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSelectModel: (String) -> Unit,
    onFetchModels: () -> Unit,
    onStartFullTest: () -> Unit,
    onSave: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val testRunning = state.testResults.values.any { it.status == TestStatus.RUNNING }

    // 选中/未选中两套色对象整体复用：列表滚动重组时不再逐项分配 ButtonColors
    // （buttonColors() 本身是 @Composable 工厂，只能在组合期调用，放页面顶部整体求值一次）
    val scheme = MaterialTheme.colorScheme
    val selectedColors = ButtonDefaults.buttonColors()
    val unselectedColors = ButtonDefaults.buttonColors(
        containerColor = scheme.surfaceContainer,
        contentColor = scheme.onSurfaceVariant
    )

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item(key = "header") {
                com.oriftts.astur.presentation.subscreens.SubScreenHeader(
                    title = "模型配置",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 1. 供应商 ──
            item(key = "provider-label") {
                SectionLabel("供应商", Modifier.fillMaxWidth().transformedHeight(this, spec))
            }
            items(ModelProvider.entries.size, key = { ModelProvider.entries[it].id }) { index ->
                val provider = ModelProvider.entries[index]
                val selected = provider == state.provider
                Button(
                    onClick = { onSelectProvider(provider) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    colors = if (selected) selectedColors else unselectedColors,
                    transformation = SurfaceTransformation(spec)
                ) {
                    Text(
                        text = provider.label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── 2. URL ──
            item(key = "url") {
                FieldRow(
                    label = "URL",
                    value = state.baseUrl,
                    onValueChange = onBaseUrlChange,
                    hint = "https://api.example.com/v1",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 3. API Key ──
            item(key = "apikey") {
                FieldRow(
                    label = "API Key",
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    hint = "sk-…",
                    password = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 4. 模型（可手动输入 / 自动读取）──
            item(key = "model") {
                FieldRow(
                    label = "模型",
                    value = state.model,
                    onValueChange = onModelChange,
                    hint = "手动输入，或点下方自动读取",
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "fetch") {
                Button(
                    onClick = onFetchModels,
                    enabled = state.baseUrl.isNotBlank() && state.apiKey.isNotBlank() && !state.loadingModels,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec)
                ) {
                    if (state.loadingModels) CircularProgressIndicator()
                    else Text(
                        text = "自动读取模型",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            items(state.models.size, key = { "m_$it" }) { index ->
                val model = state.models[index]
                val selected = model == state.model
                Button(
                    onClick = { onSelectModel(model) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (selected) selectedColors else unselectedColors
                ) {
                    Text(
                        text = model,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── 5. 保存 ──
            item(key = "save") {
                Button(
                    onClick = onSave,
                    enabled = !state.saved && !state.saving && state.baseUrl.isNotBlank() &&
                        state.apiKey.isNotBlank() && state.model.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    transformation = SurfaceTransformation(spec)
                ) {
                    Text(
                        text = if (state.saved) "已保存" else "保存",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            state.saveError?.let { error ->
                item(key = "save-error") {
                    Text(error, color = scheme.error, modifier = Modifier.fillMaxWidth())
                }
            }

            // ── 6. 测试连接：展开长面板，逐项检测 ──
            item(key = "test") {
                Button(
                    onClick = onStartFullTest,
                    enabled = !testRunning && state.baseUrl.isNotBlank() && state.apiKey.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, spec),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    transformation = SurfaceTransformation(spec)
                ) {
                    if (testRunning) CircularProgressIndicator()
                    else Text(
                        text = if (state.fullTestOpen) "重新测试" else "测试连接",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── 检测长面板：聊天 / Tool Call / 图片 / 音频 / 视频 ──
            if (state.fullTestOpen) {
                item(key = "testpanel") {
                    Column(Modifier.fillMaxWidth()) {
                        capabilityTests.forEach { (key, label) ->
                            val item = state.testResults[key] ?: TestItemUi()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.weight(1f)
                                )
                                when (item.status) {
                                    TestStatus.IDLE -> Text(
                                        text = "待测试",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    TestStatus.RUNNING -> CircularProgressIndicator()
                                    TestStatus.SUCCESS -> Text(
                                        text = "成功",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    TestStatus.FAILED -> Text(
                                        text = if ((item.code ?: -1) == -1) "失败 [网络]"
                                        else "失败 [${item.code}]",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    password: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            // TextStyle 记忆化：每次键入重组时不再重复分配
            val onSurface = MaterialTheme.colorScheme.onSurface
            val fieldStyle = remember(onSurface) {
                TextStyle(color = onSurface, fontSize = 13.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = fieldStyle,
                visualTransformation = if (password) PasswordVisualTransformation()
                else VisualTransformation.None,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun SettingsScreenPreview() {
    AsturTheme {
        SettingsScreen(
            state = SettingsUiState(
                provider = ModelProvider.DEEPSEEK,
                baseUrl = "https://api.deepseek.com/v1",
                apiKey = "sk-demo",
                model = "deepseek-chat",
                testResult = "读取成功，共 12 个模型"
            ),
            onSelectProvider = {},
            onBaseUrlChange = {},
            onApiKeyChange = {},
            onModelChange = {},
            onSelectModel = {},
            onFetchModels = {},
            onStartFullTest = {},
            onSave = {}
        )
    }
}
