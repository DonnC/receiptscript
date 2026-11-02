package io.github.donnclab.rct.sample

import org.junit.Assert.*
import org.junit.Test

class ReceiptScreenPreviewTest {
    @Test fun `screen previews cover the actual runnable gallery catalogue`() {
        assertEquals(SampleCatalog.examples, ReceiptExamplePreviewProvider().values.toList())
        assertTrue(ReceiptExamplePreviewProvider().values.any { it.id == "daily-total" })
    }

    @Test fun `default screen state represents the gallery without loading or printer work`() {
        val state = GalleryUiState()
        assertNull(state.selected)
        assertNull(state.document)
        assertFalse(state.printing)
        assertFalse(state.printerConnected)
        assertEquals(0, state.tab)
    }
}
