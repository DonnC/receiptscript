package io.github.donnclab.rct.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.compose.ReceiptPreview
import kotlinx.coroutines.runBlocking

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
                val receiptDocument = previewDocument?.sample
                when {
                    loaded == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                    receiptDocument == null -> Text("Could not render: ${loaded.exceptionOrNull()?.message}", Modifier.padding(20.dp))
                    else -> {
                        key(example.id, tab) {
                            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                if (tab == 0) {
                                    Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().align(Alignment.CenterHorizontally)) {
                                        PaperReceipt(requireNotNull(previewDocument).receipt)
                                    }
                                } else SourcePanel(
                                    source = if (tab == 1) receiptDocument.source else receiptDocument.contextJson,
                                    filename = if (tab == 1) "${example.id}.rct" else "${example.id}.preview.json"
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

/** Decoration is screen-only. The pixels inside the paper are the actual print job. */
@Composable
fun PaperReceipt(receipt: Receipt, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = if (receipt.width > 384) 336.dp else 280.dp).fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
            ReceiptPreview(receipt, Modifier.fillMaxWidth())
        }
    }
}

/** Template or JSON source, shown as selectable monospace text. */
@Composable
private fun SourcePanel(source: String, filename: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF18232A))) {
        Text(filename, Modifier.fillMaxWidth().background(Color(0xFF24333C)).padding(14.dp),
            color = Color(0xFFE2E9E9), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        SelectionContainer(Modifier.horizontalScroll(rememberScrollState())) {
            Text(source.trimEnd(), Modifier.padding(16.dp), color = Color(0xFFE2E9E9),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp, softWrap = false)
        }
    }
}

// Android Studio previews of every screen. They render synchronously because previews cannot wait
// for produceState.

class ReceiptExamplePreviewProvider : PreviewParameterProvider<SampleExample> {
    override val values: Sequence<SampleExample> get() = SampleCatalog.examples.asSequence()
}

@Composable
private fun receiptScreenState(example: SampleExample, tab: Int = 0): GalleryUiState {
    val context = LocalContext.current
    return remember(context, example.id, tab) {
        val catalog = SampleCatalog { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } }
        val document = runCatching {
            val sample = catalog.open(example)
            PreviewDocument(sample, runBlocking { sampleReceipts(context).render(example.id, catalog.data(sample)) })
        }
        GalleryUiState(selected = example, document = document, tab = tab)
    }
}

@Preview(name = "Template gallery", group = "Gallery", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun GalleryScreenPreview() {
    StarterTheme { ReceiptGalleryScreen(GalleryUiState()) }
}

@Preview(name = "Receipt screen", group = "Receipts", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun ReceiptScreenPreview(@PreviewParameter(ReceiptExamplePreviewProvider::class) example: SampleExample) {
    val state = receiptScreenState(example)
    StarterTheme { ReceiptGalleryScreen(state) }
}

@Preview(name = "RCT source", group = "Code", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun TemplateScreenPreview() {
    val state = receiptScreenState(SampleCatalog.examples.first { it.id == "bank" }, tab = 1)
    StarterTheme { ReceiptGalleryScreen(state) }
}

@Preview(name = "JSON context", group = "Code", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun DataScreenPreview() {
    val state = receiptScreenState(SampleCatalog.examples.first { it.id == "bank" }, tab = 2)
    StarterTheme { ReceiptGalleryScreen(state) }
}
