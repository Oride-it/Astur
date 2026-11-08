package com.oriftts.astur.presentation

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import com.oriftts.astur.AsturApp
import com.oriftts.astur.presentation.chat.ChatScreen
import com.oriftts.astur.presentation.chat.ChatViewModel
import com.oriftts.astur.presentation.components.AsturTimeText
import com.oriftts.astur.presentation.hub.HubScreen
import com.oriftts.astur.presentation.settings.SettingsScreen
import com.oriftts.astur.presentation.settings.SettingsViewModel
import com.oriftts.astur.presentation.stats.StatsScreen
import com.oriftts.astur.presentation.stats.StatsViewModel
import com.oriftts.astur.presentation.subscreens.AboutScreen
import com.oriftts.astur.presentation.subscreens.AppSettingsScreen
import com.oriftts.astur.presentation.subscreens.ChatLogScreen
import com.oriftts.astur.presentation.subscreens.PersonaScreen
import com.oriftts.astur.presentation.subscreens.PersonaViewModel
import com.oriftts.astur.presentation.theme.AsturTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 入口：主页（聊天）⇄ 功能中心，仅两页，靠左滑切换。
 * 功能中心六个入口以悬浮层方式打开子页面（带过渡动画），
 * 系统返回手势/按键退出子页面。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = LocalContext.current.applicationContext as AsturApp
            val saveQuota by app.settingsRepository.saveQuota.collectAsState(initial = false)
            val showTime by app.settingsRepository.showTime.collectAsState(initial = true)
            val keepScreenOn by app.settingsRepository.keepScreenOn.collectAsState(initial = false)
            val haptics by app.settingsRepository.haptics.collectAsState(initial = true)
            // OPPO Watch 模式：开启后提供应用内右滑返回，替代被手表禁用的系统手势
            val oppoMode by app.settingsRepository.oppoMode.collectAsState(initial = false)

            // 保持屏幕常亮：聊天过程中防止自动息屏
            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            AsturTheme {
                val chatVm: ChatViewModel = viewModel(
                    initializer = {
                        ChatViewModel(app.settingsRepository, app.statsRepository, app.chatHistoryRepository)
                    }
                )
                val settingsVm: SettingsViewModel = viewModel(
                    initializer = { SettingsViewModel(app.settingsRepository) }
                )
                val statsVm: StatsViewModel = viewModel(
                    initializer = { StatsViewModel(app.statsRepository) }
                )
                val personaVm: PersonaViewModel = viewModel(
                    initializer = { PersonaViewModel(app.statsRepository) }
                )

                // 子页面导航：null = 两页主界面
                var subScreen by remember { mutableStateOf<String?>(null) }
                BackHandler(enabled = subScreen != null) { subScreen = null }
                val scope = rememberCoroutineScope()

                // 右滑返回仅在"有路可退"时启用：子页面打开，或聊天输入栏展开
                val chatInputExpanded by chatVm.inputExpanded.collectAsState()
                val swipeBackActive = oppoMode && (subScreen != null || chatInputExpanded)

                // 记录最近一次打开的子页面，退出动画期间内容保持稳定不闪变
                var lastScreen by remember { mutableStateOf<String?>(null) }
                if (subScreen != null) lastScreen = subScreen

                // 推入/弹出进度：0 = 主界面，1 = 子页面，进入返回共用同一曲线
                val navProgress by animateFloatAsState(
                    targetValue = if (subScreen != null) 1f else 0f,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "navProgress"
                )
                // 悬浮层可见性：derivedStateOf 收敛布尔读取——动画逐帧推进时主树零重组，
                // 仅在打开/关闭两个瞬间各触发一次组合
                val overlayVisible by remember {
                    derivedStateOf { subScreen != null || navProgress > 0.001f }
                }

                // 关闭 AppScaffold 自带的滚动式 TimeText，改用固定悬浮的 AsturTimeText
                AppScaffold(timeText = {}) {
                    // 主界面层保持静态，仅子页层做单一位移动画（一次平滑过渡，无二次回弹）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .oppoSwipeBack(enabled = swipeBackActive) {
                                // 可见的优先退：子页面开着就退子页面，否则收起输入栏
                                if (subScreen != null) subScreen = null
                                else chatVm.collapseInput()
                            }
                    ) {
                        AsturPager(
                            chatVm = chatVm,
                            showPageIndicator = !overlayVisible,
                            onOpenModelConfig = { subScreen = "modelconfig" },
                            onOpenStats = { subScreen = "stats" },
                            onOpenChatLog = { subScreen = "chatlog" },
                            onOpenPersona = { subScreen = "persona" },
                            onOpenAppSettings = { subScreen = "appsettings" },
                            onOpenAbout = { subScreen = "about" }
                        )

                        // 子页面从右侧进入，返回时向右退出，与右滑返回手势方向一致。
                        if (overlayVisible) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = navProgress
                                        translationX = (1f - navProgress) * size.width
                                    }
                                    .background(MaterialTheme.colorScheme.background)
                            ) {
                                when (lastScreen) {
                                    "modelconfig" -> SettingsScreen(viewModel = settingsVm)
                                    "stats" -> StatsScreen(viewModel = statsVm)
                                    "chatlog" -> ChatLogScreen(
                                        messages = chatVm.history.collectAsState().value
                                    )
                                    "persona" -> PersonaScreen(viewModel = personaVm)
                                    "appsettings" -> AppSettingsScreen(
                                        saveQuota = saveQuota,
                                        showTime = showTime,
                                        keepScreenOn = keepScreenOn,
                                        haptics = haptics,
                                        oppoWatchMode = oppoMode,
                                        onSaveQuotaChange = { value ->
                                            scope.launch { app.settingsRepository.setSaveQuota(value) }
                                        },
                                        onShowTimeChange = { value ->
                                            scope.launch { app.settingsRepository.setShowTime(value) }
                                        },
                                        onKeepScreenOnChange = { value ->
                                            scope.launch { app.settingsRepository.setKeepScreenOn(value) }
                                        },
                                        onHapticsChange = { value ->
                                            scope.launch { app.settingsRepository.setHaptics(value) }
                                        },
                                        onOppoWatchModeChange = { value ->
                                            scope.launch { app.settingsRepository.setOppoMode(value) }
                                        }
                                    )
                                    "about" -> AboutScreen()
                                }
                            }
                        }

                        // 顶部时间栏：固定悬浮，可在设置中开关
                        if (showTime) {
                            AsturTimeText(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 两页主界面：第 0 页聊天主页，左滑进入第 1 页功能中心。 */
@Composable
fun AsturPager(
    chatVm: ChatViewModel,
    showPageIndicator: Boolean,
    onOpenModelConfig: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenChatLog: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    // beyondViewportPageCount = 1：两页常驻组合，滑动手势全程纯位移，
    // 不会在滑动中途才现场组合目标页（手表 SoC 上这是左右滑掉帧的头号来源）
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = showPageIndicator
        ) { page ->
            when (page) {
                0 -> ChatScreen(viewModel = chatVm)
                else -> HubScreen(
                    onOpenModelConfig = onOpenModelConfig,
                    onOpenStats = onOpenStats,
                    onOpenChatLog = onOpenChatLog,
                    onOpenPersona = onOpenPersona,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenAbout = onOpenAbout
                )
            }
        }
        if (showPageIndicator) {
            HorizontalPageIndicator(
                pagerState = pagerState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

/**
 * OPPO Watch 模式的右滑返回手势：从屏幕左缘起手、向右滑过阈值即触发返回。
 *
 * 观察者模式实现——在 Initial 阶段监听但平时不消费事件，列表滚动、按钮点击完全不受影响；
 * 仅当滑动距离达标时消费该事件并回调 onBack，其余手势（含系统返回键）行为不变。
 */
private fun Modifier.oppoSwipeBack(enabled: Boolean, onBack: () -> Unit): Modifier {
    if (!enabled) return this
    return pointerInput(enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startX = down.position.x
            // 仅左缘起手才认定为返回手势，避免误伤输入框内移动光标的拖动
            if (startX > size.width * 0.22f) return@awaitEachGesture
            val minDistance = size.width * 0.30f
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (change.id != down.id) break
                val dx = change.position.x - startX
                val dy = change.position.y - down.position.y
                // 水平向右位移达标且明显强于纵向位移才判定为返回
                if (dx >= minDistance && dx > abs(dy) * 1.5f) {
                    change.consume()
                    onBack()
                    break
                }
                if (change.changedToUp()) break
            }
        }
    }
}
