package io.github.donnclab.rct

import org.junit.Assert.*
import org.junit.Test

class RctCompilerTest {
    @Test fun `compiled templates render fresh data and compose shared footer`() {
        val engine = RctCompiler(templates = mapOf("footer" to "center \"Thanks\""))
        val template = engine.compile("receipt \"sale\"\nuse footer \"footer\"\nleft \"{{name}}\"")
        val first = template.render(mapOf("name" to "A"))
        assertEquals("A", (first.blocks.first() as ReceiptBlock.Text).text)
        assertEquals("B", (template.render(mapOf("name" to "B")).blocks.first() as ReceiptBlock.Text).text)
        assertEquals(listOf(ReceiptBlock.Spacer(2)), first.blocks.filterIsInstance<ReceiptBlock.Spacer>())
        assertEquals("Thanks", (first.blocks.last() as ReceiptBlock.Text).text)
        assertEquals("sale", first.name)
    }

    @Test fun `barcode defaults to code128 and supports explicit formats`() {
        val engine = RctCompiler()
        val legacy = engine.compile("receipt \"sale\"\nbarcode \"INV-1\" 300 60").render(emptyMap<String, Any>())
        assertEquals(ReceiptBarcodeFormat.CODE128, (legacy.blocks.single() as ReceiptBlock.Barcode).format)
        val typed = engine.compile("receipt \"sale\"\nbarcode \"5901234123457\" 300 60 \"ean13\"").render(null)
        assertEquals(ReceiptBarcodeFormat.EAN13, (typed.blocks.single() as ReceiptBlock.Barcode).format)
    }

    @Test fun `missing bindings fail while conditional fields remain optional`() {
        val engine = RctCompiler()
        assertThrows(IllegalArgumentException::class.java) { engine.compile("receipt \"s\"\nleft \"{{missing}}\"").render(null) }
        assertEquals(0, engine.compile("receipt \"s\"\nif optional:\n  left \"{{optional}}\"").render(null).blocks.size)
        assertThrows(IllegalArgumentException::class.java) { engine.compile("receipt \"s\"\nuse header \"missing\"").render(null) }
    }

    @Test fun `tables preserve column semantics and reject mismatched rows`() {
        val table = RctCompiler().compile("receipt \"s\"\ntable:\n  headers: \"A\" \"B\"\n  weights: 2 1\n  alignments: start end\n  for item in items:\n    row \"{{item.a}}\" \"{{item.b}}\"")
            .render(mapOf("items" to listOf(mapOf("a" to "One", "b" to 2)))).blocks.single() as ReceiptBlock.Table
        assertEquals(listOf(listOf("One", "2")), table.rows)
        assertEquals(listOf(2f, 1f), table.weights)
        assertThrows(IllegalArgumentException::class.java) { RctCompiler().compile("receipt \"s\"\ntable:\n  headers: \"A\" \"B\"\n  row \"1\" \"2\" \"3\"").render(null) }
    }

    @Test fun `money formatting preserves large integral values`() {
        val receipt = RctCompiler().compile("receipt \"s\"\nleft \"{{amount | money:USD}}\"")
            .render(mapOf("amount" to Long.MAX_VALUE))
        assertEquals("USD 9223372036854775807.00", (receipt.blocks.single() as ReceiptBlock.Text).text)
    }
}
