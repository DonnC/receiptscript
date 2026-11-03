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
import androidx.compose.runtime.saveable.rememberSaveable
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
 * The gallery host. Studio reuses this engine and its sample documents:
 *  - one ReceiptScript, created with the driver from SamplePrinter.kt
 *  - render(id, data) when a sample is opened, print(receipt) from the app bar
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

/**
 * The one ReceiptScript the app uses. Templates come from `assets/receipts/` (the default), and
 * images resolve from the same folder, so `asset:logo.png` works here and in the VS Code preview.
 */
fun sampleReceipts(context: Context, printer: ReceiptDriver? = null): ReceiptScript =
    ReceiptScript(context, printer, images = AssetImageResolver(context, directory = "receipts"))

@Composable
fun ReceiptGallery(printer: ReceiptDriver? = null) {
    val context = LocalContext.current
    val catalog = remember(context) { SampleCatalog { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } } }
    val receipts = remember(context, printer) { sampleReceipts(context, printer) }
    val scope = rememberCoroutineScope()

    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = SampleCatalog.examples.firstOrNull { it.id == selectedId }
    var sample by remember { mutableStateOf<SampleSource?>(null) }
    var receipt by remember { mutableStateOf<Result<Receipt>?>(null) }
    var printing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    // Load the template and its sample data, then render. A render error still leaves the
    // Template and Data tabs readable, which is where the mistake usually is.
    LaunchedEffect(selected, reload) {
        sample = null
        receipt = null
        val example = selected ?: return@LaunchedEffect
        val source = withContext(Dispatchers.IO) {
            val file = android.util.AtomicFile(context.filesDir.resolve("studio-drafts/${example.id}.json"))
            if (file.baseFile.exists()) {
                val stored = org.json.JSONObject(String(file.readFully(), Charsets.UTF_8))
                SampleSource(stored.getString("source"), stored.getString("dataJson"))
            } else catalog.open(example)
        }
        sample = source
        receipt = try {
            Result.success(receipts.renderSource(source.source, catalog.data(source)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }

    BackHandler(enabled = selected != null && !printing && !editing) { selectedId = null }

    if (editing && selected != null && sample != null) {
        SampleStudio(selected, sample!!, receipts, printer != null, onClose = { editing = false; reload++ })
        return
    }

    ReceiptGalleryScreen(
        state = GalleryUiState(selected, sample, receipt, printing, printerConnected = printer != null),
        onSelect = { selectedId = it.id },
        onBack = { selectedId = null },
        onEdit = { editing = true },
        onPrint = {
            val ready = receipt?.getOrNull() ?: return@ReceiptGalleryScreen
            if (printer == null) {
                message = "No printer is connected. Return your SDK driver or an EscPosDriver from " +
                    "connectedPrinter() in SamplePrinter.kt. Nothing was printed."
            } else scope.launch {
                printing = true
                message = try {
                    receipts.print(ready)
                    "Sent to the printer. Check the paper to confirm it printed."
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    "Printing failed: ${failure.message}. Part of the receipt may have printed; check before trying again."
                } finally {
                    printing = false
                }
            }
        }
    )

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Printer") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}

/** Everything the gallery screen draws. It holds results only, so Studio previews can build it directly. */
data class GalleryUiState(
    val selected: SampleExample? = null,
    val sample: SampleSource? = null,
    val receipt: Result<Receipt>? = null,
    val printing: Boolean = false,
    val printerConnected: Boolean = false
)

@Composable
internal fun StarterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFFC2471F), background = Color(0xFFF4F2EC), surface = Color(0xFFF4F2EC),
        surfaceContainer = Color(0xFFFFFDF8)
    ), content = content)
}
