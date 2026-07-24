package com.wang.curlvisualizer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import kotlin.system.measureTimeMillis

/**
 * Executes curl as a single local process and returns captured output.
 *
 * Lifecycle: created by the UI and called from a coroutine whenever the user presses the run button.
 */
class CurlRunner {
    /**
     * Runs the given command text and captures stdout, stderr, exit code, and elapsed time.
     *
     * ProcessBuilder is used without a shell so quoted curl arguments are passed directly after validation.
     */
    suspend fun run(commandText: String): CurlRunResult = withContext(Dispatchers.IO) {
        val args = CurlCommandParser.parse(commandText)
        var process: Process? = null
        var stdout = ""
        var stderr = ""
        var exitCode = -1

        val elapsedMillis = measureTimeMillis {
            process = ProcessBuilder(args).start()
            val runningProcess = process ?: error("curl 进程启动失败")

            coroutineScope {
                val stdoutTask = async {
                    runningProcess.inputStream.readBytes().toString(Charset.defaultCharset())
                }
                val stderrTask = async {
                    runningProcess.errorStream.readBytes().toString(Charset.defaultCharset())
                }

                exitCode = runningProcess.waitFor()
                stdout = stdoutTask.await()
                stderr = stderrTask.await()
            }
        }

        CurlRunResult(
            args = args,
            response = CurlResponseParser.parse(stdout),
            stderr = stderr.trim(),
            exitCode = exitCode,
            elapsedMillis = elapsedMillis,
        )
    }
}

/**
 * Immutable result for one curl execution.
 *
 * It keeps the parsed response and execution metadata separate so UI switches do not rerun curl.
 */
data class CurlRunResult(
    val args: List<String>,
    val response: CurlResponse,
    val stderr: String,
    val exitCode: Int,
    val elapsedMillis: Long,
)
