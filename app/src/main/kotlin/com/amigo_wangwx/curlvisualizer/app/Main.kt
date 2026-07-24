package com.amigo_wangwx.curlvisualizer.app

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.amigo_wangwx.curlvisualizer.data.settings.AppSettingsStore
import com.amigo_wangwx.curlvisualizer.ui.CurlVisualizerApp

/**
 * Application entry point for the desktop curl visualizer.
 *
 * Lifecycle: Compose Desktop creates one main window and delegates UI state to the root app composable.
 */
fun main() = application {
    val settingsStore = remember { AppSettingsStore() }
    val settings = remember { settingsStore.load() }
    val windowState = rememberWindowState(
        width = settings.windowWidthDp.dp,
        height = settings.windowHeightDp.dp,
    )

    Window(
        state = windowState,
        onCloseRequest = {
            // 只记录尺寸不记录坐标，避免外接屏变化后窗口恢复到不可见区域。
            settingsStore.saveWindowSize(
                widthDp = windowState.size.width.value.toInt(),
                heightDp = windowState.size.height.value.toInt(),
            )
            exitApplication()
        },
        title = "Curl Visualizer",
    ) {
        CurlVisualizerApp()
    }
}
