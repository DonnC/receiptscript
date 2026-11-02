package io.github.donnclab.rct.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.compose.ReceiptPreview

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
