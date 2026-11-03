package io.github.donnclab.rct.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.compose.ReceiptPreview
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/*
 * The starter app's UI. It only draws GalleryUiState; loading and printing live in MainActivity.kt.
 * Gallery: a list of samples. Detail: Receipt / Template / Data tabs, swipeable, with print in the app bar.
 */

private val Tabs = listOf("Receipt", "Template", "Data")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptGalleryScreen(
    state: GalleryUiState,
    initialTab: Int = 0,
    onSelect: (SampleExample) -> Unit = {},
    onBack: () -> Unit = {},
    onPrint: () -> Unit = {},
    onEdit: (() -> Unit)? = null
) {
    val example = state.selected
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(example?.title ?: "ReceiptScript", maxLines = 1) },
                navigationIcon = {
                    if (example != null) IconButton(onClick = onBack, enabled = !state.printing) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to samples")
                    }
                },
                actions = {
                    if (example != null) {
                        if (onEdit != null) TextButton(onClick = onEdit, enabled = state.sample != null) { Text("Edit") }
                        PrintAction(state, onPrint)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets)) {
            if (example == null) SampleList(onSelect) else SampleDetail(example, state, initialTab)
        }
    }
}

@Composable
private fun PrintAction(state: GalleryUiState, onPrint: () -> Unit) {
    if (state.printing) {
        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = onPrint, enabled = state.receipt?.isSuccess == true) {
            Icon(
                painterResource(R.drawable.ic_print),
                contentDescription = if (state.printerConnected) "Print receipt" else "Print receipt (no printer connected)"
            )
        }
    }
}

@Composable
private fun SampleList(onSelect: (SampleExample) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text("Sample receipts", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
                Text(
                    "Each sample is a real .rct template with sample data. Open one to see the receipt, " +
                        "its template and its data. Swipe between them, and print from the top bar.",
                    Modifier.padding(top = 4.dp, bottom = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        itemsIndexed(SampleCatalog.examples, key = { _, item -> item.id }) { index, item ->
            ElevatedCard(
                Modifier.fillMaxWidth().clickable { onSelect(item) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("%02d".format(index + 1), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(item.title, fontWeight = FontWeight.SemiBold)
                        Text(item.description, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SampleDetail(example: SampleExample, state: GalleryUiState, initialTab: Int) {
    val pager = rememberPagerState(initialPage = initialTab) { Tabs.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = pager.currentPage, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Tabs.forEachIndexed { index, label ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = { Text(label) }
                )
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ReceiptPage(state.receipt)
                1 -> SourcePage(state.sample?.source, "${example.id}.rct")
                else -> SourcePage(state.sample?.contextJson, "${example.id}.preview.json")
            }
        }
    }
}

@Composable
private fun ReceiptPage(receipt: Result<Receipt>?) {
    val rendered = receipt?.getOrNull()
    when {
        receipt == null -> Loading()
        rendered == null -> Box(Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Could not render this receipt:\n${receipt.exceptionOrNull()?.message}\n\n" +
                    "Check the Template and Data tabs.",
                color = MaterialTheme.colorScheme.error
            )
        }
        else -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PaperReceipt(rendered)
        }
    }
}

/** Decoration is screen-only. The pixels inside the paper are exactly what prints. */
@Composable
fun PaperReceipt(receipt: Receipt, modifier: Modifier = Modifier) {
    Column(
        modifier.widthIn(max = if (receipt.width > 384) 336.dp else 280.dp).fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(4.dp)).clip(RoundedCornerShape(4.dp)).background(Color.White)
    ) {
        ReceiptPreview(receipt, Modifier.fillMaxWidth())
    }
}

/**
 * Template or JSON source as selectable monospace text. Long lines wrap rather than scroll
 * sideways, so a horizontal swipe always moves between tabs.
 */
@Composable
private fun SourcePage(source: String?, filename: String) {
    if (source == null) Loading() else Column(Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF18232A))) {
        Text(
            filename, Modifier.fillMaxWidth().background(Color(0xFF24333C)).padding(14.dp),
            color = Color(0xFFE2E9E9), fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium
        )
        SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                source.trimEnd(), Modifier.padding(16.dp), color = Color(0xFFE2E9E9),
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

// Android Studio previews. Previews cannot wait for coroutines, so they render with runBlocking.

class ReceiptExamplePreviewProvider : PreviewParameterProvider<SampleExample> {
    override val values: Sequence<SampleExample> get() = SampleCatalog.examples.asSequence()
}

@Composable
private fun previewState(example: SampleExample): GalleryUiState {
    val context = LocalContext.current
    return remember(context, example.id) {
        val catalog = SampleCatalog { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } }
        val sample = catalog.open(example)
        val receipt = runCatching { runBlocking { sampleReceipts(context).render(example.id, catalog.data(sample)) } }
        GalleryUiState(selected = example, sample = sample, receipt = receipt)
    }
}

@Preview(name = "Samples", group = "Gallery", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun SampleListPreview() {
    StarterTheme { ReceiptGalleryScreen(GalleryUiState()) }
}

@Preview(name = "Receipt", group = "Receipts", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun ReceiptTabPreview(@PreviewParameter(ReceiptExamplePreviewProvider::class) example: SampleExample) {
    StarterTheme { ReceiptGalleryScreen(previewState(example)) }
}

@Preview(name = "Template", group = "Tabs", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun TemplateTabPreview() {
    StarterTheme { ReceiptGalleryScreen(previewState(SampleCatalog.examples.first { it.id == "bank" }), initialTab = 1) }
}

@Preview(name = "Data", group = "Tabs", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun DataTabPreview() {
    StarterTheme { ReceiptGalleryScreen(previewState(SampleCatalog.examples.first { it.id == "bank" }), initialTab = 2) }
}
