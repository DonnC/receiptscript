package dev.receiptscript.sample

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.android.AssetReceiptMediaResolver
import zw.co.donnclab.rct.android.ReceiptScript

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
fun ReceiptGallery(printer: SamplePrinter? = null) {
    val context = LocalContext.current
    val catalog = remember(context) { SampleCatalog { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } } }
    val receipts = remember(context, printer) {
        ReceiptScript(context, printer, images = AssetReceiptMediaResolver(context, "receipts"))
    }
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
                    val source = catalog.open(example)
                    Result.success(PreviewDocument(source, receipts.render(example.id, catalog.data(source))))
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
                if (printer == null) message = "Connect your printer in SamplePrinter.kt. Print these raster bands at their original dot width using your SDK bitmap API or the ESC/POS helper. No print has been attempted."
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

data class GalleryUiState(
    val selected: SampleExample? = null,
    val document: Result<PreviewDocument>? = null,
    val tab: Int = 0,
    val printing: Boolean = false,
    val printerConnected: Boolean = false
)

/** The same screen is used by the activity and Studio previews; it never loads or prints data. */
@Composable
fun ReceiptGalleryScreen(
    state: GalleryUiState,
    onSelect: (SampleExample) -> Unit = {},
    onGallery: () -> Unit = {},
    onTab: (Int) -> Unit = {},
    onPrint: () -> Unit = {}
) {
    val selected = state.selected
    val tab = state.tab
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).background(Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("RECEIPTSCRIPT / STARTER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(selected?.title ?: "Small paper. Big possibilities.", fontFamily = FontFamily.Serif,
                        style = MaterialTheme.typography.headlineSmall)
                }
                if (selected != null) TextButton(onClick = onGallery) { Text("Gallery") }
            }
            val example = selected
            if (example == null) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Real templates. One engine. Your printer.", style = MaterialTheme.typography.bodyMedium)
                    SampleCatalog.examples.forEachIndexed { index, item ->
                        OutlinedCard(Modifier.fillMaxWidth().clickable { onSelect(item) }, shape = RoundedCornerShape(16.dp)) {
                            Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("0${index + 1}", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                                Column { Text(item.title, fontWeight = FontWeight.SemiBold); Text(item.description, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf("Receipt", ".rct", "Data").forEachIndexed { index, label ->
                        TextButton(onClick = { onTab(index) }) { Text(if (tab == index) "[$label]" else label) }
                    }
                }
                val loaded = state.document
                val previewDocument = loaded?.getOrNull()
                val receiptDocument = previewDocument?.document
                when {
                    loaded == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                    receiptDocument == null -> Text("Could not render: ${loaded.exceptionOrNull()?.message}", Modifier.padding(20.dp))
                    else -> {
                        key(example.id, tab) {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                if (tab == 0) {
                                    Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(androidx.compose.ui.Alignment.CenterHorizontally)) {
                                        ReceiptPreview(requireNotNull(previewDocument).receipt)
                                    }
                                } else CodePanel(
                                    source = if (tab == 1) receiptDocument.source else receiptDocument.contextJson,
                                    filename = if (tab == 1) "${example.id}.rct" else "${example.id}.preview.json",
                                    language = if (tab == 1) CodeLanguage.RCT else CodeLanguage.JSON
                                )
                            }
                        }
                        Button(enabled = !state.printing, modifier = Modifier.fillMaxWidth().padding(16.dp), onClick = onPrint) {
                            Text(if (state.printing) "Printing..." else if (!state.printerConnected) "Connect your printer" else "Print receipt")
                        }
                    }
                }
            }
        }
    }
}
