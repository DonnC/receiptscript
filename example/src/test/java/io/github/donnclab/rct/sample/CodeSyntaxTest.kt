package io.github.donnclab.rct.sample

import org.junit.Assert.*
import org.junit.Test

class CodeSyntaxTest {
    @Test fun `rct highlighting reuses the engine scanner and keeps bindings separate`() {
        val source = "# comment\ncenter bold \"{{merchant.name}}\"\n"
        val spans = CodeSyntax.tokens(source, CodeLanguage.RCT)
        assertTrue(spans.any { it.kind == CodeKind.KEYWORD && source.substring(it.start, it.end) == "center" })
        assertTrue(spans.any { it.kind == CodeKind.BINDING && source.substring(it.start, it.end) == "{{merchant.name}}" })
        assertTrue(spans.any { it.kind == CodeKind.COMMENT })
    }

    @Test fun `json highlights keys separately from values and respects escaped quotes`() {
        val source = """{"name": "Store \"One\"", "amount": -12.50, "paid": true, "note": null}"""
        val spans = CodeSyntax.tokens(source, CodeLanguage.JSON)
        assertEquals(4, spans.count { it.kind == CodeKind.PROPERTY })
        assertTrue(spans.any { it.kind == CodeKind.STRING && source.substring(it.start, it.end) == "\"Store \\\"One\\\"\"" })
        assertTrue(spans.any { it.kind == CodeKind.NUMBER && source.substring(it.start, it.end) == "-12.50" })
        assertEquals(2, spans.count { it.kind == CodeKind.LITERAL })
    }

    @Test fun `highlighting never mutates source indentation or adds display text`() {
        val source = "receipt \"demo\"\nbody:\n  center \"Hello\"\n"
        val spans = CodeSyntax.tokens(source, CodeLanguage.RCT)
        assertTrue(spans.all { it.start >= 0 && it.end <= source.length && it.end > it.start })
        assertTrue(spans.zipWithNext().all { (first, second) -> first.end <= second.start })
        assertEquals(source, CodeSyntax.tokens(source, CodeLanguage.RCT).joinToString("") { source.substring(it.start, it.end) })
    }
}
