package io.github.donnclab.rct.studio

import androidx.compose.runtime.*

internal data class StudioDragSlot(val id: Int, val top: Int, val height: Int)

/** Preview ordering is transient; a completed gesture becomes one source edit/undo step. */
@Stable
internal class StudioDragState {
    var activeId: Int? by mutableStateOf(null)
        private set
    var order: List<Int> by mutableStateOf(emptyList())
        private set
    var desiredTop by mutableFloatStateOf(0f)
        private set
    private var rowHeight = 0
    private var original: List<Int> = emptyList()
    val pointerY: Float get() = desiredTop + rowHeight / 2f

    fun start(id: Int, group: List<Int>, top: Int, height: Int) {
        activeId = id; original = group; order = group; desiredTop = top.toFloat(); rowHeight = height
    }

    fun dragBy(amount: Float, slots: List<StudioDragSlot>) {
        if (activeId == null) return
        desiredTop += amount
        updateTargets(slots)
    }

    fun updateTargets(slots: List<StudioDragSlot>) {
        val active = activeId ?: return
        val target = slots.firstOrNull { it.id in order && pointerY >= it.top && pointerY < it.top + it.height } ?: return
        if (target.id == active) return
        val from = order.indexOf(active)
        val to = order.indexOf(target.id)
        val activeSlot = slots.firstOrNull { it.id == active } ?: return
        // Input may arrive again before LazyColumn measures the preceding reorder.
        // Ignore targets whose measured position still describes the old order.
        if ((to > from) != (target.top > activeSlot.top)) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun finish(): List<Int>? {
        val result = order.takeIf { it != original }
        cancel()
        return result
    }

    fun cancel() { activeId = null; order = emptyList(); original = emptyList() }
}
