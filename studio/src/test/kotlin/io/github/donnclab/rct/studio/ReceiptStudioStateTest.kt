package io.github.donnclab.rct.studio

import org.junit.Assert.*
import org.junit.Test

class ReceiptStudioStateTest {
    private val initial = ReceiptStudioDraft("receipt \"sale\"\nleft \"Hello\"", "{}")

    @Test fun `new documents can be saved before their first edit`() {
        val state = ReceiptStudioState(initial, initiallySaved = false)
        assertTrue(state.isDirty)
        state.markSaved()
        assertFalse(state.isDirty)
    }

    @Test fun `undo redo keeps template and JSON drafts together`() {
        val state = ReceiptStudioState(initial)
        state.editSource(initial.source + "\nspacer 1")
        state.editData("{\"total\":10}")
        state.undo()
        assertEquals("{}", state.draft.dataJson)
        state.undo()
        assertEquals(initial, state.draft)
        assertFalse(state.isDirty)
        state.redo()
        assertTrue(state.isDirty)
        state.editData("{\"new\":true}")
        assertFalse(state.canRedo)
    }

    @Test fun `saving an older revision cannot clear new edits`() {
        val state = ReceiptStudioState(initial)
        state.editData("{\"a\":1}")
        val saving = state.draft
        state.editData("{\"a\":2}")
        state.markSaved(saving)
        assertTrue(state.isDirty)
        state.undo()
        assertFalse(state.isDirty)
    }

    @Test fun `invalid source and JSON remain editable and undoable`() {
        val state = ReceiptStudioState(initial)
        state.editSource("receipt \"")
        state.editData("{")
        assertEquals("{", state.draft.dataJson)
        state.undo()
        state.undo()
        assertEquals(initial, state.draft)
    }

    @Test fun `outline summaries are readable without changing the underlying binding`() {
        val source = "receipt \"sale\"\nkv \"TOTAL\" \"{{txn.total | money:txn.currency}}\""
        val document = io.github.donnclab.rct.tooling.RctSourceDocument.parse(source)
        assertEquals("TOTAL | Txn total", blockSummary(document, document.nodes.last()))
        assertEquals(source, document.source)
    }
}
