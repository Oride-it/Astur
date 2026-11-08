package com.oriftts.astur.presentation.hub

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * 功能中心页：六个功能模块入口，点击进入对应子页面查看内容。
 * 列表整体下移使入口视觉居中；按钮色对象复用同一实例，减少滚动期分配。
 */
@Composable
fun HubScreen(
    onOpenModelConfig: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenChatLog: () -> Unit,
    onOpenPersona: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    val entryColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 14.dp,
                start = contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
            ),
            state = listState
        ) {
            item(key = "header") {
                ListHeader(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                ) {
                    Text("功能中心")
                }
            }
            item(key = "modelconfig") {
                HubEntry(
                    label = "模型配置",
                    onClick = onOpenModelConfig,
                    colors = entryColors,
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                )
            }
            item(key = "stats") {
                HubEntry(
                    label = "Token 统计",
                    onClick = onOpenStats,
                    colors = entryColors,
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                )
            }
            item(key = "chatlog") {
                HubEntry(
                    label = "聊天记录",
                    onClick = onOpenChatLog,
                    colors = entryColors,
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                )
            }
            item(key = "persona") {
                HubEntry(
                    label = "人物设定",
                    onClick = onOpenPersona,
                    colors = entryColors,
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                )
            }
            item(key = "appsettings") {
                HubEntry(
                    label = "设置",
                    onClick = onOpenAppSettings,
                    colors = entryColors,
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                )
            }
            item(key = "about") {
                HubEntry(
                    label = "关于",
                    onClick = onOpenAbout,
                    colors = entryColors,
                    transformation = SurfaceTransformation(spec),
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
                )
            }
        }
    }
}

@Composable
private fun HubEntry(
    label: String,
    onClick: () -> Unit,
    colors: ButtonColors,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        transformation = transformation
    ) {
        Text(label)
    }
}
