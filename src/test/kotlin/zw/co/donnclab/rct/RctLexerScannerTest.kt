package zw.co.donnclab.rct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RctLexerScannerTest {

    @Test
    fun `nested collection paths are valid outside quoted bindings`() {
        val source = "for record in group.records:"
        val tokens = RctLexerScanner.scan(source)
        assertTrue(tokens.none { it.type == RctHighlightTokenType.BAD_CHARACTER })
        assertTrue(tokens.any { it.type == RctHighlightTokenType.IDENTIFIER && it.text(source) == "group.records" })
    }

    @Test
    fun `scanner should classify core receipt keywords`() {
        val source = """
            receipt "sale-default"
            meta:
              width: 42
              font: 21
              fontScaleMode: "relative"
            center bold underline "{{merchant.name}}"
            kv "INVOICE" "{{txn.invoiceId}}"
        """.trimIndent()
        val tokens = RctLexerScanner.scan(source).filterNot { it.type == RctHighlightTokenType.WHITESPACE }

        assertEquals(RctHighlightTokenType.KEYWORD, tokens[0].type)
        assertEquals("receipt", tokens[0].text(source))
        assertTrue(tokens.any { it.type == RctHighlightTokenType.STRING })
        assertTrue(tokens.any { it.type == RctHighlightTokenType.KEYWORD && it.text(source) == "meta" })
        assertTrue(tokens.any { it.type == RctHighlightTokenType.KEYWORD && it.text(source) == "font" })
        assertTrue(tokens.any { it.type == RctHighlightTokenType.KEYWORD && it.text(source) == "fontScaleMode" })
        assertTrue(tokens.any { it.type == RctHighlightTokenType.KEYWORD && it.text(source) == "underline" })
        assertTrue(tokens.any { it.type == RctHighlightTokenType.NUMBER })
    }

    @Test
    fun `scanner should classify comments and interpolations`() {
        val source = """
            # receipt note
            center "{{txn.amount | money:txn.currency}}"
        """.trimIndent()

        val tokens = RctLexerScanner.scan(source).filterNot { it.type == RctHighlightTokenType.WHITESPACE }

        assertEquals(RctHighlightTokenType.COMMENT, tokens.first().type)
        assertTrue(tokens.any { it.type == RctHighlightTokenType.INTERPOLATION })
    }

    @Test
    fun `scanner should flag unterminated strings as bad characters`() {
        val source = "center \"broken"
        val tokens = RctLexerScanner.scan(source).filterNot { it.type == RctHighlightTokenType.WHITESPACE }

        assertEquals(RctHighlightTokenType.KEYWORD, tokens[0].type)
        assertEquals(RctHighlightTokenType.BAD_CHARACTER, tokens[1].type)
    }

    @Test
    fun `scanner should classify section and reusable fragment keywords`() {
        val source = """
            use header "merchant-default"
            body:
              center "Hello"
            footer:
              center "Thanks"
        """.trimIndent()

        val keywordTexts = RctLexerScanner.scan(source)
            .filter { it.type == RctHighlightTokenType.KEYWORD }
            .map { it.text(source) }

        assertTrue("use" in keywordTexts)
        assertTrue("header" in keywordTexts)
        assertTrue("body" in keywordTexts)
        assertTrue("footer" in keywordTexts)
    }
}
