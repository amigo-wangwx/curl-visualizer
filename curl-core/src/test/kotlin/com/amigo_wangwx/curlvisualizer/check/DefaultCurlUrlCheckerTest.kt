package com.amigo_wangwx.curlvisualizer.check

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultCurlUrlCheckerTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/ok") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/ok")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        createContext("/login-redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/login")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        createContext("/login") { exchange ->
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        createContext("/forbidden") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        createContext("/missing") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        createContext("/server-error") { exchange ->
            exchange.sendResponseHeaders(503, -1)
            exchange.close()
        }
        start()
    }
    private val checker = DefaultCurlUrlChecker()
    private val baseUrl = "http://127.0.0.1:${server.address.port}"

    @AfterTest
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `classifies HTTP responses and redirects`() = runBlocking {
        assertEquals(CurlCheckStatus.OK, checker.checkUrl("$baseUrl/ok").status)
        assertEquals(CurlCheckStatus.REDIRECT, checker.checkUrl("$baseUrl/redirect").status)
        assertEquals(
            CurlCheckStatus.FORBIDDEN_OR_LOGIN,
            checker.checkUrl("$baseUrl/login-redirect").status,
        )
        assertEquals(
            CurlCheckStatus.FORBIDDEN_OR_LOGIN,
            checker.checkUrl("$baseUrl/forbidden").status,
        )
        assertEquals(CurlCheckStatus.NOT_FOUND, checker.checkUrl("$baseUrl/missing").status)
        assertEquals(
            CurlCheckStatus.SERVER_ERROR,
            checker.checkUrl("$baseUrl/server-error").status,
        )
    }

    @Test
    fun `returns invalid url without starting a request`() = runBlocking {
        val result = checker.checkUrl("not-a-url")

        assertEquals(CurlCheckStatus.INVALID_URL, result.status)
        assertNull(result.statusCode)
        assertNull(result.finalUrl)
        assertTrue(result.error?.isNotBlank() == true)
    }

    @Test
    fun `batch emits exactly one result per input`() = runBlocking {
        val urls = listOf("$baseUrl/ok", "$baseUrl/missing", "invalid")

        val results = checker.checkBatch(
            urls = urls,
            options = CurlCheckOptions(timeoutSeconds = 5, workers = 2),
        ).toList()

        assertEquals(urls.size, results.size)
        assertEquals(urls.toSet(), results.map { it.url }.toSet())
    }

    @Test
    fun `custom rules are applied inside the checker pipeline`() = runBlocking {
        val customPolicy = CurlCheckPolicies.defaultWith(
            CurlCheckRule { facts ->
                CurlCheckStatus.FORBIDDEN_OR_LOGIN.takeIf {
                    facts.finalUrl?.endsWith("/ok") == true
                }
            },
        )
        val customChecker = DefaultCurlUrlChecker(policy = customPolicy)

        val result = customChecker.checkUrl("$baseUrl/ok")

        assertEquals(CurlCheckStatus.FORBIDDEN_OR_LOGIN, result.status)
    }
}
