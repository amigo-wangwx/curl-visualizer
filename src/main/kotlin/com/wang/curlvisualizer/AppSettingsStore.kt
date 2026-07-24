package com.wang.curlvisualizer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path

/**
 * Stores small app preferences under the current user's home directory.
 *
 * Lifecycle: used by the application entry point before and after the main window is displayed.
 */
class AppSettingsStore(
    private val settingsFile: Path = Path.of(
        System.getProperty("user.home"),
        ".curl-visualizer",
        "settings.json",
    ),
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Loads settings, falling back to defaults if no settings file exists or it is invalid.
     *
     * Invalid settings should not prevent the app from opening.
     */
    fun load(): AppSettings {
        if (!Files.exists(settingsFile)) return AppSettings()
        return runCatching {
            json.decodeFromString<AppSettings>(Files.readString(settingsFile))
        }.getOrDefault(AppSettings())
    }

    /**
     * Saves the latest main window size with minimum bounds.
     *
     * Bounds prevent a tiny resized window from becoming unusable on the next launch.
     */
    fun saveWindowSize(widthDp: Int, heightDp: Int) {
        save(
            AppSettings(
                windowWidthDp = widthDp.coerceAtLeast(MIN_WINDOW_WIDTH_DP),
                windowHeightDp = heightDp.coerceAtLeast(MIN_WINDOW_HEIGHT_DP),
            ),
        )
    }

    private fun save(settings: AppSettings) {
        Files.createDirectories(settingsFile.parent)
        Files.writeString(settingsFile, json.encodeToString(settings))
    }

    private companion object {
        const val MIN_WINDOW_WIDTH_DP = 900
        const val MIN_WINDOW_HEIGHT_DP = 620
    }
}
