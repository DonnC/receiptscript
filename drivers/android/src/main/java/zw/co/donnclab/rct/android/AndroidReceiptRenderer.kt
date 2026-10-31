package zw.co.donnclab.rct.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import zw.co.donnclab.rct.*
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Pixel geometry belongs to the output adapter, not the template or the Android display density. */
data class RasterOptions(
    val width: Int = 384,
    val padding: Int = 16,
    val fontSize: Float = 24f,
    val lineSpacing: Float = 1.18f,
    val bandHeight: Int = 1024,
    val maxHeight: Int = 32768,
    val maxPixels: Long = 16_777_216
) {
    init {
        require(width in 64..4096 && padding >= 0 && padding * 2 < width)
        require(fontSize.isFinite() && fontSize in 1f..256f)
        require(lineSpacing.isFinite() && lineSpacing in 1f..3f)
        require(bandHeight in 1..8192 && maxHeight in 1..65536 && maxPixels in 1..67_108_864)
    }
}

/** Immutable monochrome bands in printer dots. Preview and print must use this same result.
 * Do not recycle a band while a preview or print job is using it. No device handles are retained.
 */
class RasterReceipt internal constructor(val width: Int, bands: List<Bitmap>) {
    val bands: List<Bitmap> = bands.toList()
    val height: Int = bands.sumOf { it.height }
}

/**
 * Lays out a resolved Receipt once, then draws it into bounded raster bands. This adapter does not
 * depend on Compose, a printer SDK, Bluetooth or network access. Image ownership stays with the
 * resolver. Work is synchronous: call on a worker dispatcher, not the Android UI thread.
 */
