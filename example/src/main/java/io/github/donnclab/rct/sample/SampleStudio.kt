package io.github.donnclab.rct.sample

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import io.github.donnclab.rct.ReceiptScript
import io.github.donnclab.rct.studio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Host-owned persistence and PDF destination; none of this belongs to the Studio library. */
@Composable
internal fun SampleStudio(example: SampleExample, sample: SampleSource, engine: ReceiptScript, canPrint: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberReceiptStudioState(sample.source, sample.contextJson)
    var leaving by remember { mutableStateOf(false) }
    var pdf by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val bytes = pdf
        pdf = null
        if (uri != null && bytes != null) scope.launch {
            try { withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) } } }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Could not save PDF." }
        }
    }
    BackHandler { if (state.isDirty) leaving = true else onClose() }
    ReceiptStudio(
        state, engine, title = example.title, onClose = onClose,
        onSave = { draft ->
            withContext(Dispatchers.IO) {
                val directory = context.filesDir.resolve("studio-drafts").apply { mkdirs() }
                val file = android.util.AtomicFile(directory.resolve("${example.id}.json"))
                val bytes = org.json.JSONObject().put("source", draft.source).put("dataJson", draft.dataJson).toString().toByteArray()
                val stream = file.startWrite()
                try { stream.write(bytes); file.finishWrite(stream) }
                catch (failure: Exception) { file.failWrite(stream); throw failure }
            }
        },
        onPrint = if (canPrint) engine::print else null,
        onExport = { receipt ->
            pdf = withContext(Dispatchers.Default) { receipt.toPdf().bytes }
            destination.launch("${example.id}.pdf")
        }
    )
    if (leaving) AlertDialog(onDismissRequest = { leaving = false }, title = { Text("Leave this draft?") },
        text = { Text("Your changes have not been saved.") },
        confirmButton = { TextButton(onClick = onClose) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { leaving = false }) { Text("Keep editing") } })
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("Export") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } }) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun StudioPhonePreview() {
    val context = LocalContext.current
    MaterialTheme {
        ReceiptStudio(rememberReceiptStudioState("receipt \"sale\"\nmeta:\n  width: 34\n  font: 21\n\nheader:\n  center bold \"COFFEE & CODE\"\nbody:\n  kv \"TOTAL\" \"{{txn.total | money:txn.currency}}\"\nfooter:\n  center \"Thank you!\"",
            "{\"txn\": {\"total\": 10, \"currency\": \"USD\"}}"), remember { sampleReceipts(context) }, title = "Sale receipt")
    }
}

@Preview(showBackground = true, widthDp = 1200, heightDp = 800)
@Composable
private fun StudioTabletPreview() { StudioPhonePreview() }
