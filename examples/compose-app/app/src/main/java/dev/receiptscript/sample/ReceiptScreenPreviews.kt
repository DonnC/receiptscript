package dev.receiptscript.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

class ReceiptExamplePreviewProvider : PreviewParameterProvider<SampleExample> {
    override val values: Sequence<SampleExample> get() = SampleCatalog.examples.asSequence()
}

/** Synchronous only in Studio: previews must not wait for produceState or a background coroutine. */
@Composable
private fun receiptScreenState(example: SampleExample, tab: Int = 0): GalleryUiState {
    val context = LocalContext.current
    return remember(context, example.id, tab) {
        val catalog = SampleCatalog { context.assets.open(it).bufferedReader().use { reader -> reader.readText() } }
        GalleryUiState(selected = example,
            document = runCatching { renderPreview(context, catalog.open(example)) }, tab = tab)
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
