package io.github.donnclab.rct.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.ReceiptScript
import io.github.donnclab.rct.compose.ReceiptPreview
import io.github.donnclab.rct.tooling.RctStatementForm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** Resolves through the host's existing engine, never a second image loader. */
@Composable
internal fun StudioImageSample(engine: ReceiptScript, form: RctStatementForm, values: Map<String, String>, data: Any?, args: Map<String, Any?>) {
    var sample by remember { mutableStateOf<Receipt?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    LaunchedEffect(engine, form, values, data, args) {
        sample = null
        unavailable = false
        delay(200)
        try { sample = engine.renderSource("receipt \"studio_image\"\n${form.write(values)}", data, args) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { unavailable = true }
    }
    OutlinedCard(shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(140.dp).padding(8.dp), contentAlignment = Alignment.Center) {
            sample?.let { receipt ->
                val width = (124f * receipt.width / receipt.height.coerceAtLeast(1)).coerceAtMost(240f).dp
                ReceiptPreview(receipt, Modifier.width(width))
            }
                ?: Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioIcon(StudioSymbol.IMAGE, modifier = Modifier.size(28.dp))
                    Text(if (unavailable) "Image preview unavailable" else "Loading image...", style = MaterialTheme.typography.bodySmall)
                }
        }
    }
}
