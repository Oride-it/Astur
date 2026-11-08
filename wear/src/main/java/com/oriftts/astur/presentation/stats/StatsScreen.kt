package com.oriftts.astur.presentation.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.oriftts.astur.data.TokenRecord
import com.oriftts.astur.presentation.components.TokenStatCard
import com.oriftts.astur.presentation.subscreens.SubScreenHeader
import com.oriftts.astur.presentation.theme.AsturTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 状态版入口：从 ViewModel 收集状态后交给无状态核心。 */
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.ui.collectAsState()
    StatsScreen(state = state)
}

/** 无状态核心 UI：Token 消耗统计 + 消耗记录。 */
@Composable
fun StatsScreen(state: StatsUiState) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val dayFormat = remember { SimpleDateFormat("yyyyMMdd", Locale.getDefault()) }
    val recordFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    // 汇总与时间格式化一次算好（原来滚动时每个可见项每次组合都要跑一遍 SimpleDateFormat）
    val todayTotal = remember(state.history) {
        val today = dayFormat.format(Date())
        state.history
            .filter { dayFormat.format(Date(it.timestamp)) == today }
            .sumOf { it.totalTokens.toLong() }
    }
    val allTotal = remember(state.history) {
        state.history.sumOf { it.totalTokens.toLong() }
    }
    // 最新记录排在前面；时间文本随记录一同预格式化，滚动期零格式化开销
    val displayHistory = remember(state.history) {
        state.history.asReversed().map { it to recordFormat.format(Date(it.timestamp)) }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
            item(key = "header") {
                SubScreenHeader(
                    title = "Token 统计",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 用量总览 ──
            item(key = "today") {
                TokenStatCard(
                    label = "今日消耗",
                    value = "$todayTotal tokens",
                    modifier = Modifier.fillMaxWidth().transformItem(this, spec)
                )
            }
            item(key = "total") {
                TokenStatCard(
                    label = "累计消耗",
                    value = "$allTotal tokens",
                    modifier = Modifier.fillMaxWidth().transformItem(this, spec)
                )
            }

            // ── 消耗记录 ──
            item(key = "history-label") {
                Text(
                    text = "消耗记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().transformItem(this, spec)
                )
            }
            if (state.history.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "暂无记录，回聊天页发一条消息就有了",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformItem(this, spec)
                    )
                }
            } else {
                items(displayHistory.size, key = { "h_${displayHistory[it].first.timestamp}_$it" }) { index ->
                    val (record, timeText) = displayHistory[index]
                    HistoryCard(
                        record = record,
                        timeText = timeText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformItem(this, spec)
                    )
                }
            }
        }
    }
}

private fun Modifier.transformItem(
    scope: TransformingLazyColumnItemScope,
    spec: TransformationSpec
): Modifier {
    val transformation = scope.SurfaceTransformation(spec)
    return transformedHeight(scope, spec).graphicsLayer {
        with(transformation) { applyContainerTransformation() }
    }
}

/**
 * 单条消耗记录：非交互直绘（无点击层/涟漪），长列表快速滚动省帧。
 */
@Composable
private fun HistoryCard(
    record: TokenRecord,
    timeText: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large
        )
    ) {
        Text(
            text = "${record.model} · $timeText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
        )
        Text(
            text = "+${record.totalTokens} tokens（输入 ${record.promptTokens} · 输出 ${record.completionTokens}）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun StatsScreenPreview() {
    AsturTheme {
        StatsScreen(
            state = StatsUiState(
                history = listOf(
                    TokenRecord(
                        timestamp = System.currentTimeMillis(),
                        model = "deepseek-chat",
                        promptTokens = 120,
                        completionTokens = 356,
                        totalTokens = 476
                    )
                )
            )
        )
    }
}
