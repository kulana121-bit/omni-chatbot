package com.ravi.omnichat.data

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class ProviderErrorsTest {
    @Test fun statusMapping() {
        assertEquals(ErrorKind.AUTH, ProviderErrors.kindForStatus(401))
        assertEquals(ErrorKind.AUTH, ProviderErrors.kindForStatus(403))
        assertEquals(ErrorKind.RATE_LIMIT, ProviderErrors.kindForStatus(429, "Too many requests"))
        assertEquals(ErrorKind.QUOTA, ProviderErrors.kindForStatus(429, "You exceeded your current quota"))
        assertEquals(ErrorKind.QUOTA, ProviderErrors.kindForStatus(402))
        assertEquals(ErrorKind.SERVER, ProviderErrors.kindForStatus(503))
        assertEquals(ErrorKind.NOT_FOUND, ProviderErrors.kindForStatus(404))
        assertEquals(ErrorKind.REDIRECT, ProviderErrors.kindForStatus(302))
    }

    @Test fun exceptionMapping() {
        assertEquals(ErrorKind.DNS, ProviderErrors.classify(UnknownHostException("x")))
        assertEquals(ErrorKind.TLS, ProviderErrors.classify(SSLHandshakeException("bad cert")))
        assertEquals(ErrorKind.TIMEOUT, ProviderErrors.classify(SocketTimeoutException("timeout")))
        assertEquals(ErrorKind.NETWORK, ProviderErrors.classify(java.io.IOException("Stream ended unexpectedly")))
    }

    @Test fun onlyTransientErrorsRetrySameEndpoint() {
        assertTrue(ErrorKind.TIMEOUT.retrySameEndpoint)
        assertTrue(ErrorKind.NETWORK.retrySameEndpoint)
        listOf(ErrorKind.AUTH, ErrorKind.QUOTA, ErrorKind.TLS, ErrorKind.DNS, ErrorKind.CONFIG, ErrorKind.RATE_LIMIT)
            .forEach { assertFalse(it.name, it.retrySameEndpoint) }
    }

    @Test fun backoffGrowsWithJitterAndIsCapped() {
        assertEquals(750, ProviderErrors.backoffMillis(0, 0.0))
        assertEquals(1250, ProviderErrors.backoffMillis(0, 1.0))
        assertEquals(1500, ProviderErrors.backoffMillis(1, 0.0))
        assertTrue(ProviderErrors.backoffMillis(10, 1.0) <= 8000)
    }

    @Test fun userMessagesNeverContainKeys() {
        val msg = ProviderErrors.userMessage(ApiException("HTTP 401: Incorrect API key provided: sk-proj-abcdefghijklmnop", 401))
        assertFalse(msg.contains("abcdefghijklmnop"))
    }
}
