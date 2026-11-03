package io.github.donnclab.rct.studio

import io.github.donnclab.rct.tooling.RctSourceDocument
import org.junit.Assert.*
import org.junit.Test

class StudioOutlineTest {
    @Test fun `outline follows print sections rather than definition position`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nmeta:\n  font: 21\nuse footer \"shared\"\nfooter:\n  center \"Thanks\"\nbody:\n  left \"Sale\"\nheader:\n  center \"Shop\"\nuse header \"brand\"")
        val sections = studioSections(document)
        assertEquals(listOf("Header", "Body", "Footer"), sections.map { it.title })
        assertEquals(listOf("use header \"brand\"", "center \"Shop\""), sections[0].nodes.map { it.statement })
        assertEquals(listOf("left \"Sale\""), sections[1].nodes.map { it.statement })
        assertEquals(listOf("center \"Thanks\"", "use footer \"shared\""), sections[2].nodes.map { it.statement })
        assertFalse(sections.flatMap { it.nodes }.any { it.statement.startsWith("receipt ") || it.statement == "meta:" })
    }

    @Test fun `table internals stay behind expansion instead of crowding the outline`() {
        val document = RctSourceDocument.parse("receipt \"x\"\ntable:\n  headers: \"Item\" \"Total\"\n  for item in items:\n    row \"{{item.name}}\" \"{{item.total}}\"")
        val table = studioSections(document)[1].nodes.single()
        assertEquals("table:", table.statement)
        assertEquals(2, outlineChildren(document, table).size)
        assertEquals("Table", blockTitle(table.statement))
    }
}
