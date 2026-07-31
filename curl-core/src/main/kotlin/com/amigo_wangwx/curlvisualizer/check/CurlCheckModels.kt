package com.amigo_wangwx.curlvisualizer.check

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Options shared by single and batch URL checks.
 *
 * The timeout applies to each request, while workers limits concurrent requests in a batch.
 */
@Serializable
data class CurlCheckOptions(
    val timeoutSeconds: Long = 30,
    val workers: Int = 4,
) {
    init {
        require(timeoutSeconds > 0) { "timeoutSeconds must be greater than 0" }
        require(workers > 0) { "workers must be greater than 0" }
    }
}

/**
 * Stable outcome categories exposed by the library and CLI protocols.
 */
@Serializable
enum class CurlCheckStatus {
    @SerialName("ok")
    OK,

    @SerialName("redirect")
    REDIRECT,

    @SerialName("forbidden_or_login")
    FORBIDDEN_OR_LOGIN,

    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("server_error")
    SERVER_ERROR,

    @SerialName("timeout")
    TIMEOUT,

    @SerialName("network_error")
    NETWORK_ERROR,

    @SerialName("ssl_error")
    SSL_ERROR,

    @SerialName("invalid_url")
    INVALID_URL,
}

/**
 * Stable result for one URL check.
 *
 * Nullable fields are serialized explicitly so every JSON object has the same schema.
 */
@Serializable
data class CurlCheckResult(
    val url: String,
    val status: CurlCheckStatus,
    val statusCode: Int?,
    val finalUrl: String?,
    val elapsedMillis: Long,
    val error: String?,
)
