package com.oriftts.astur.presentation.subscreens

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.oriftts.astur.R
import com.oriftts.astur.data.ChatMessage
import com.oriftts.astur.data.StatsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 子页面标题：水平居中显示页面名（返回使用系统自带返回手势/按键）。
 */
@Composable
fun SubScreenHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
}

// ────────────────────────── 人物设定 ──────────────────────────

data class PersonaUiState(
    val name: String = "",
    val text: String = "",
    val saved: Boolean = false
)

/** 人物设定状态：名字 + 性格，持久化。 */
class PersonaViewModel(private val stats: StatsRepository) : ViewModel() {

    private val _ui = MutableStateFlow(PersonaUiState())
    val ui: StateFlow<PersonaUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            stats.personaName.collect { _ui.value = _ui.value.copy(name = it) }
        }
        viewModelScope.launch {
            stats.personaText.collect { _ui.value = _ui.value.copy(text = it) }
        }
    }

    fun onNameChange(value: String) {
        _ui.value = _ui.value.copy(name = value)
    }

    fun onTextChange(value: String) {
        _ui.value = _ui.value.copy(text = value)
    }

    fun save() {
        viewModelScope.launch {
            stats.savePersona(_ui.value.name, _ui.value.text)
            _ui.value = _ui.value.copy(saved = true)
            delay(1500)
            _ui.value = _ui.value.copy(saved = false)
        }
    }
}

/** 人物设定页：名字 + 性格输入框，底部保存按钮。 */
@Composable
fun PersonaScreen(viewModel: PersonaViewModel) {
    val state by viewModel.ui.collectAsState()
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item(key = "header") {
                SubScreenHeader(title = "人物设定")
            }
            item(key = "name-label") {
                Text(
                    text = "名字",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "name") {
                PersonaField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    hint = "例如：杰克船长",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "text-label") {
                Text(
                    text = "性格",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "text") {
                PersonaField(
                    value = state.text,
                    onValueChange = viewModel::onTextChange,
                    hint = "例如：说话简洁，带点海盗腔调",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "save") {
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (state.saved) "已保存 ✓" else "保存设定")
                }
            }
        }
    }
}

@Composable
private fun PersonaField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    singleLine: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            // 单行高度与「保存设定」按钮一致（52dp），视觉上同宽同高
            .heightIn(min = if (singleLine) 52.dp else 64.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        // 单行时文字垂直居中，与按钮内的文字对齐方式一致
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
    ) {
        // TextStyle 记忆化：键入重组时不再重复分配
        val onSurface = MaterialTheme.colorScheme.onSurface
        val fieldStyle = remember(onSurface) {
            TextStyle(
                color = onSurface,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = fieldStyle,
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

// ────────────────────────── 聊天记录 ──────────────────────────

/** 聊天记录页：只读展示当前会话的全部消息（最新在底部）。 */
@Composable
fun ChatLogScreen(messages: List<ChatMessage>) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item(key = "header") {
                SubScreenHeader(title = "聊天记录")
            }
            if (messages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "暂无聊天记录，去聊一条就有了",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(messages.size, key = { "log_$it" }) { index ->
                    val message = messages[index]
                    val isUser = message.role == "user"
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isUser) "我" else "AI",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isUser) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────── 应用设置 ──────────────────────────

/** 应用设置页：节省额度 / 显示时间 / 保持屏幕常亮 / 振动反馈 / OPPO Watch 模式。内容整体下移使视觉居中。 */
@Composable
fun AppSettingsScreen(
    saveQuota: Boolean,
    showTime: Boolean,
    keepScreenOn: Boolean,
    haptics: Boolean,
    oppoWatchMode: Boolean,
    onSaveQuotaChange: (Boolean) -> Unit,
    onShowTimeChange: (Boolean) -> Unit,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onOppoWatchModeChange: (Boolean) -> Unit
) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
            ),
            state = listState
        ) {
            item(key = "header") {
                SubScreenHeader(title = "设置")
            }
            item(key = "quota") {
                SwitchButton(
                    checked = saveQuota,
                    onCheckedChange = onSaveQuotaChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("节省额度") },
                    secondaryLabel = { Text("限制回复长度，节省 Token") }
                )
            }
            item(key = "showtime") {
                SwitchButton(
                    checked = showTime,
                    onCheckedChange = onShowTimeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("显示时间") },
                    secondaryLabel = { Text("在页面顶部显示悬浮时间") }
                )
            }
            item(key = "keepscreen") {
                SwitchButton(
                    checked = keepScreenOn,
                    onCheckedChange = onKeepScreenOnChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("保持屏幕常亮") },
                    secondaryLabel = { Text("聊天时防止自动息屏") }
                )
            }
            item(key = "haptics") {
                SwitchButton(
                    checked = haptics,
                    onCheckedChange = onHapticsChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("振动反馈") },
                    secondaryLabel = { Text("收到新回复时轻微振动") }
                )
            }
            item(key = "oppo") {
                SwitchButton(
                    checked = oppoWatchMode,
                    onCheckedChange = onOppoWatchModeChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("OPPO Watch 模式") },
                    secondaryLabel = { Text("从屏幕左缘右滑返回，替代被禁用的系统手势") }
                )
            }
        }
    }
}

/** 关于页：图标 + 应用名 + 版本 + 设备 + 简介，全部居中排列。 */
@Composable
fun AboutScreen() {
    val listState = rememberTransformingLazyColumnState()
    val context = LocalContext.current
    val versionName = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 20.dp,
                bottom = contentPadding.calculateBottomPadding(),
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
            ),
            state = listState
        ) {
            item(key = "icon") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // painterResource 不支持自适应图标 XML，直接叠加矢量背景层与前景层
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "应用图标",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            item(key = "name") {
                Text(
                    text = "Astur",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "version") {
                Text(
                    text = "版本 $versionName",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item(key = "device") {
                Text(
                    text = "${Build.MANUFACTURER} ${Build.MODEL}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
            item(key = "intro") {
                // 应用简介放在框内：左上角标题，下方为简介内容
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.large
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "应用简介",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Astur 是一款专为圆表与移动设备打造的 AI 聊天助手。" +
                            "内置 12 家主流模型供应商预设，支持 DeepSeek、Google Gemini、xAI Grok、" +
                            "Kimi、智谱、通义千问、字节豆包等一键切换；提供流式对话、人物设定、" +
                            "Token 用量统计与节省额度模式，帮你把每一次提问都花在刀刃上。" +
                            "界面采用纯黑底色与电光蓝主题，针对圆形屏幕深度优化，" +
                            "左滑即可呼出功能中心，聊天、配置一目了然。无需注册账号，" +
                            "填入你自己的 API Key 即可开始使用。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            item(key = "by") {
                Text(
                    text = "By Oride",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                )
            }
        }
    }
}
