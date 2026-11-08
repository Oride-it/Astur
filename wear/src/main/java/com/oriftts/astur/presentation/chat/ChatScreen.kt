package com.oriftts.astur.presentation.chat

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.oriftts.astur.R
import com.oriftts.astur.data.ChatMessage
import com.oriftts.astur.presentation.theme.AsturTheme

/** 状态版入口：从 ViewModel 收集状态后交给无状态核心。 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.ui.collectAsState()
    val hapticsOn by viewModel.haptics.collectAsState(initial = true)
    val context = LocalContext.current

    // 收到 AI 新回复时轻微振动反馈（可在设置中关闭）
    var wasSending by remember { mutableStateOf(false) }
    LaunchedEffect(state.sending) {
        val isNewReply = wasSending && !state.sending &&
            state.messages.lastOrNull()?.let { it.role == "assistant" && it.content.isNotBlank() } == true
        if (isNewReply && hapticsOn) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as android.os.VibratorManager
                    manager.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                vibrator.vibrate(
                    VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            }
        }
        wasSending = state.sending
    }

    ChatScreen(
        state = state,
        onInputChange = viewModel::onInputChange,
        onExpandInput = viewModel::expandInput,
        onCollapseInput = viewModel::collapseInput,
        onSend = viewModel::send
    )
}

/**
 * 无状态核心 UI：可在预览器中直接渲染。
 * 底部交互两态：
 * - 收起：右下角一个圆形聊天按钮，随滚动隐藏/出现，不占屏
 * - 展开：完整输入栏贴底（圆表沿弧线留边），右侧圆形发送键；发送后自动收回
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    onInputChange: (String) -> Unit,
    onExpandInput: () -> Unit,
    onCollapseInput: () -> Unit,
    onSend: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()

    // 系统返回：展开状态下先收起输入栏
    BackHandler(enabled = state.inputExpanded) { onCollapseInput() }

    // 新消息出现时滚到最新一条
    LaunchedEffect(state.messages.size, state.error) {
        if (state.error != null) listState.scrollToItem(state.messages.size + 1)
        else if (state.messages.isNotEmpty()) listState.scrollToItem(state.messages.size)
    }

    // 流式输出跟随：思考过程/回答文本每增长一段就把列表推到底部（思考文字持续向下滚动）
    val lastContentLength = state.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(state.sending, lastContentLength, state.streamingReasoning.length) {
        if (state.sending && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.size)
            // 大幅正向滚动被内容末尾截断，等效滚到列表最底部
            listState.scrollBy(10_000f)
        }
    }

    // 圆形按钮显隐：derivedStateOf 收敛读取，滚动开始/结束才触发重组，滚动过程中零重组
    val showCircleButton by remember(state.inputExpanded) {
        derivedStateOf { !state.inputExpanded && !listState.isScrollInProgress }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 消息列表：底部留出固定交互区的空间
            TransformingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 84.dp),
                contentPadding = contentPadding
            ) {
                item(key = "header") {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                    ) {
                        Text("Astur AI")
                    }
                }

                // 消息气泡：消息以值参数传入独立组件——流式刷新时仅最后一条重组，其余直接跳过
                items(state.messages.size, key = { "msg_$it" }) { index ->
                    val message = state.messages[index]
                    MessageBubble(
                        message = message,
                        isUser = message.role == "user",
                        showThinking = state.sending && index == state.messages.lastIndex,
                        reasoning = if (state.sending && index == state.messages.lastIndex) {
                            state.streamingReasoning
                        } else ""
                    )
                }

                if (state.error != null) {
                    item(key = "error") {
                        Text(
                            text = "\u26A0 ${state.error ?: ""}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── 收起态：底部居中圆形聊天按钮，滚动时自动隐退，松手回归 ──
            AnimatedVisibility(
                visible = showCircleButton,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f)
            ) {
                CircleChatButton(onClick = onExpandInput)
            }

            // ── 展开态：完整输入栏从底部滑入，发送后自动收回 ──
            AnimatedVisibility(
                visible = state.inputExpanded,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                InputBar(
                    value = state.input,
                    enabled = !state.sending,
                    sendEnabled = state.input.isNotBlank() && !state.sending && !state.loadingHistory,
                    onValueChange = onInputChange,
                    onSend = onSend,
                    onCollapse = onCollapseInput
                )
            }
        }
    }
}

/**
 * 单条消息气泡：消息以值传入（ChatMessage 为不可变 data class，内容不变即跳过重组）。
 * 不做逐帧变换、无点击层，快速滚动与流式输出均零多余开销。
 * 流式期间若思考过程在输出（正文未到），气泡内滚动展示思考文字；正文到达后思考收起。
 */
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isUser: Boolean,
    showThinking: Boolean,
    reasoning: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large
            )
            .padding(12.dp)
    ) {
        Text(
            text = if (isUser) "我" else "AI",
            style = MaterialTheme.typography.labelMedium,
            color = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
        )
        if (isUser || message.content.isNotEmpty()) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (reasoning.isNotEmpty()) {
            // 思考过程流式展示，回答开始后由 render() 清空、此处随之消失
            Text(
                text = "思考中",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = reasoning,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (showThinking) {
            Text(
                text = "正在思考…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 收起态的圆形聊天按钮。 */
@Composable
private fun CircleChatButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(56.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chat),
            contentDescription = "打开输入框",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 展开态输入栏：文本框占满剩余宽度，右侧圆形发送键。
 */
@Composable
private fun InputBar(
    value: String,
    enabled: Boolean,
    sendEnabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 46.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.large
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
                enabled = enabled,
                textStyle = fieldStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            if (value.isEmpty()) {
                Text(
                    text = "输入消息…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Start
                )
            }
        }
        // 圆形发送键
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(
                    color = if (sendEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape
                )
                .clickable(enabled = sendEnabled) { onSend() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "发送",
                tint = if (sendEnabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun ChatScreenPreview() {
    AsturTheme {
        ChatScreen(
            state = ChatUiState(
                messages = listOf(
                    ChatMessage(role = "user", content = "你好，介绍一下你自己"),
                    ChatMessage(role = "assistant", content = "你好！我是 Astur，你的手表 AI 助手。")
                ),
                input = ""
            ),
            onInputChange = {},
            onExpandInput = {},
            onCollapseInput = {},
            onSend = {}
        )
    }
}
