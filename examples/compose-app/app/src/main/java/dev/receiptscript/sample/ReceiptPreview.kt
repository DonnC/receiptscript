package dev.receiptscript.sample

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import zw.co.donnclab.rct.android.RasterReceipt

/** Decoration is screen-only. The pixels inside the paper are the actual print job. */
@Composable
fun ReceiptPreview(receipt: RasterReceipt, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = if (receipt.width > 384) 336.dp else 280.dp).fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
            receipt.bands.forEachIndexed { index, bitmap ->
                Image(bitmap.asImageBitmap(), "Receipt preview, part ${index + 1}",
                    Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
                    contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
            }
        }
    }
}
