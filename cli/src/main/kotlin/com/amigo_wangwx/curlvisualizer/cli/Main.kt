package com.amigo_wangwx.curlvisualizer.cli

import com.amigo_wangwx.curlvisualizer.check.CurlCheckOptions
import com.amigo_wangwx.curlvisualizer.check.CurlCheckResult
import com.amigo_wangwx.curlvisualizer.check.CurlUrlChecker
import com.amigo_wangwx.curlvisualizer.check.DefaultCurlUrlChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.system.exitProcess

private const val EXIT_SUCCESS = 0
private const val EXIT_ARGUMENT_ERROR = 1
private const val EXIT_FILE_ERROR = 2
private const val EXIT_INTERRUPTED = 3

private val protocolJson = Json {
    encodeDefaults = true
    explicitNulls = true
}

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

/**
 * Runs one CLI invocation and returns its stable process exit code.
 */
internal fun runCli(
    args: Array<String>,
    checker: CurlUrlChecker = DefaultCurlUrlChecker(),
    stdout: PrintWriter = PrintWriter(System.out, true),
    stderr: PrintWriter = PrintWriter(System.err, true),
): Int {
    val command = try {
        parseCommand(args)
    } catch (error: IllegalArgumentException) {
        stderr.println(error.message)
        stderr.println(usage())
        return EXIT_ARGUMENT_ERROR
    }

    return try {
        when (command) {
            is CliCommand.CheckUrl -> {
                val result = runBlocking {
                    checker.checkUrl(
                        url = command.url,
                        options = CurlCheckOptions(timeoutSeconds = command.timeoutSeconds),
                    )
                }
                stdout.println(protocolJson.encodeToString(result))
                EXIT_SUCCESS
            }

            is CliCommand.CheckBatch -> runBatch(command, checker)
        }
    } catch (error: FileAccessException) {
        stderr.println(error.message)
        EXIT_FILE_ERROR
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        stderr.println("Execution interrupted")
        EXIT_INTERRUPTED
    } catch (error: CancellationException) {
        stderr.println("Execution interrupted")
        EXIT_INTERRUPTED
    } catch (error: Exception) {
        stderr.println(error.message ?: error.javaClass.simpleName)
        EXIT_INTERRUPTED
    }
}

private fun runBatch(
    command: CliCommand.CheckBatch,
    checker: CurlUrlChecker,
): Int {
    val urls = try {
        Files.readAllLines(command.input)
            .map(String::trim)
            .filter(String::isNotEmpty)
    } catch (error: IOException) {
        throw FileAccessException("Cannot read input file: ${command.input}", error)
    } catch (error: SecurityException) {
        throw FileAccessException("Cannot read input file: ${command.input}", error)
    }

    try {
        Files.newBufferedWriter(command.output).use { writer ->
            runBlocking {
                checker.checkBatch(
                    urls = urls,
                    options = CurlCheckOptions(
                        timeoutSeconds = command.timeoutSeconds,
                        workers = command.workers,
                    ),
                ).collect { result ->
                    writer.write(protocolJson.encodeToString<CurlCheckResult>(result))
                    writer.newLine()
                    writer.flush()
                }
            }
        }
    } catch (error: IOException) {
        throw FileAccessException("Cannot write output file: ${command.output}", error)
    } catch (error: SecurityException) {
        throw FileAccessException("Cannot write output file: ${command.output}", error)
    }
    return EXIT_SUCCESS
}

private fun parseCommand(args: Array<String>): CliCommand {
    require(args.isNotEmpty()) { "Missing command" }
    return when (args.first()) {
        "check-url" -> parseCheckUrl(args.drop(1))
        "check-batch" -> parseCheckBatch(args.drop(1))
        else -> throw IllegalArgumentException("Unknown command: ${args.first()}")
    }
}

private fun parseCheckUrl(args: List<String>): CliCommand.CheckUrl {
    require(args.isNotEmpty()) { "check-url requires <url>" }
    val url = args.first()
    val options = parseOptions(args.drop(1), allowed = setOf("--timeout"))
    return CliCommand.CheckUrl(
        url = url,
        timeoutSeconds = options.longValue("--timeout", default = 30),
    )
}

private fun parseCheckBatch(args: List<String>): CliCommand.CheckBatch {
    require(args.size >= 2) {
        "check-batch requires <input_urls.txt> <output_results.jsonl>"
    }
    val input = args[0].toPath("input file")
    val output = args[1].toPath("output file")
    val options = parseOptions(args.drop(2), allowed = setOf("--timeout", "--workers"))
    return CliCommand.CheckBatch(
        input = input,
        output = output,
        timeoutSeconds = options.longValue("--timeout", default = 30),
        workers = options.intValue("--workers", default = 4),
    )
}

private fun parseOptions(
    args: List<String>,
    allowed: Set<String>,
): Map<String, String> {
    require(args.size % 2 == 0) { "Every option requires a value" }
    val options = linkedMapOf<String, String>()
    args.chunked(2).forEach { (name, value) ->
        require(name in allowed) { "Unknown option: $name" }
        require(name !in options) { "Duplicate option: $name" }
        options[name] = value
    }
    return options
}

private fun Map<String, String>.longValue(name: String, default: Long): Long {
    val value = this[name] ?: return default
    return value.toLongOrNull()
        ?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("$name must be a positive integer")
}

private fun Map<String, String>.intValue(name: String, default: Int): Int {
    val value = this[name] ?: return default
    return value.toIntOrNull()
        ?.takeIf { it > 0 }
        ?: throw IllegalArgumentException("$name must be a positive integer")
}

private fun String.toPath(label: String): Path {
    return try {
        Path.of(this)
    } catch (error: InvalidPathException) {
        throw IllegalArgumentException("Invalid $label path: $this", error)
    }
}

private fun usage(): String {
    return """
        Usage:
          curl-visualizer check-url <url> [--timeout <seconds>]
          curl-visualizer check-batch <input_urls.txt> <output_results.jsonl> [--timeout <seconds>] [--workers <n>]
    """.trimIndent()
}

private sealed interface CliCommand {
    data class CheckUrl(
        val url: String,
        val timeoutSeconds: Long,
    ) : CliCommand

    data class CheckBatch(
        val input: Path,
        val output: Path,
        val timeoutSeconds: Long,
        val workers: Int,
    ) : CliCommand
}

private class FileAccessException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)
