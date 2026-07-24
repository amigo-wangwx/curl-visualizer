package com.wang.curlvisualizer

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Persists request and response history under the current user's home directory.
 *
 * Lifecycle: the UI keeps one instance and calls mutation methods after curl runs or delete actions.
 */
class HistoryStore(
    private val historyFile: Path = Path.of(
        System.getProperty("user.home"),
        ".curl-visualizer",
        "history.json",
    ),
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Loads saved history, returning an empty state when no file exists or the file is invalid.
     *
     * Invalid history should not block the tool from opening because curl debugging is the primary workflow.
     */
    fun load(): HistoryState {
        if (!Files.exists(historyFile)) return HistoryState()
        return runCatching {
            json.decodeFromString<HistoryState>(Files.readString(historyFile))
        }.getOrDefault(HistoryState())
    }

    /**
     * Inserts or updates history after one curl execution.
     *
     * Request identity uses the command text, while response identity uses the exact response body.
     */
    fun record(command: String, result: CurlRunResult): HistoryState {
        val now = System.currentTimeMillis()
        val current = load()
        val normalizedCommand = command.trim()
        val responseId = result.response.body
            .takeIf { it.isNotBlank() && it.toByteArray().size <= MAX_RESPONSE_BYTES }
            ?.let(::sha256)

        val responses = if (responseId == null) {
            current.responses
        } else {
            val responseItem = ResponseHistoryItem(
                id = responseId,
                body = result.response.body,
                updatedAtMillis = now,
                statusLine = result.response.statusLine,
                headers = result.response.headers,
                exitCode = result.exitCode,
            )
            upsertResponse(current.responses, responseItem)
        }

        val requestItem = CurlHistoryItem(
            id = sha256(normalizedCommand),
            command = normalizedCommand,
            executedAtMillis = now,
            statusLine = result.response.statusLine,
            exitCode = result.exitCode,
            elapsedMillis = result.elapsedMillis,
            responseId = responseId,
        )
        val next = HistoryState(
            requests = upsertRequest(current.requests, requestItem),
            responses = responses.take(MAX_ITEMS),
        )
        save(next)
        return next
    }

    /**
     * Deletes one saved request by id.
     *
     * Response records are kept because other requests may still point to the same response body.
     */
    fun deleteRequest(id: String): HistoryState {
        val current = load()
        val next = current.copy(requests = current.requests.filterNot { it.id == id })
        save(next)
        return next
    }

    /**
     * Deletes one saved response by id and clears dangling request links.
     *
     * Requests are preserved so users can still rerun the original curl command.
     */
    fun deleteResponse(id: String): HistoryState {
        val current = load()
        val next = current.copy(
            requests = current.requests.map {
                if (it.responseId == id) it.copy(responseId = null) else it
            },
            responses = current.responses.filterNot { it.id == id },
        )
        save(next)
        return next
    }

    /**
     * Clears all request history while leaving response history untouched.
     *
     * The two lists are independent so users can clean one side without losing the other.
     */
    fun clearRequests(): HistoryState {
        val current = load()
        val next = current.copy(requests = emptyList())
        save(next)
        return next
    }

    /**
     * Clears all response history and removes response links from requests.
     *
     * This is the fastest way to purge saved response bodies that may contain sensitive data.
     */
    fun clearResponses(): HistoryState {
        val current = load()
        val next = current.copy(
            requests = current.requests.map { it.copy(responseId = null) },
            responses = emptyList(),
        )
        save(next)
        return next
    }

    /**
     * Builds a display-only curl result from a saved response record.
     *
     * Loading history should never execute curl, so execution fields are filled from saved metadata.
     */
    fun toRunResult(item: ResponseHistoryItem): CurlRunResult {
        return CurlRunResult(
            args = emptyList(),
            response = CurlResponse(
                statusLine = item.statusLine,
                headers = item.headers,
                body = item.body,
            ),
            stderr = "",
            exitCode = item.exitCode,
            elapsedMillis = 0,
        )
    }

    private fun save(state: HistoryState) {
        Files.createDirectories(historyFile.parent)
        Files.writeString(historyFile, json.encodeToString(state))
    }

    private fun upsertRequest(
        items: List<CurlHistoryItem>,
        item: CurlHistoryItem,
    ): List<CurlHistoryItem> {
        return (listOf(item) + items.filterNot { it.id == item.id })
            .sortedByDescending { it.executedAtMillis }
            .take(MAX_ITEMS)
    }

    private fun upsertResponse(
        items: List<ResponseHistoryItem>,
        item: ResponseHistoryItem,
    ): List<ResponseHistoryItem> {
        return (listOf(item) + items.filterNot { it.id == item.id })
            .sortedByDescending { it.updatedAtMillis }
            .take(MAX_ITEMS)
    }

    private fun sha256(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_ITEMS = 100
        const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
    }
}
