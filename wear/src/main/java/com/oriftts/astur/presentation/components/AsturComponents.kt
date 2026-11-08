package com.oriftts.astur.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 顶部时间栏：固定悬浮在页面顶部（内容滚动时保持不动），每 30 秒自动刷新。
 * 纯黑主题下无底衬框，仅显示时间文字。
 */
@Composable
fun AsturTimeText(modifier: Modifier = Modifier) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var now by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = formatter.format(Date())
        }
    }
    Box(modifier = modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(
            text = now,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Token 用量卡片：主色容器，标签 + 数值，供统计页共用。
 * 纯展示组件：非交互直绘，无点击层与涟漪开销。
 */
@Composable
fun TokenStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.large
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
        )
    }
}