class AndroidReceiptRenderer(
    context: Context,
    private val options: RasterOptions = RasterOptions(),
    private val images: (String) -> Bitmap? = { null },
    typeface: Typeface? = null
) : ReceiptAdapter<RasterReceipt> {
    private val face = typeface ?: context.resources.getFont(R.font.receipt_mono)

    override fun render(receipt: Receipt): RasterReceipt = Job(receipt).render()

    private data class Mark(val top: Int, val bottom: Int, val draw: (Canvas) -> Unit)

    private inner class Job(private val receipt: Receipt) {
        private val marks = mutableListOf<Mark>()
        private val contentWidth = options.width - options.padding * 2
        private var y = options.padding
        private val normal = ReceiptTextStyle(font = receipt.meta?.font ?: options.fontSize)

        private fun paint(style: ReceiptTextStyle): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(face, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
            textSize = style.font ?: when (style.size) {
                RctFontSize.SMALL -> options.fontSize * 0.85f
                RctFontSize.BIG -> options.fontSize * 1.25f
                RctFontSize.MIDDLE -> options.fontSize
            }
            require(textSize.isFinite() && textSize in 1f..256f) { "Raster font size must be between 1 and 256 dots" }
            isUnderlineText = style.underline
        }

        private fun lineHeight(style: ReceiptTextStyle = normal) = ceil(paint(style).textSize * options.lineSpacing).toInt()

        private fun layout(text: String, style: ReceiptTextStyle, width: Int, align: Layout.Alignment): StaticLayout {
            require(width > 0)
            return StaticLayout.Builder.obtain(text, 0, text.length, paint(style), width)
                .setAlignment(align).setIncludePad(false).setLineSpacing(0f, options.lineSpacing)
                .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
        }

        private fun text(text: String, style: ReceiptTextStyle, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val layout = layout(text, style, contentWidth, align)
            val top = y
            marks += Mark(top, top + layout.height) { canvas ->
                canvas.save(); canvas.translate(options.padding.toFloat(), top.toFloat()); layout.draw(canvas); canvas.restore()
            }
            advance(maxOf(layout.height, lineHeight(style)))
        }

        private fun advance(amount: Int) {
            require(amount >= 0 && amount <= options.maxHeight - y) { "Receipt exceeds maximum raster height" }
            y += amount
            require((y.toLong() + options.padding) * options.width <= options.maxPixels) { "Receipt exceeds raster pixel budget" }
        }

        private fun keyValue(block: ReceiptBlock.KeyValue) {
            val p = paint(block.style)
            val valueWidth = ceil(p.measureText(block.value)).toInt()
            val keyWidth = ceil(p.measureText(block.key)).toInt()
            if ('\n' in block.key || '\n' in block.value || keyWidth + valueWidth + p.measureText(" ") > contentWidth) {
                text(block.key, block.style)
                text(block.value, block.style, Layout.Alignment.ALIGN_OPPOSITE)
            } else {
                val top = y
                val left = layout(block.key, block.style, contentWidth, Layout.Alignment.ALIGN_NORMAL)
                val right = layout(block.value, block.style, contentWidth, Layout.Alignment.ALIGN_OPPOSITE)
                val height = maxOf(left.height, right.height, lineHeight(block.style))
                marks += Mark(top, top + height) { canvas ->
                    canvas.save(); canvas.translate(options.padding.toFloat(), top.toFloat())
                    left.draw(canvas); right.draw(canvas); canvas.restore()
                }
                advance(height)
            }
        }

        private fun row(cells: List<String>, weights: List<Float>?, alignments: List<RctColumnAlignment>?, style: ReceiptTextStyle) {
            require(cells.isNotEmpty()) { "Raster table rows must have columns" }
            require(weights == null || weights.size == cells.size && weights.all { it.isFinite() && it > 0 })
            require(alignments == null || alignments.size == cells.size)
            val effective = weights ?: List(cells.size) { 1f }
            val total = effective.sum().also { require(it.isFinite()) }
            val gap = 6
            val available = contentWidth - gap * (cells.size - 1)
            require(available >= cells.size) { "Too many columns for raster width" }
            val widths = effective.map { (available * it / total).toInt().coerceAtLeast(1) }.toMutableList()
            widths[widths.lastIndex] += available - widths.sum()
            val minimum = ceil(paint(style).measureText("W")).toInt()
            require(widths.all { it >= minimum }) { "Too many columns for the selected raster font and width" }
            val layouts = cells.mapIndexed { index, cell -> layout(cell, style, widths[index], when (alignments?.get(index)) {
                RctColumnAlignment.START -> Layout.Alignment.ALIGN_NORMAL
                RctColumnAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            }) }
            val height = maxOf(layouts.maxOf { it.height }, lineHeight(style))
            val top = y
            marks += Mark(top, top + height) { canvas ->
                var x = options.padding
                layouts.forEachIndexed { index, cell ->
                    canvas.save(); canvas.translate(x.toFloat(), top.toFloat()); cell.draw(canvas); canvas.restore()
                    x += widths[index] + gap
                }
            }
            advance(height)
        }

        private fun image(bitmap: Bitmap, width: Int?, height: Int?) {
            require(!bitmap.isRecycled)
            val boxWidth = width ?: bitmap.width
            val boxHeight = height ?: if (width != null) (bitmap.height.toDouble() * width / bitmap.width).roundToInt() else bitmap.height
            require(boxWidth > 0 && boxHeight > 0)
            val scale = minOf(boxWidth.toDouble() / bitmap.width, boxHeight.toDouble() / bitmap.height, contentWidth.toDouble() / bitmap.width)
            val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
            val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
            val top = y
            val left = (options.width - w) / 2
            val destination = Rect(left, top, left + w, top + h)
            marks += Mark(top, top + h) { canvas -> canvas.drawBitmap(bitmap, null, destination, Paint()) }
            advance(h)
        }

        private fun code(content: String, format: ZxingFormat, width: Int, height: Int) {
            require(width in 1..contentWidth && height in 1..4096 && content.isNotEmpty() && content.length <= 4096) {
                "Code exceeds raster content dimensions"
            }
            val matrix = MultiFormatWriter().encode(content, format, width, height,
                mapOf(EncodeHintType.MARGIN to if (format == ZxingFormat.QR_CODE) 4 else 10, EncodeHintType.CHARACTER_SET to "UTF-8"))
            require(matrix.width <= contentWidth) { "Barcode cannot fit without losing modules" }
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) Color.BLACK else Color.WHITE },
                0, matrix.width, 0, 0, matrix.width, matrix.height)
            image(bitmap, bitmap.width, bitmap.height)
        }

        fun render(): RasterReceipt {
            val requestedWidth = receipt.meta?.width
            require(requestedWidth == null || requestedWidth > 0) { "Receipt width must be positive" }
            receipt.blocks.forEach { block -> when (block) {
                is ReceiptBlock.Text -> text(block.text, block.style, when (block.align) {
                    RctAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                    RctAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                    RctAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                })
                is ReceiptBlock.KeyValue -> keyValue(block)
                is ReceiptBlock.Row -> row(block.columns, null, null, normal)
                is ReceiptBlock.Table -> {
                    val count = block.headers.size.takeIf { it > 0 } ?: block.rows.firstOrNull()?.size ?: 0
                    require(block.rows.all { it.size == count }) { "Table columns must match" }
                    if (block.headers.isNotEmpty()) row(block.headers, block.weights, block.alignments, block.headerStyle)
                    block.rows.forEach { row(it, block.weights, block.alignments, block.rowStyle) }
                }
                is ReceiptBlock.Spacer -> {
                    require(block.lines in 0..10000)
                    advance(lineHeight() * block.lines)
                }
                is ReceiptBlock.Divider -> {
                    val columns = minOf(receipt.meta?.width ?: 34, (contentWidth / paint(normal).measureText("0")).toInt())
                    val count = block.length.coerceIn(0, columns)
                    if (block.pattern == "_") {
                        val width = count * paint(normal).measureText("0")
                        val top = y
                        val height = lineHeight()
                        marks += Mark(top, top + height) { canvas ->
                            canvas.drawLine((options.width - width) / 2, top + height / 2f,
                                (options.width + width) / 2, top + height / 2f, Paint().apply { color = Color.BLACK; strokeWidth = 1f })
                        }
                        advance(height)
                    } else text(buildString { while (length < count) append(block.pattern.ifBlank { "-" }) }.take(count), normal, Layout.Alignment.ALIGN_CENTER)
                }
                is ReceiptBlock.Image -> image(requireNotNull(images(block.source)) { "Unresolved image: ${block.source}" }, block.width, block.height)
                is ReceiptBlock.QrCode -> code(block.content, ZxingFormat.QR_CODE, block.size, block.size)
                is ReceiptBlock.Barcode -> code(block.content, when (block.format) {
                    BarcodeFormat.CODE128 -> ZxingFormat.CODE_128
                    BarcodeFormat.CODE39 -> ZxingFormat.CODE_39
                    BarcodeFormat.EAN13 -> ZxingFormat.EAN_13
                    BarcodeFormat.EAN8 -> ZxingFormat.EAN_8
                    BarcodeFormat.UPCA -> ZxingFormat.UPC_A
                    BarcodeFormat.ITF -> ZxingFormat.ITF
                }, block.width, block.height)
            } }
            advance(options.padding)
            val bands = (0 until y step options.bandHeight).map { top ->
                val height = minOf(options.bandHeight, y - top)
                val bitmap = Bitmap.createBitmap(options.width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                canvas.translate(0f, -top.toFloat())
                marks.filter { it.bottom > top && it.top < top + height }.forEach { it.draw(canvas) }
                // Quantize before previewing: no anti-alias or colour conversion is deferred to printing.
                val pixels = IntArray(options.width * height)
                bitmap.getPixels(pixels, 0, options.width, 0, 0, options.width, height)
                pixels.indices.forEach { index ->
                    val p = pixels[index]
                    val luminance = 299 * (p ushr 16 and 255) + 587 * (p ushr 8 and 255) + 114 * (p and 255)
                    pixels[index] = if (luminance < 160000) Color.BLACK else Color.WHITE
                }
                bitmap.setPixels(pixels, 0, options.width, 0, 0, options.width, height)
                requireNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)).also { bitmap.recycle() }
            }
            return RasterReceipt(options.width, bands)
        }
    }
}
