package com.amigo_wangwx.curlvisualizer.check

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Type-safe URL checking contract shared by GUI integrations and command-line clients.
 *
 * Every result has already passed through the configured policy; callers never need a second classification step.
 */
interface CurlUrlChecker {
    /** Checks one URL and returns its fully classified result. */
    suspend fun checkUrl(
        url: String,
        options: CurlCheckOptions = CurlCheckOptions(),
    ): CurlCheckResult

    /**
     * Checks URLs concurrently and emits fully classified results as requests complete.
     *
     * At most [CurlCheckOptions.workers] requests are active at once.
     */
    fun checkBatch(
        urls: Iterable<String>,
        options: CurlCheckOptions = CurlCheckOptions(),
    ): Flow<CurlCheckResult>
}

/**
 * Default facade that always combines the internal request capability with a policy.
 *
 * Callers may inject a different policy once, while the request-to-policy pipeline remains inside core.
 */
class DefaultCurlUrlChecker private constructor(
    private val policy: CurlCheckPolicy,
    private val requestExecutor: CurlRequestExecutor,
) : CurlUrlChecker {
    constructor(
        policy: CurlCheckPolicy = CurlCheckPolicies.default,
    ) : this(
        policy = policy,
        requestExecutor = JdkCurlRequestExecutor(),
    )

    override suspend fun checkUrl(
        url: String,
        options: CurlCheckOptions,
    ): CurlCheckResult {
        return requestExecutor.execute(url, options).toCheckResult()
    }

    override fun checkBatch(
        urls: Iterable<String>,
        options: CurlCheckOptions,
    ): Flow<CurlCheckResult> {
        return requestExecutor.executeBatch(urls, options)
            .map { it.toCheckResult() }
    }

    private fun CurlRequestFacts.toCheckResult(): CurlCheckResult {
        return CurlCheckResult(
            url = url,
            status = policy.classify(this),
            statusCode = statusCode,
            finalUrl = finalUrl,
            elapsedMillis = elapsedMillis,
            error = error,
        )
    }
}
