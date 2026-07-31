package com.amigo_wangwx.curlvisualizer.check

import java.net.URI

/** Converts request facts into the final status returned to every checker caller. */
fun interface CurlCheckPolicy {
    fun classify(facts: CurlRequestFacts): CurlCheckStatus
}

/**
 * One optional classification rule.
 *
 * Returning null delegates to the next rule, allowing callers to add only their differing behavior.
 */
fun interface CurlCheckRule {
    fun evaluate(facts: CurlRequestFacts): CurlCheckStatus?
}

/**
 * Applies rules in order and uses [fallbackStatus] only when none match.
 */
class OrderedCurlCheckPolicy(
    private val rules: List<CurlCheckRule>,
    private val fallbackStatus: CurlCheckStatus = CurlCheckStatus.OK,
) : CurlCheckPolicy {
    override fun classify(facts: CurlRequestFacts): CurlCheckStatus {
        return rules.firstNotNullOfOrNull { it.evaluate(facts) } ?: fallbackStatus
    }
}

/**
 * Reusable default policy configuration shared by CLI and external library consumers.
 */
object CurlCheckPolicies {
    val default: CurlCheckPolicy by lazy {
        OrderedCurlCheckPolicy(defaultRules)
    }

    /**
     * Places caller-specific rules before the defaults without requiring callers to copy common logic.
     */
    fun defaultWith(vararg additionalRules: CurlCheckRule): CurlCheckPolicy {
        return OrderedCurlCheckPolicy(additionalRules.toList() + defaultRules)
    }

    private val defaultRules = listOf(
        CurlCheckRule { facts ->
            when (facts.failure) {
                CurlRequestFailure.INVALID_URL -> CurlCheckStatus.INVALID_URL
                CurlRequestFailure.TIMEOUT -> CurlCheckStatus.TIMEOUT
                CurlRequestFailure.NETWORK_ERROR -> CurlCheckStatus.NETWORK_ERROR
                CurlRequestFailure.SSL_ERROR -> CurlCheckStatus.SSL_ERROR
                null -> null
            }
        },
        CurlCheckRule { facts ->
            CurlCheckStatus.FORBIDDEN_OR_LOGIN.takeIf {
                facts.statusCode == 401 || facts.statusCode == 403
            }
        },
        CurlCheckRule { facts ->
            CurlCheckStatus.FORBIDDEN_OR_LOGIN.takeIf {
                facts.redirected && facts.finalUrl.looksLikeLoginUrl()
            }
        },
        CurlCheckRule { facts ->
            CurlCheckStatus.NOT_FOUND.takeIf { facts.statusCode == 404 }
        },
        CurlCheckRule { facts ->
            CurlCheckStatus.SERVER_ERROR.takeIf {
                facts.statusCode?.let { it in 500..599 } == true
            }
        },
        CurlCheckRule { facts ->
            CurlCheckStatus.REDIRECT.takeIf {
                facts.redirected ||
                    facts.statusCode?.let { it in 300..399 } == true
            }
        },
    )

    private fun String?.looksLikeLoginUrl(): Boolean {
        val url = this ?: return false
        val normalizedPath = runCatching { URI.create(url).path.orEmpty().lowercase() }
            .getOrDefault("")
        return LOGIN_PATH_MARKERS.any { marker ->
            normalizedPath == marker || normalizedPath.startsWith("$marker/")
        }
    }

    private val LOGIN_PATH_MARKERS = setOf("/login", "/signin", "/sign-in")
}
