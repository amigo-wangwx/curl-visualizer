package com.amigo_wangwx.curlvisualizer.cli

import com.amigo_wangwx.curlvisualizer.check.CurlCheckOptions
import com.amigo_wangwx.curlvisualizer.check.CurlCheckResult
import com.amigo_wangwx.curlvisualizer.check.CurlCheckStatus
import com.amigo_wangwx.curlvisualizer.check.CurlUrlChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainTest {
    private val checker = object : CurlUrlChecker {
        override suspend fun checkUrl(
            url: String,
            options: CurlCheckOptions,
        ): CurlCheckResult {
            return CurlCheckResult(
                url = url,
                status = CurlCheckStatus.OK,
                statusCode = 200,
                finalUrl = url,
                elapsedMillis = 12,
                error = null,
            )
        }

        override fun checkBatch(
            urls: Iterable<String>,
            options: CurlCheckOptions,
        ): Flow<CurlCheckResult> = flow {
            urls.forEach { emit(checkUrl(it, options)) }
        }
    }

    @Test
    fun `check-url writes the stable JSON schema`() {
        val stdout = StringWriter()

        val exitCode = runCli(
            args = arrayOf("check-url", "https://example.com", "--timeout", "5"),
            checker = checker,
            stdout = PrintWriter(stdout, true),
        )

        assertEquals(0, exitCode)
        assertEquals(
            setOf("url", "status", "statusCode", "finalUrl", "elapsedMillis", "error"),
            Regex("\"([^\"]+)\"\\s*:").findAll(stdout.toString())
                .map { it.groupValues[1] }
                .toSet(),
        )
        assertTrue("\"status\":\"ok\"" in stdout.toString())
        assertTrue("\"error\":null" in stdout.toString())
    }

    @Test
    fun `check-batch writes one JSON line per nonblank URL`() {
        val directory = Files.createTempDirectory("curl-visualizer-cli-test")
        val input = directory.resolve("urls.txt")
        val output = directory.resolve("results.jsonl")
        Files.writeString(input, "https://one.example\n\nhttps://two.example\n")

        val exitCode = runCli(
            args = arrayOf(
                "check-batch",
                input.toString(),
                output.toString(),
                "--timeout",
                "5",
                "--workers",
                "2",
            ),
            checker = checker,
        )

        assertEquals(0, exitCode)
        assertEquals(2, output.readLines().size)
    }

    @Test
    fun `argument and file errors use stable exit codes`() {
        assertEquals(1, runCli(arrayOf("check-url"), checker = checker))
        assertEquals(
            2,
            runCli(
                arrayOf("check-batch", "missing-input-file", "output.jsonl"),
                checker = checker,
            ),
        )
    }
}
