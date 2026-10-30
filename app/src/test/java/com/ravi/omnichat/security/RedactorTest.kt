package com.ravi.omnichat.security

import org.junit.Assert.*
import org.junit.Test

class RedactorTest {
    @Test fun redactsCommonKeyFormats() {
        val samples = listOf(
            "sk-proj-abcdefghijklmnop1234", "sk-ant-api03-abcdefghijk", "AIzaSyA1234567890abcdefghijklmnop",
            "gsk_abcdefghijklmnop", "xai-abcdefghijklmnopq", "ghp_abcdefghijklmnopqrstu", "github_pat_11ABCDEFGHIJKLMNOPQRSTUV",
            "nvapi-abcdefghijk", "hf_abcdefghijklmn"
        )
        samples.forEach { k ->
            val out = Redactor.redact("Error: invalid key $k provided")
            assertFalse("leaked $k: $out", out.contains(k))
        }
    }

    @Test fun redactsHeadersAndQuery() {
        assertFalse(Redactor.redact("Authorization: Bearer abc.def.ghi123").contains("abc.def.ghi123"))
        assertFalse(Redactor.redact("https://x.com/v1?key=SECRETVALUE&a=1").contains("SECRETVALUE"))
        assertFalse(Redactor.redact("{\"x-api-key\": \"zzzzzzzzzz\"}").contains("zzzzzzzzzz"))
    }

    @Test fun redactsRegisteredSecrets() {
        Redactor.register("my-custom-provider-secret")
        assertEquals("token [REDACTED] used", Redactor.redact("token my-custom-provider-secret used"))
        Redactor.forget("my-custom-provider-secret")
    }

    @Test fun leavesNormalTextAlone() {
        assertEquals("HTTP 404: model not found", Redactor.redact("HTTP 404: model not found"))
    }
}
