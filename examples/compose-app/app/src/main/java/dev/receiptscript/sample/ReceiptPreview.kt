package dev.receiptscript.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import zw.co.donnclab.rct.*

/** Displays the resolved Receipt, not a second template. Native device fonts can still differ. */
@Composable
fun ReceiptPreview(receipt: Receipt, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.background(Color.White).padding(18.dp)) {
        val paperWidth = maxWidth.value
        val columns = receipt.meta?.width ?: 32
        val base = paperWidth / (columns * 0.6f)
        val metaFont = receipt.meta?.font ?: 24f
        val paperDots = if (receipt.meta?.paper == "80mm") 576f else 384f
        fun textStyle(style: ReceiptTextStyle = ReceiptTextStyle()): TextStyle {
            val font = style.font ?: when (style.size) {
                RctFontSize.SMALL -> metaFont * 11f / 13f
                RctFontSize.BIG -> metaFont * 16f / 13f
                RctFontSize.MIDDLE -> metaFont
            }
            return TextStyle(
                color = Color.Black, fontSize = (base * font / metaFont).sp,
                lineHeight = (base * font / metaFont * 1.45f).sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                textDecoration = if (style.underline) TextDecoration.Underline else null
            )
        }
        @Composable fun cells(values: List<String>, weights: List<Float>?, alignments: List<RctColumnAlignment>?, style: ReceiptTextStyle) {
            Row(Modifier.fillMaxWidth()) {
                values.forEachIndexed { index, value ->
                    Text(value, Modifier.weight(weights?.get(index) ?: 1f).padding(horizontal = 2.dp),
                        style = textStyle(style), textAlign = when (alignments?.get(index)) {
                            RctColumnAlignment.START -> TextAlign.Start
                            RctColumnAlignment.END -> TextAlign.End
                            else -> TextAlign.Center
                        })
                }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            receipt.blocks.forEach { block ->
                when (block) {
                    is ReceiptBlock.Text -> Text(block.text, Modifier.fillMaxWidth(), style = textStyle(block.style),
                        textAlign = when (block.align) {
                            RctAlign.LEFT -> TextAlign.Start
                            RctAlign.RIGHT -> TextAlign.End
                            RctAlign.CENTER -> TextAlign.Center
                        })
                    is ReceiptBlock.KeyValue -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(block.key, Modifier.weight(1f), style = textStyle(block.style))
                        Text(block.value, Modifier.weight(1f), style = textStyle(block.style), textAlign = TextAlign.End)
                    }
                    is ReceiptBlock.Spacer -> Spacer(Modifier.height((base * 1.45f * block.lines).dp))
                    is ReceiptBlock.Divider -> {
                        val fraction = (block.length.toFloat() / columns).coerceIn(0f, 1f)
                        if (block.pattern == "_") Box(Modifier.padding(vertical = 5.dp).fillMaxWidth(fraction).height(1.dp).background(Color.Black))
                        else Text(buildString { repeat(block.length) { append(block.pattern) } }.take(block.length),
                            style = textStyle(), softWrap = false)
                    }
                    is ReceiptBlock.Row -> cells(block.columns, null, null, ReceiptTextStyle())
                    is ReceiptBlock.Table -> {
                        if (block.headers.isNotEmpty()) {
                            cells(block.headers, block.weights, block.alignments, block.headerStyle)
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black))
                        }
                        block.rows.forEach { cells(it, block.weights, block.alignments, block.rowStyle) }
                    }
                    is ReceiptBlock.Image -> {
                        val bitmap = remember(block.source) {
                            context.assets.open("receipts/${block.source.removePrefix("asset:")}").use {
                                requireNotNull(BitmapFactory.decodeStream(it)) { "Unreadable image: ${block.source}" }
                            }.asImageBitmap()
                        }
                        Image(bitmap, "Merchant logo", Modifier
                            .width((paperWidth * (block.width ?: 80) / paperDots).dp)
                            .height((paperWidth * (block.height ?: 80) / paperDots).dp), contentScale = ContentScale.Fit)
                    }
                    is ReceiptBlock.QrCode -> {
                        val bitmap = remember(block) { encodeCode(block.content, ZxingFormat.QR_CODE, block.size, block.size).asImageBitmap() }
                        Image(bitmap, "QR: ${block.content}", Modifier.size((paperWidth * block.size / paperDots).coerceAtMost(paperWidth).dp))
                    }
                    is ReceiptBlock.Barcode -> {
                        val bitmap = remember(block) { encodeCode(block.content, ZxingFormat.valueOf(when (block.format) {
                            BarcodeFormat.CODE128 -> "CODE_128"
                            BarcodeFormat.CODE39 -> "CODE_39"
                            BarcodeFormat.EAN13 -> "EAN_13"
                            BarcodeFormat.EAN8 -> "EAN_8"
                            BarcodeFormat.UPCA -> "UPC_A"
                            BarcodeFormat.ITF -> "ITF"
                        }), block.width, block.height).asImageBitmap() }
                        Image(bitmap, "Barcode: ${block.content}", Modifier
                            .width((paperWidth * block.width / paperDots).coerceAtMost(paperWidth).dp)
                            .height((paperWidth * block.height / paperDots).dp), contentScale = ContentScale.FillBounds)
                    }
                }
            }
        }
    }
}

private fun encodeCode(content: String, format: ZxingFormat, width: Int, height: Int): Bitmap {
    val matrix = MultiFormatWriter().encode(content, format, width, height, mapOf(EncodeHintType.MARGIN to 4))
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}
