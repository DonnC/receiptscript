package io.github.donnclab.rct.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import io.github.donnclab.rct.R
import io.github.donnclab.rct.Receipt

/**
 * Shows a rendered [Receipt] exactly as it will print, scaled to the available width.
 *
 * ```kotlin
 * val receipt by produceState<Receipt?>(null, sale) { value = rct.render("sale", sale) }
 * receipt?.let { ReceiptPreview(it, Modifier.width(280.dp)) }
 * ```
 *
 * The composable only draws; render the receipt with `ReceiptScript.render` first. No printer is needed.
 */
@Composable
public fun ReceiptPreview(receipt: Receipt, modifier: Modifier = Modifier) {
    val bands = remember(receipt) { receipt.bands.map { it.asImageBitmap() } }
    Column(modifier) {
        bands.forEachIndexed { index, band ->
            Image(
                bitmap = band,
                contentDescription = stringResource(R.string.rct_preview_part, index + 1),
                modifier = Modifier.fillMaxWidth().aspectRatio(band.width.toFloat() / band.height),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None
            )
        }
    }
}
