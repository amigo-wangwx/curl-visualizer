package com.amigo_wangwx.curlvisualizer.check

import kotlin.test.Test
import kotlin.test.assertEquals

class CurlCheckPolicyTest {
    @Test
    fun `default policy maps every transport failure`() {
        val expectedStatuses = mapOf(
            CurlRequestFailure.INVALID_URL to CurlCheckStatus.INVALID_URL,
            CurlRequestFailure.TIMEOUT to CurlCheckStatus.TIMEOUT,
            CurlRequestFailure.NETWORK_ERROR to CurlCheckStatus.NETWORK_ERROR,
            CurlRequestFailure.SSL_ERROR to CurlCheckStatus.SSL_ERROR,
        )

        expectedStatuses.forEach { (failure, expectedStatus) ->
            assertEquals(
                expectedStatus,
                CurlCheckPolicies.default.classify(facts(failure = failure)),
            )
        }
    }

    @Test
    fun `default policy maps HTTP facts`() {
        assertEquals(
            CurlCheckStatus.FORBIDDEN_OR_LOGIN,
            CurlCheckPolicies.default.classify(facts(statusCode = 401)),
        )
        assertEquals(
            CurlCheckStatus.NOT_FOUND,
            CurlCheckPolicies.default.classify(facts(statusCode = 404)),
        )
        assertEquals(
            CurlCheckStatus.SERVER_ERROR,
            CurlCheckPolicies.default.classify(facts(statusCode = 500)),
        )
        assertEquals(
            CurlCheckStatus.REDIRECT,
            CurlCheckPolicies.default.classify(facts(statusCode = 200, redirected = true)),
        )
        assertEquals(
            CurlCheckStatus.OK,
            CurlCheckPolicies.default.classify(facts(statusCode = 200)),
        )
    }

    private fun facts(
        statusCode: Int? = null,
        redirected: Boolean = false,
        failure: CurlRequestFailure? = null,
    ): CurlRequestFacts {
        return CurlRequestFacts(
            url = "https://example.com",
            statusCode = statusCode,
            finalUrl = "https://example.com",
            redirected = redirected,
            elapsedMillis = 1,
            failure = failure,
            error = null,
        )
    }
}
