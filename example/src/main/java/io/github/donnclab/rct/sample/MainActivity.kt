package io.github.donnclab.rct.sample

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.donnclab.rct.AssetImageResolver
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.ReceiptDriver
import io.github.donnclab.rct.ReceiptScript
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The starter app. Everything that touches ReceiptScript is in this file:
 *  - one ReceiptScript, created with the driver from SamplePrinter.kt
 *  - render(id, data) on selection, print(receipt) on the button
 * GalleryScreen.kt only draws state; SampleCatalog.kt reads the bundled templates and sample data.
 */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val printer = remember { connectedPrinter(this@MainActivity) }
            StarterTheme { ReceiptGallery(printer) }
        }
    }
}

@Composable
internal fun StarterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xE8FA663B), background = Color(0xFFF4F2EC), surface = Color(0xFFFFFDF8)
    ), content = content)
}

@Composable
fun ReceiptGallery(printer: ReceiptDriver? = null) {
    val context = LocalContext.current
    val catalog = remember(context) { SampleCatalog { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } } }
    val receipts = remember(context, printer) { sampleReceipts(context, printer) }
    var selected by remember { mutableStateOf<SampleExample?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var printing by remember { mutableStateOf(false) }
    BackHandler(enabled = selected != null && !printing) { selected = null }
    val scope = rememberCoroutineScope()
    val document by produceState<Result<PreviewDocument>?>(null, selected, receipts) {
        value = null
        selected?.let { example ->
            value = withContext(Dispatchers.IO) {
                try {
                    val sample = catalog.open(example)
                    Result.success(PreviewDocument(sample, receipts.render(example.id, catalog.data(sample))))
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { Result.failure(failure) }
            }
        }
    }
    ReceiptGalleryScreen(
        state = GalleryUiState(selected, document, tab, printing, printer != null),
        onSelect = { tab = 0; selected = it },
        onGallery = { selected = null },
        onTab = { tab = it },
        onPrint = {
            val preview = document?.getOrNull()
            if (preview != null) {
                if (printer == null) message = "Connect your printer in SamplePrinter.kt: return your SDK driver or an EscPosDriver. No print has been attempted."
                else scope.launch {
                    printing = true
                    try {
                        receipts.print(preview.receipt)
                        message = "Printer job completed. Confirm the physical output on your device."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { message = "Print failed: ${failure.message}. Output may be partial; check before retrying." }
                    finally { printing = false }
                }
            }
        }
    )
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Printer integration") },
        text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("Close") } }) }
}

/**
 * The one ReceiptScript the app uses. Templates come from `assets/receipts/` (the default), and
 * images resolve from the same folder, so `asset:logo.png` works here and in the VS Code preview.
 */
fun sampleReceipts(context: Context, printer: ReceiptDriver? = null): ReceiptScript =
    ReceiptScript(context, printer, images = AssetImageResolver(context, directory = "receipts"))

/** A gallery entry: its source files and the rendered receipt. */
data class PreviewDocument(val sample: SampleSource, val receipt: Receipt)

data class GalleryUiState(
    val selected: SampleExample? = null,
    val document: Result<PreviewDocument>? = null,
    val tab: Int = 0,
    val printing: Boolean = false,
    val printerConnected: Boolean = false
)
