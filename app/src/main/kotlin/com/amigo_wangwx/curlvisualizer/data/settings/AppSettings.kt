package com.amigo_wangwx.curlvisualizer.data.settings

import kotlinx.serialization.Serializable

/**
 * User-level app settings persisted between launches.
 *
 * Lifecycle: loaded before creating the main window and saved when the window closes.
 */
@Serializable
data class AppSettings(
    val windowWidthDp: Int = 1200,
    val windowHeightDp: Int = 760,
)
