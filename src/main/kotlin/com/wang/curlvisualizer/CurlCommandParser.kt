package com.wang.curlvisualizer

/**
 * Parses a pasted curl command into process arguments.
 *
 * Lifecycle: called before each execution, and it only accepts a single curl process so the UI cannot run shell chains.
 */
object CurlCommandParser {
    private val blockedOperators = setOf(";", "&&", "||", "|", ">", ">>", "<", "`")
    private val blockedOptions = setOf(
        "-o",
        "--output",
        "-O",
        "--remote-name",
        "--output-dir",
        "-K",
        "--config",
        "-D",
        "--dump-header",
        "--trace",
        "--trace-ascii",
        "--trace-config",
        "-w",
        "--write-out",
    )

    /**
     * Converts user text to safe curl arguments and injects response-friendly flags.
     *
     * Throws IllegalArgumentException with a user-facing reason when the command is unsafe or unsupported.
     */
    fun parse(commandText: String): List<String> {
        val tokens = tokenize(commandText.trim())
        require(tokens.isNotEmpty()) { "请输入 curl 命令" }

        val executable = tokens.first()
        require(executable == "curl" || executable == "curl.exe") {
            "命令必须以 curl 或 curl.exe 开头"
        }

        tokens.forEachIndexed { index, token ->
            // 直接使用 ProcessBuilder 执行单进程；拦截 shell 组合符号，避免用户误执行链式命令。
            require(token !in blockedOperators) { "不支持 shell 组合符号：$token" }

            // 这个工具只展示返回数据，禁止 curl 写文件或把 header 写到外部路径。
            require(token !in blockedOptions) { "不支持会写入文件或改变输出结构的参数：$token" }

            // 常见 shell 注入形式在 ProcessBuilder 中不会展开，但这里提前拒绝，避免用户以为它会被安全解析。
            require(!token.contains("$(") && !token.contains("`")) {
                "不支持 shell 命令替换"
            }

            if (index > 0 && token == "curl") {
                throw IllegalArgumentException("一次只能执行一个 curl 命令")
            }
        }

        val args = tokens.toMutableList()
        if (!args.any { it == "-i" || it == "--include" }) {
            args.add(1, "--include")
        }
        if (!args.any { it == "-s" || it == "--silent" || it.startsWith("-s") }) {
            args.add(1, "--silent")
        }
        if (!args.any { it == "-S" || it == "--show-error" || it.startsWith("-S") }) {
            args.add(1, "--show-error")
        }
        return args
    }

    /**
     * Extracts request-focused display fields from a saved curl command.
     *
     * Lifecycle: request history rows call this while rendering; it never validates or mutates execution input.
     */
    fun parseDisplayInfo(commandText: String): CurlDisplayInfo {
        val tokens = runCatching { tokenize(commandText.trim()) }.getOrDefault(emptyList())
        val method = extractMethod(tokens)
        val url = extractUrl(tokens)
            ?: commandText.lineSequence().firstOrNull().orEmpty().trim().take(120)

        return CurlDisplayInfo(
            method = method,
            url = url.ifBlank { "unknown url" },
            headers = extractHeaders(tokens),
            queryParams = extractQueryParams(url.orEmpty()),
            requestBody = extractRequestBody(tokens),
        )
    }

    /**
     * Reads the HTTP method that curl will most likely use for this command.
     *
     * Display defaults are intentionally conservative so history stays readable even for partial commands.
     */
    private fun extractMethod(tokens: List<String>): String {
        tokens.forEachIndexed { index, token ->
            when {
                token == "-I" || token == "--head" -> return "HEAD"
                token in dataOptionsWithValue || dataOptionPrefixes.any { token.startsWith(it) } -> return "POST"
                token.startsWith("--request=") -> return token.substringAfter("=").uppercase()
                token == "-X" || token == "--request" -> {
                    // The method value is stored in the next token for the common curl request flag form.
                    return tokens.getOrNull(index + 1).orEmpty().uppercase().ifBlank { "GET" }
                }
            }
        }

        return "GET"
    }

    /**
     * Reads request headers from common curl -H/--header forms for display.
     *
     * Header values are never used for execution here; they only make the request summary easier to inspect.
     */
    private fun extractHeaders(tokens: List<String>): List<HeaderLine> {
        return tokens.mapIndexedNotNull { index, token ->
            val rawHeader = when {
                token == "-H" || token == "--header" -> tokens.getOrNull(index + 1)
                token.startsWith("--header=") -> token.substringAfter("=")
                else -> null
            } ?: return@mapIndexedNotNull null

            val separator = rawHeader.indexOf(':')
            if (separator <= 0) return@mapIndexedNotNull null
            HeaderLine(
                name = rawHeader.substring(0, separator).trim(),
                value = rawHeader.substring(separator + 1).trim(),
            )
        }
    }

