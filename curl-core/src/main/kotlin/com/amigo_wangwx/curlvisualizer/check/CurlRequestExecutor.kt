package com.amigo_wangwx.curlvisualizer.check

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import javax.net.ssl.SSLException
import kotlin.math.max

/**
 * Request facts consumed by core policies before a public check result is returned.
 *
 * The model contains transport observations only and deliberately carries no classified check status.
 */
data class CurlRequestFacts(
    val url: String,
    val statusCode: Int?,
    val finalUrl: String?,
    val redirected: Boolean,
    val elapsedMillis: Long,
    val failure: CurlRequestFailure?,
    val error: String?,
)

/** Transport-level failures that can be observed without applying caller-specific rules. */
enum class CurlRequestFailure {
    INVALID_URL,
    TIMEOUT,
    NETWORK_ERROR,
    SSL_ERROR,
}

/**
 * Internal request capability used by the public checker facade.
 *
 * Keeping policy application outside this contract prevents transport code from owning detection rules.
 */
internal interface CurlRequestExecutor {
    suspend fun execute(
        url: String,
        options: CurlCheckOptions,
    ): CurlRequestFacts

    fun executeBatch(
        urls: Iterable<String>,
        options: CurlCheckOptions,
    ): Flow<CurlRequestFacts>
}

/**
 * JDK HttpClient transport that follows redirects and performs no response-body buffering.
 */
internal class JdkCurlRequestExecutor(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) : CurlRequestExecutor {
    override suspend fun execute(
        url: String,
        options: CurlCheckOptions,
    ): CurlRequestFacts {
        val startedAt = System.nanoTime()
        val uri = parseHttpUri(url)
            ?: return facts(
                url = url,
                startedAt = startedAt,
                failure = CurlRequestFailure.INVALID_URL,
                error = "URL must be an absolute HTTP or HTTPS URL",
            )

        return try {
            val request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(options.timeoutSeconds))
                .GET()
                .build()
            val response = withContext(Dispatchers.IO) {
                client.send(request, HttpResponse.BodyHandlers.discarding())
            }
            facts(
                url = url,
                startedAt = startedAt,
                statusCode = response.statusCode(),
                finalUrl = response.uri().toString(),
                redirected = uri != response.uri(),
            )
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: Exception) {
            facts(
                url = url,
                startedAt = startedAt,
                failure = error.toRequestFailure(),
                error = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    override fun executeBatch(
        urls: Iterable<String>,
        options: CurlCheckOptions,
    ): Flow<CurlRequestFacts> = channelFlow {
        val work = Channel<String>(options.workers)
        repeat(options.workers) {
            launch {
                for (url in work) {
                    send(execute(url, options))
                }
            }
        }
        for (url in urls) {
            work.send(url)
        }
        work.close()
    }

    private fun parseHttpUri(url: String): URI? {
        return runCatching { URI.create(url) }
            .getOrNull()
            ?.takeIf {
                (it.scheme.equals("http", ignoreCase = true) ||
                    it.scheme.equals("https", ignoreCase = true)) &&
                    !it.host.isNullOrBlank()
            }
    }

    private fun Exception.toRequestFailure(): CurlRequestFailure {
        return when {
            hasCause<HttpTimeoutException>() -> CurlRequestFailure.TIMEOUT
            hasCause<SSLException>() -> CurlRequestFailure.SSL_ERROR
            hasCause<IOException>() -> CurlRequestFailure.NETWORK_ERROR
            else -> CurlRequestFailure.NETWORK_ERROR
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is T) return true
            current = current.cause
        }
        return false
    }

    private fun facts(
        url: String,
        startedAt: Long,
        statusCode: Int? = null,
        finalUrl: String? = null,
        redirected: Boolean = false,
        failure: CurlRequestFailure? = null,
        error: String? = null,
    ): CurlRequestFacts {
        return CurlRequestFacts(
            url = url,
            statusCode = statusCode,
            finalUrl = finalUrl,
            redirected = redirected,
            elapsedMillis = max(0, (System.nanoTime() - startedAt) / NANOS_PER_MILLISECOND),
            failure = failure,
            error = error,
        )
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000
    }
}
