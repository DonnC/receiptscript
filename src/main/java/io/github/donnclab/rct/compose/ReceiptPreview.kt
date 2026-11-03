package io.github.donnclab.rct.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.donnclab.rct.R
import io.github.donnclab.rct.Receipt

/**
 * Shows a rendered [Receipt]'s layout, scaled to the available width with smooth screen text.
 * Use [ReceiptPreviewMode.THERMAL] to inspect the exact quantized printer pixels instead.
 *
 * ```kotlin
 * val receipt by produceState<Receipt?>(null, sale) { value = rct.render("sale", sale) }
 * receipt?.let { ReceiptPreview(it, Modifier.width(280.dp)) }
 * ```
 *
 * The composable only draws; render the receipt with `ReceiptScript.render` first. No printer is needed.
 */
@Composable
public fun ReceiptPreview(receipt: Receipt, modifier: Modifier = Modifier, mode: ReceiptPreviewMode = ReceiptPreviewMode.SMOOTH) {
    val bands = remember(receipt) { receipt.bands.map { it.asImageBitmap() } }
    val offsets = remember(receipt) { receipt.bands.runningFold(0) { top, band -> top + band.height } }
    Column(modifier) {
        bands.forEachIndexed { index, band ->
            val display = receipt.displayList
            val description = stringResource(R.string.rct_preview_part, index + 1)
            if (mode == ReceiptPreviewMode.SMOOTH && display != null) {
                val top = offsets[index]
                Canvas(Modifier.fillMaxWidth().aspectRatio(band.width.toFloat() / band.height).semantics { contentDescription = description }) {
                    drawIntoCanvas { target ->
                        val canvas = target.nativeCanvas
                        canvas.save()
                        val scale = size.width / receipt.width
                        canvas.scale(scale, scale)
                        canvas.clipRect(0, 0, receipt.width, band.height)
                        canvas.drawColor(android.graphics.Color.WHITE)
                        display.draw(canvas, top, band.height)
                        canvas.restore()
                    }
                }
            } else Image(
                bitmap = band,
                contentDescription = description,
                modifier = Modifier.fillMaxWidth().aspectRatio(band.width.toFloat() / band.height),
                contentScale = ContentScale.FillBounds,
                filterQuality = if (mode == ReceiptPreviewMode.THERMAL) FilterQuality.None else FilterQuality.High
            )
        }
    }
}

/** Smooth screen text, or exact quantized printer pixels for hardware diagnostics. */
public enum class ReceiptPreviewMode { SMOOTH, THERMAL }