    /**
     * Reads the request body from curl data options for display.
     *
     * Multiple data flags are joined with ampersands because curl combines them that way for form-style payloads.
     */
    private fun extractRequestBody(tokens: List<String>): String {
        val bodies = mutableListOf<String>()
        tokens.forEachIndexed { index, token ->
            when {
                token in dataOptionsWithValue -> tokens.getOrNull(index + 1)?.let { bodies += it }
                dataOptionPrefixes.any { token.startsWith(it) } -> bodies += token.substringAfter("=")
            }
        }
        return bodies.joinToString("&")
    }

    /**
     * Reads query parameters from the displayed request URL.
     *
     * Values stay URL-encoded because this panel is for inspecting the exact request address pasted by the user.
     */
    private fun extractQueryParams(url: String): List<RequestParamLine> {
        val query = url.substringAfter("?", missingDelimiterValue = "")
            .substringBefore("#")
        if (query.isBlank()) return emptyList()

        return query
            .split("&")
            .filter { it.isNotBlank() }
            .map { part ->
                RequestParamLine(
                    name = part.substringBefore("="),
                    value = part.substringAfter("=", missingDelimiterValue = ""),
                )
            }
    }

    /**
     * Reads the URL from common curl forms without changing the command parser contract.
     *
     * The history UI only needs a best-effort label, so unsupported forms fall back to the original command preview.
     */
    private fun extractUrl(tokens: List<String>): String? {
        tokens.forEachIndexed { index, token ->
            when {
                token.startsWith("--url=") -> return token.substringAfter("=")
                token == "--url" -> return tokens.getOrNull(index + 1)
                token.looksLikeUrl() -> return token
            }
        }

        return null
    }

    /**
     * Splits shell-like command text while preserving quoted values as one argument.
     *
     * It intentionally supports the common curl quoting cases instead of evaluating shell syntax.
     */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false

        for (char in text) {
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }

                char == '\\' -> {
                    escaping = true
                    tokenStarted = true
                }

                quote != null -> {
                    if (char == quote) {
                        quote = null
                    } else {
                        current.append(char)
                    }
                }

                char == '\'' || char == '"' -> {
                    quote = char
                    // 空引号是合法参数，例如 curl -d ''，不能因为内容为空就丢掉这个 token。
                    tokenStarted = true
                }

                char.isWhitespace() -> {
                    if (tokenStarted) {
                        tokens += current.toString()
                        current.clear()
                        tokenStarted = false
                    }
                }

                else -> {
                    current.append(char)
                    tokenStarted = true
                }
            }
        }

        require(!escaping) { "命令末尾存在未完成的转义符" }
        require(quote == null) { "命令中存在未闭合的引号" }
        if (tokenStarted) {
            tokens += current.toString()
        }
        return tokens
    }

    /**
     * Checks whether a token can serve as the primary request address in history.
     *
     * Restricting this to explicit HTTP schemes avoids mistaking header values or option payloads for URLs.
     */
    private fun String.looksLikeUrl(): Boolean = startsWith("http://") || startsWith("https://")

    /** Curl options whose request body is stored in the next argument. */
    private val dataOptionsWithValue = setOf(
        "-d",
        "--data",
        "--data-raw",
        "--data-binary",
        "--data-urlencode",
        "--json",
    )

    /** Curl long options whose request body is stored after an equals sign. */
    private val dataOptionPrefixes = setOf(
        "--data=",
        "--data-raw=",
        "--data-binary=",
        "--data-urlencode=",
        "--json=",
    )
}

/**
 * Display-only request identity extracted from a curl command.
 *
 * Stored history remains keyed by the full command; these fields only make the list easier to scan.
 */
data class CurlDisplayInfo(
    /** HTTP method shown before the address in request history. */
    val method: String,
    /** Primary request address shown as the request history title. */
    val url: String,
    /** Request headers parsed from -H/--header flags for the inspector panel. */
    val headers: List<HeaderLine>,
    /** Query parameters parsed from the request URL for the inspector panel. */
    val queryParams: List<RequestParamLine>,
    /** Request body parsed from curl data flags for the inspector panel. */
    val requestBody: String,
)

/**
 * One request query parameter parsed from the URL.
 *
 * Values are display-only and intentionally keep the original encoded text from the curl command.
 */
data class RequestParamLine(
    /** Query parameter name before the equals sign. */
    val name: String,
    /** Query parameter value after the equals sign, or blank when no value is present. */
    val value: String,
)
