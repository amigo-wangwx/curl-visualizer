package com.amigo_wangwx.curlvisualizer.curl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.nio.charset.Charset
import kotlin.system.measureTimeMillis

/**
 * Executes curl as a single local process and applies a result rule before returning.
 *
 * Lifecycle: the GUI calls this facade once; process execution and rule application both remain inside core.
 */
class CurlRunner(
    private val resultRule: CurlCommandResultRule = DefaultCurlCommandResultRule,
) {
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

        resultRule.process(
            CurlCommandExecution(
                args = args,
                stdout = stdout,
                stderr = stderr,
                exitCode = exitCode,
                elapsedMillis = elapsedMillis,
            ),
        )
    }
}

/**
 * Complete curl process facts passed to a configured core rule.
 *
 * The rule runs inside [CurlRunner], so GUI callers never perform a manual second processing step.
 */
data class CurlCommandExecution(
    val args: List<String>,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val elapsedMillis: Long,
)

/** Converts complete curl process facts into the final result consumed by a GUI caller. */
fun interface CurlCommandResultRule {
    fun process(execution: CurlCommandExecution): CurlRunResult
}

/**
 * Default GUI rule that preserves command metadata and parses the complete response for visualization.
 */
object DefaultCurlCommandResultRule : CurlCommandResultRule {
    override fun process(execution: CurlCommandExecution): CurlRunResult {
        return CurlRunResult(
            args = execution.args,
            response = CurlResponseParser.parse(execution.stdout),
            stderr = execution.stderr.trim(),
            exitCode = execution.exitCode,
            elapsedMillis = execution.elapsedMillis,
        )
    }
}

/**
 * Immutable rule-processed result for one curl execution.
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
