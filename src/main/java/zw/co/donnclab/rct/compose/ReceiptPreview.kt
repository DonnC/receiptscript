package zw.co.donnclab.rct.compose

import zw.co.donnclab.rct.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.android.AndroidReceiptRenderer
import zw.co.donnclab.rct.android.RasterReceipt

/** A printer-independent preview using the renderer's bundled monospace font, in source-dot proportions. */
@Composable
fun ReceiptPreview(
    receipt: Receipt,
    modifier: Modifier = Modifier,
    renderer: AndroidReceiptRenderer? = null,
    onError: (Throwable) -> Unit = {}
) {
    val context = LocalContext.current
    val outputRenderer = renderer ?: remember(context) { AndroidReceiptRenderer(context) }
    val result = if (LocalInspectionMode.current) {
        remember(receipt, outputRenderer) { runCatching { outputRenderer.render(receipt) } }
    } else {
        val state by produceState<Result<RasterReceipt>?>(null, receipt, outputRenderer) {
            value = withContext(Dispatchers.IO) {
                try { Result.success(outputRenderer.render(receipt)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Result.failure(error) }
            }
        }
        state
    }
    val failure = result?.exceptionOrNull()
    LaunchedEffect(failure) { failure?.let(onError) }
    when {
        failure != null -> BasicText(stringResource(R.string.rct_preview_unavailable), modifier)
        result?.isSuccess == true -> ReceiptPreview(result.getOrThrow(), modifier)
    }
}

/** Pass the same RasterReceipt to preview, exporter and printer when exact output matching matters. */
@Composable
fun ReceiptPreview(receipt: RasterReceipt, modifier: Modifier = Modifier) {
    Column(modifier) {
        receipt.bands.forEachIndexed { index, bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.rct_preview_part, index + 1),
                modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None
            )
        }
    }
}
