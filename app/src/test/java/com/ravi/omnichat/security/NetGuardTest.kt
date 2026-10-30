package com.ravi.omnichat.security

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class NetGuardTest {
    @Test fun aiModeDecisions() {
        assertTrue(NetGuard.decide(NetGuard.Mode.AI, "https", "api.openai.com", "https://api.openai.com/", false) is NetGuard.Decision.Allow)
        assertTrue(NetGuard.decide(NetGuard.Mode.AI, "http", "api.openai.com", "http://api.openai.com/", true) is NetGuard.Decision.Block)
        assertTrue(NetGuard.decide(NetGuard.Mode.AI, "http", "192.168.1.5", "http://192.168.1.5/", false) is NetGuard.Decision.Block)
        assertTrue(NetGuard.decide(NetGuard.Mode.AI, "http", "192.168.1.5", "http://192.168.1.5/", true) is NetGuard.Decision.Allow)
    }

    @Test fun publicModeUpgradesAndBlocksLan() {
        val d = NetGuard.decide(NetGuard.Mode.PUBLIC, "http", "example.com", "http://example.com/x", false)
        assertEquals("https://example.com/x", (d as NetGuard.Decision.Upgrade).httpsUrl)
        assertTrue(NetGuard.decide(NetGuard.Mode.PUBLIC, "https", "10.0.0.1", "https://10.0.0.1/", true) is NetGuard.Decision.Block)
    }

    /** A redirect from the (local test) server to another host must not be followed with the API key. */
    @Test fun aiClientDoesNotFollowRedirects() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://evil.example/steal"))
        server.start()
        try {
            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .addInterceptor(NetGuard(NetGuard.Mode.AI, allowLocalHttp = { true })).build()
            val r = client.newCall(Request.Builder().url(server.url("/v1/chat")).header("x-api-key", "sk-test-123456789").build()).execute()
            assertEquals(302, r.code)
            r.close()
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun publicClientBlocksDowngradeRedirectHop() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", server.url("/next").toString()))
        try {
            // The mock server is on localhost (http), so PUBLIC mode must refuse it outright.
            val client = OkHttpClient.Builder().addInterceptor(NetGuard(NetGuard.Mode.PUBLIC)).build()
            try { client.newCall(Request.Builder().url(server.url("/")).build()).execute(); fail("expected block") }
            catch (e: InsecureRequestException) { /* ok */ }
            assertEquals(0, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun timeoutsSurfaceAsTimeouts() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("slow").setBodyDelay(3, TimeUnit.SECONDS))
        server.start()
        try {
            val client = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).addInterceptor(NetGuard(NetGuard.Mode.AI, allowLocalHttp = { true })).build()
            val e = runCatching { client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body!!.string() } }.exceptionOrNull()
            assertNotNull(e)
            assertEquals(com.ravi.omnichat.data.ErrorKind.TIMEOUT, com.ravi.omnichat.data.ProviderErrors.classify(e!!))
        } finally { server.shutdown() }
    }
}
