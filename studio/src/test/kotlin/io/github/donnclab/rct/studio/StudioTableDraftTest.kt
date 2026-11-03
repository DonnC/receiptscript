package io.github.donnclab.rct.studio

import io.github.donnclab.rct.ReceiptColumnAlign
import io.github.donnclab.rct.tooling.RctSourceDocument
import org.junit.Assert.*
import org.junit.Test

class StudioTableDraftTest {
    private val document = RctSourceDocument.parse("receipt \"x\"\ntable:\n  row \"{{item.name}}\" \"{{item.total}}\"")

    @Test fun `default switches retain core equal width and centred defaults`() {
        val draft = StudioTableDraft(document, document.nodes[1])
        assertEquals(2, draft.columns.size)
        assertTrue(draft.equalWidths)
        assertTrue(draft.defaultAlignment)
        val result = draft.apply(document, document.nodes[1])
        assertFalse(result.source.contains("weights:"))
        assertFalse(result.source.contains("alignments:"))
        assertTrue(result.source.contains("{{item.total}}"))
    }

    @Test fun `column controls map to core headers weights and alignments`() {
        val draft = StudioTableDraft(document, document.nodes[1])
        draft.showHeaders = true
        draft.equalWidths = false
        draft.defaultAlignment = false
        draft.columns[0].heading = "Item"
        draft.columns[1].heading = "Total"
        draft.columns[0].width = "2"
        draft.columns[1].alignment = ReceiptColumnAlign.END
        val result = draft.apply(document, document.nodes[1])
        assertTrue(result.source.contains("headers: \"Item\" \"Total\""))
        assertTrue(result.source.contains("weights: 2.0 1.0"))
        assertTrue(result.source.contains("alignments: center end"))
        assertTrue(result.source.contains("row \"{{item.name}}\" \"{{item.total}}\""))
    }
}
