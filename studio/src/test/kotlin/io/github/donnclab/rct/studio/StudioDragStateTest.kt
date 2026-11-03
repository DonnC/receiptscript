package io.github.donnclab.rct.studio

import org.junit.Assert.*
import org.junit.Test

class StudioDragStateTest {
    @Test fun `drag can cross several rows with live preview order and one final commit`() {
        val state = StudioDragState()
        state.start(1, listOf(1, 2, 3), 0, 64)
        state.dragBy(140f, listOf(StudioDragSlot(1, 0, 64), StudioDragSlot(2, 70, 64), StudioDragSlot(3, 140, 64)))
        assertEquals(listOf(2, 3, 1), state.order)
        assertEquals(listOf(2, 3, 1), state.finish())
        assertNull(state.activeId)
    }

    @Test fun `rows outside the selected container are never drag targets`() {
        val state = StudioDragState()
        state.start(1, listOf(1, 2), 0, 64)
        state.dragBy(140f, listOf(StudioDragSlot(1, 0, 64), StudioDragSlot(99, 140, 64)))
        assertEquals(listOf(1, 2), state.order)
        state.cancel()
        assertNull(state.activeId)
    }

    @Test fun `repeated events before list measurement cannot undo a crossing`() {
        val state = StudioDragState()
        val slots = listOf(StudioDragSlot(1, 0, 64), StudioDragSlot(2, 70, 64), StudioDragSlot(3, 140, 64))
        state.start(1, listOf(1, 2, 3), 0, 64)
        repeat(20) { state.dragBy(7f, slots) }
        repeat(10) { state.updateTargets(slots) }
        assertEquals(listOf(2, 3, 1), state.finish())
    }
}
