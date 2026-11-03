package io.github.donnclab.rct.tooling

import org.junit.Assert.*
import org.junit.Test

class RctSourceDocumentTest {
    @Test fun `reorder can move across several sibling slots without moving other sections`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nleft \"A\"\nuse footer \"f\"\nleft \"B\"\nleft \"C\"")
        val rows = document.nodes.filter { it.statement.startsWith("left ") }
        val reordered = document.reorder(listOf(rows[2], rows[0], rows[1]))
        assertEquals(listOf("left \"C\"", "left \"A\"", "left \"B\""), reordered.nodes.filter { it.statement.startsWith("left ") }.map { it.statement })
        assertEquals("use footer \"f\"", reordered.nodes[2].statement)
    }

    @Test fun `independent sections can be created replaced and cleared`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nleft \"Body\"")
        val header = document.withSection("header", "center \"Shop\"")
        assertEquals("center \"Shop\"", header.sectionSource("header").trim())
        assertTrue(header.source.contains("left \"Body\""))
        val footer = header.withSection("footer", "center \"Thanks\"")
        assertTrue(footer.source.contains("footer:"))
        val cleared = footer.withSection("header", "")
        assertFalse(cleared.source.contains("header:"))
        assertTrue(cleared.source.contains("footer:"))
    }

    @Test fun `localising a section can remove references without changing body data`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nuse header \"shared\"\nkv \"TOTAL\" \"{{total}}\"")
        val local = document.withSection("header", "center \"Brand\"", emptyList())
        assertFalse(local.source.contains("use header"))
        assertTrue(local.source.contains("{{total}}"))
    }
    private val source = "receipt \"sale\"\r\n# Keep this comment\r\nuse footer \"shared\"\r\nbody:\r\n  center bold \"{{merchant.name}}\" 24\r\n  if txn.total:\r\n    kv \"TOTAL\" \"{{txn.total | money:txn.currency}}\"\r\n  spacer 1\r\n"

    @Test fun `opening a document is lossless`() {
        assertEquals(source, RctSourceDocument.parse(source).source)
    }

    @Test fun `editing one statement preserves references comments bindings and CRLF`() {
        val document = RctSourceDocument.parse(source)
        val title = document.nodes.first { it.statement.startsWith("center") }
        val edited = document.replaceStatement(title, "center underline \"{{merchant.name}}\" 26")
        assertEquals(source.replace("center bold \"{{merchant.name}}\" 24", "center underline \"{{merchant.name}}\" 26"), edited.source)
    }

    @Test fun `nodes expose nesting without evaluating conditional data`() {
        val document = RctSourceDocument.parse(source)
        val conditional = document.nodes.first { it.statement.startsWith("if ") }
        assertEquals(conditional.id, document.nodes.first { it.statement.startsWith("kv ") }.parentId)
    }

    @Test fun `invalid edits never mutate the original`() {
        val document = RctSourceDocument.parse(source)
        val title = document.nodes.first { it.statement.startsWith("center") }
        assertThrows(IllegalArgumentException::class.java) { document.replaceStatement(title, "html \"no\"") }
        assertEquals(source, document.source)
    }

    @Test fun `moving a group moves its descendants and remains valid`() {
        val document = RctSourceDocument.parse(source)
        val conditional = document.nodes.first { it.statement.startsWith("if ") }
        val moved = document.move(conditional, 1).source
        assertTrue(moved.indexOf("spacer 1") < moved.indexOf("if txn.total:"))
        assertTrue(moved.contains("    kv \"TOTAL\""))
        assertTrue(moved.contains("# Keep this comment"))
    }

    @Test fun `cannot remove the only child of a required block`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nbody:\n  spacer 1")
        assertThrows(IllegalArgumentException::class.java) { document.remove(document.nodes.last()) }
    }

    @Test fun `append respects parent indentation`() {
        val document = RctSourceDocument.parse(source)
        val body = document.nodes.first { it.statement == "body:" }
        val edited = document.append("divider \"_\"", body)
        assertTrue(edited.source.contains("\r\n  divider \"_\""))
    }

    @Test fun `every block palette entry is accepted by core`() {
        RctBlockPalette.entries.forEach { block ->
            RctSourceDocument.parse("receipt \"sample\"\n${block.source}")
        }
    }

    @Test fun `stale node handles cannot edit a new source revision`() {
        val document = RctSourceDocument.parse(source)
        val node = document.nodes.last()
        val next = document.append("spacer 2")
        assertThrows(IllegalArgumentException::class.java) { next.remove(node) }
    }

    @Test fun `property forms round trip every palette example through core`() {
        RctBlockPalette.entries.forEach { example ->
            var document = RctSourceDocument.parse("receipt \"sample\"\n${example.source}")
            val statements = document.nodes.map { it.statement }
            statements.forEachIndexed { index, statement ->
                val form = RctStatementForm.read(statement)
                document = document.replaceStatement(document.nodes[index], form.write(emptyMap()))
            }
        }
    }

    @Test fun `text form retains filters and applies the existing trailing font syntax`() {
        val form = RctStatementForm.read("center small bold \"{{txn.total | money:txn.currency}}\" 24")
        assertEquals("right small bold \"{{txn.total | money:txn.currency}}\" 26",
            form.write(mapOf("align" to "right", "font" to "26")))
    }

    @Test fun `property choices come from barcode formats in core`() {
        val form = RctStatementForm.read("barcode \"1234\" 300 60 \"code128\"")
        assertEquals(io.github.donnclab.rct.ReceiptBarcodeFormat.entries.map { it.id },
            form.properties.first { it.key == "format" }.choices)
    }

    @Test fun `numeric overflow becomes a lint issue instead of crashing the editor`() {
        assertTrue(RctLinter.lint("receipt \"x\"\nspacer 999999999999999999999999").isNotEmpty())
    }

    @Test fun `table options can be added without rewriting its rows or comments`() {
        val document = RctSourceDocument.parse("receipt \"x\"\ntable:\n  # Keep rows\n  row \"{{item.name}}\" \"{{item.total}}\"\n")
        val edited = document.withTableOptions(document.nodes[1], listOf("Item", "Total"), listOf(2f, 1f), null)
        assertTrue(edited.source.contains("headers: \"Item\" \"Total\""))
        assertTrue(edited.source.contains("weights: 2.0 1.0"))
        assertTrue(edited.source.contains("# Keep rows"))
        assertTrue(edited.source.contains("row \"{{item.name}}\" \"{{item.total}}\""))
    }

    @Test fun `clearing optional table config keeps surrounding comments`() {
        val document = RctSourceDocument.parse("receipt \"x\"\ntable:\n  weights: 2 1\n  # Keep this\n  row \"A\" \"B\"\n")
        val edited = document.withTableOptions(document.nodes[1], null, null, null)
        assertFalse(edited.source.contains("weights:"))
        assertTrue(edited.source.contains("# Keep this"))
    }

    @Test fun `metadata can be added without moving shared references`() {
        val document = RctSourceDocument.parse("receipt \"x\"\n# Branding\nuse header \"shared\"\nleft \"Sale\"")
        val edited = document.withMetadata(mapOf("font" to "21", "width" to "34"))
        assertTrue(edited.source.indexOf("meta:") < edited.source.indexOf("use header"))
        assertTrue(edited.source.contains("# Branding"))
        assertTrue(edited.source.contains("use header \"shared\""))
    }

    @Test fun `metadata updates retain unknown host options and comments`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nmeta:\n  font: 20\n  # Host option\n  custom: true\nleft \"Sale\"")
        val edited = document.withMetadata(mapOf("font" to null, "width" to "34"))
        assertFalse(edited.source.contains("font:"))
        assertTrue(edited.source.contains("custom: true"))
        assertTrue(edited.source.contains("# Host option"))
    }

    @Test fun `an empty metadata section without a final newline can be populated`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nmeta:")
        assertTrue(document.withMetadata(mapOf("font" to "21")).source.contains("meta:\n  font: 21"))
    }
}
