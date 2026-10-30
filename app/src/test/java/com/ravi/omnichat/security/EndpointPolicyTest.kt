package com.ravi.omnichat.security

import org.junit.Assert.*
import org.junit.Test

class EndpointPolicyTest {
    @Test fun httpsRemoteAccepted() {
        assertNull(EndpointPolicy.checkAiEndpoint("https://api.openai.com/v1", false))
        assertNull(EndpointPolicy.checkAiEndpoint("https://generativelanguage.googleapis.com/v1beta", false))
    }

    @Test fun cleartextRemoteRejectedEvenWhenLocalHttpAllowed() {
        assertNotNull(EndpointPolicy.checkAiEndpoint("http://api.openai.com/v1", false))
        assertNotNull(EndpointPolicy.checkAiEndpoint("http://api.openai.com/v1", true))
        assertNotNull(EndpointPolicy.checkAiEndpoint("http://8.8.8.8:11434/v1", true))
    }

    @Test fun localHttpNeedsOptIn() {
        assertNotNull(EndpointPolicy.checkAiEndpoint("http://192.168.1.10:11434/v1", false))
        assertNull(EndpointPolicy.checkAiEndpoint("http://192.168.1.10:11434/v1", true))
        assertNull(EndpointPolicy.checkAiEndpoint("http://localhost:1234/v1", true))
        assertNull(EndpointPolicy.checkAiEndpoint("http://10.0.2.2:11434/v1", true))
        assertNull(EndpointPolicy.checkAiEndpoint("http://[::1]:11434/v1", true))
    }

    @Test fun invalidEndpointsRejected() {
        listOf("", "not a url", "ftp://example.com", "file:///etc/passwd", "javascript:alert(1)", "https://",
            "https://user:pass@api.example.com/v1", "https://api.example.com/v1?key=abc", "https://api.example.com:99999/v1",
            "https://api.example.com/v1#frag").forEach {
            assertNotNull("should reject $it", EndpointPolicy.checkAiEndpoint(it, true))
        }
    }

    @Test fun privateRanges() {
        assertTrue(EndpointPolicy.isLocalHost("127.0.0.1"))
        assertTrue(EndpointPolicy.isLocalHost("172.16.0.5"))
        assertTrue(EndpointPolicy.isLocalHost("172.31.255.255"))
        assertFalse(EndpointPolicy.isLocalHost("172.32.0.1"))
        assertTrue(EndpointPolicy.isLocalHost("fd00::1"))
        assertTrue(EndpointPolicy.isLocalHost("fe80::1"))
        assertTrue(EndpointPolicy.isLocalHost("mypc.local"))
        assertFalse(EndpointPolicy.isLocalHost("example.com"))
        assertFalse(EndpointPolicy.isLocalHost("192.168.1.1.evil.com"))
        assertFalse(EndpointPolicy.isLocalHost("2001:db8::1"))
    }

    @Test fun safeLinks() {
        assertEquals("https://example.com/a", EndpointPolicy.safeLink("https://example.com/a"))
        listOf("javascript:alert(1)", "intent://x#Intent;end", "file:///sdcard/a", "content://x/y", "https://", "https://a b.com",
            "https://user@evil.com").forEach { assertNull("should reject $it", EndpointPolicy.safeLink(it)) }
    }
}
