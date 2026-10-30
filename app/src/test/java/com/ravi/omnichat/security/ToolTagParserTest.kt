package com.ravi.omnichat.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2-3: parser edge cases — nested braces, unicode, junk, unterminated tags. */
class ToolTagParserTest {
    @Test fun nestedBracesInsideStringValue() {
        val tags = ToolTagParser.parse("""[[app:copy {"text":"a {nested} brace and ]] inside"}]]""")
        assertEquals(1, tags.size)
        assertEquals("copy", tags[0].tool)
        val v = ToolPolicy.validate(tags[0], { true }) as ToolPolicy.Verdict.Ok
        assertEquals("a {nested} brace and ]] inside", v.args.getString("text"))
    }

    @Test fun unicodeTextAndArgsSurvive() {
        val text = "नमस्ते 🙏 यह रहा: [[app:weather {\"city\":\"दिल्ली\"}]] धन्यवाद"
        val tags = ToolTagParser.parse(text)
        assertEquals(1, tags.size)
        val v = ToolPolicy.validate(tags[0], { true }) as ToolPolicy.Verdict.Ok
        assertEquals("दिल्ली", v.args.getString("city"))
        assertEquals("नमस्ते 🙏 यह रहा:  धन्यवाद", ToolTagParser.strip(text))
    }

    @Test fun hundredKbOfJunkStaysBounded() {
        val junk = buildString { repeat(10_000) { append("[[app: junk [[ ]] {{}} x".repeat(1)) } }
        assertTrue(junk.length >= 100_000)
        val tags = ToolTagParser.parse(junk) // must terminate, never exceed the cap
        assertTrue(tags.size <= ToolTagParser.MAX_TAGS)
        ToolTagParser.strip(junk) // must not throw
    }

    @Test fun unterminatedTagNeverParses() {
        assertTrue(ToolTagParser.parse("hello [[app:weather {\"city\":\"Delhi\"}").isEmpty())
        assertTrue(ToolTagParser.parse("hello [[app:weather").isEmpty())
        assertTrue(ToolTagParser.parse("hello [[ ").isEmpty())
        assertTrue(ToolTagParser.parse("hello [[app:").isEmpty())
    }

    @Test fun escapedQuotesInsideString() {
        val tags = ToolTagParser.parse("""[[app:sms {"to":"123456","text":"say \"hi\" } now"}]]""")
        assertEquals(1, tags.size)
        val v = ToolPolicy.validate(tags[0], { true }) as ToolPolicy.Verdict.Ok
        assertEquals("say \"hi\" } now", v.args.getString("text"))
    }
}
