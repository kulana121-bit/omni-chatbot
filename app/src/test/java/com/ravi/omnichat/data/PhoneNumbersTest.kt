package com.ravi.omnichat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** P1-3: no hardcoded country code; blank numbers resolve to null, not a fake success. */
class PhoneNumbersTest {
    @Test fun diallingCodeFromSimCountry() {
        assertEquals("91", PhoneNumbers.diallingCode("in"))
        assertEquals("91", PhoneNumbers.diallingCode("IN"))
        assertEquals("1", PhoneNumbers.diallingCode("us"))
        assertEquals("44", PhoneNumbers.diallingCode("gb"))
        assertNull(PhoneNumbers.diallingCode("xx"))
        assertNull(PhoneNumbers.diallingCode(null))
        assertNull(PhoneNumbers.diallingCode(""))
    }

    @Test fun localNumberGetsSimCountryCode() {
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("98765 43210", "91"))
        assertEquals("12025550123", PhoneNumbers.forWhatsApp("2025550123", "1"))
    }

    @Test fun internationalNumberPassesThrough() {
        assertEquals("442071234567", PhoneNumbers.forWhatsApp("+44 20 7123 4567", "91"))
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("0091 98765 43210", "1"))
        assertEquals("919876543210", PhoneNumbers.forWhatsApp("919876543210", "91"))
    }

    @Test fun blankOrUselessNumberIsNull() {
        assertNull(PhoneNumbers.forWhatsApp("", "91"))
        assertNull(PhoneNumbers.forWhatsApp("   ", "91"))
        assertNull(PhoneNumbers.forWhatsApp(null, "91"))
        assertNull(PhoneNumbers.forWhatsApp("abc", "91"))
        assertNull(PhoneNumbers.forWhatsApp("123", "91"))
    }
}
