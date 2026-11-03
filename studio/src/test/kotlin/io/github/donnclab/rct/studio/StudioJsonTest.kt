package io.github.donnclab.rct.studio

import org.junit.Assert.*
import org.junit.Test

class StudioJsonTest {
    @Test fun `binds nested JSON to core compatible maps lists and primitives`() {
        val data = StudioJson.parse("""{"txn":{"total":10.5},"items":[1,null,true],"name":"shop"}""")
        assertEquals(java.math.BigDecimal("10.5"), (data["txn"] as Map<*, *>)["total"])
        assertEquals(listOf(1L, null, true), data["items"])
        assertEquals("shop", data["name"])
    }

    @Test fun `rejects relaxed JavaScript rather than silently accepting it`() {
        listOf("{a:1}", "{\"a\":1,}", "{'a':1}", "{\"a\":NaN}", "{} garbage", "//comment\n{}", "[]").forEach {
            assertThrows(IllegalArgumentException::class.java) { StudioJson.parse(it) }
        }
    }

    @Test fun `formatting preserves values and is deterministic`() {
        val json = """{"items":[{"name":"Coffee"}],"enabled":true}"""
        val formatted = StudioJson.format(json)
        assertTrue(formatted.contains('\n'))
        assertEquals(StudioJson.parse(json), StudioJson.parse(formatted))
        assertEquals(formatted, StudioJson.format(formatted))
    }

    @Test fun `number precision is not silently rounded`() {
        assertEquals(java.math.BigDecimal("999999999999999999999999999999"),
            StudioJson.parse("""{"amount":999999999999999999999999999999}""")["amount"])
    }

    @Test fun `number syntax follows JSON rather than a permissive number parser`() {
        listOf("01", "+1", ".1", "1.", "NaN", "Infinity", "True").forEach { literal ->
            assertThrows("Accepted invalid JSON literal $literal", IllegalArgumentException::class.java) { StudioJson.parse("{\"value\":$literal}") }
        }
    }

    @Test fun `unclosed deeply nested drafts are bounded before recursive parsing`() {
        assertThrows(IllegalArgumentException::class.java) { StudioJson.parse("{\"nested\":" + "[".repeat(65)) }
    }

    @Test fun `literal line breaks inside strings must be escaped`() {
        assertThrows(IllegalArgumentException::class.java) { StudioJson.parse("{\"text\":\"first\nsecond\"}") }
        assertEquals("first\nsecond", StudioJson.parse("{\"text\":\"first\\nsecond\"}")["text"])
    }
}
