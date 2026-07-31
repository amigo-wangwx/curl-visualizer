package com.amigo_wangwx.curlvisualizer.curl

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultCurlCommandResultRuleTest {
    @Test
    fun `GUI rule preserves execution details and parses the response`() {
        val execution = CurlCommandExecution(
            args = listOf("curl", "--include", "https://example.com"),
            stdout = """
                HTTP/1.1 200 OK
                Content-Type: application/json

                {"value":1}
            """.trimIndent(),
            stderr = "  diagnostic output  ",
            exitCode = 7,
            elapsedMillis = 123,
        )

        val result = DefaultCurlCommandResultRule.process(execution)

        assertEquals(execution.args, result.args)
        assertEquals("HTTP/1.1 200 OK", result.response.statusLine)
        assertEquals(listOf(HeaderLine("Content-Type", "application/json")), result.response.headers)
        assertEquals("""{"value":1}""", result.response.body)
        assertEquals("diagnostic output", result.stderr)
        assertEquals(7, result.exitCode)
        assertEquals(123, result.elapsedMillis)
    }
}
