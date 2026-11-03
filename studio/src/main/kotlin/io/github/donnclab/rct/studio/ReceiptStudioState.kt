package io.github.donnclab.rct.studio

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable

/** The complete editable document. Template identity and persistence belong to the host. */
data class ReceiptStudioDraft(val source: String, val dataJson: String = "{}")

/** Hoist this state to observe drafts, guard navigation or persist drafts in your application. */
@Stable
class ReceiptStudioState(initial: ReceiptStudioDraft, initiallySaved: Boolean = true) {
    var draft: ReceiptStudioDraft by mutableStateOf(initial)
        private set
    private var saved by mutableStateOf<ReceiptStudioDraft?>(if (initiallySaved) initial else null)
    private val history = mutableStateListOf<ReceiptStudioDraft>()
    private val future = mutableStateListOf<ReceiptStudioDraft>()

    val isDirty: Boolean get() = draft != saved
    val canUndo: Boolean get() = history.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun editSource(source: String) { update(draft.copy(source = source)) }
    fun editData(json: String) { update(draft.copy(dataJson = json)) }

    fun undo() {
        if (history.isEmpty()) return
        future.add(draft)
        draft = history.removeAt(history.lastIndex)
    }

    fun redo() {
        if (future.isEmpty()) return
        history.add(draft)
        draft = future.removeAt(future.lastIndex)
    }

    /** Call only after persistence succeeds. Passing the saved snapshot preserves newer edits. */
    fun markSaved(snapshot: ReceiptStudioDraft = draft) { saved = snapshot }

    private fun update(next: ReceiptStudioDraft) {
        if (next == draft) return
        history.add(draft)
        if (history.size > 100) history.removeAt(0)
        future.clear()
        draft = next
    }

    companion object {
        internal val StateSaver = Saver<ReceiptStudioState, List<String>>(
            save = { listOf(it.draft.source, it.draft.dataJson, it.saved?.source.orEmpty(), it.saved?.dataJson.orEmpty(), (it.saved != null).toString()) },
            restore = {
                ReceiptStudioState(ReceiptStudioDraft(it[0], it[1])).apply {
                    saved = if (it[4] == "true") ReceiptStudioDraft(it[2], it[3]) else null
                }
            }
        )
    }
}

/** Remembers drafts across configuration changes. Undo history is intentionally memory-only. */
@Composable
fun rememberReceiptStudioState(source: String, dataJson: String = "{}", initiallySaved: Boolean = true): ReceiptStudioState =
    rememberSaveable(saver = ReceiptStudioState.StateSaver) {
        ReceiptStudioState(ReceiptStudioDraft(source, dataJson), initiallySaved)
    }
