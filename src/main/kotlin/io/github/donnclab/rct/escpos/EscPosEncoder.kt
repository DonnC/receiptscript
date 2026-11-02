package io.github.donnclab.rct.escpos

import com.google.zxing.BarcodeFormat as ZxingFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import kotlin.math.roundToInt
import io.github.donnclab.rct.*

/**
 * Builds a complete ESC/POS job in memory without touching a device, so template, encoding and
 * image errors surface before the first byte is sent.
 *
 * On Android, use `EscPosDriver`, which wraps this encoder and a [PrinterConnection]. Use the
 * encoder directly on the JVM or when you manage transport yourself:
 *
 * ```kotlin
 * connection.send(EscPosEncoder(EscPosProfile.Portable58).encode(document))
 * ```
 *
 * @param images resolves `image` references for [encode]; unresolved images fail the job.
 */
public class EscPosEncoder(
    private val profile: EscPosProfile = EscPosProfile.Portable58,
    private val images: (String) -> MonochromeImage? = { null }
) {
    /**
     * Encodes pre-rendered raster bands (for example the bitmaps of an Android `Receipt`) exactly as
     * given: no scaling, gaps or native text. Output matches the on-screen preview pixel for pixel.
     */
    public fun encodeRaster(bands: List<MonochromeImage>): ByteArray {
        require(bands.isNotEmpty()) { "Raster receipt has no bands" }
        val width = bands.first().width
        require(width <= profile.printableWidthDots && bands.all { it.width == width }) { "Raster bands must share a printable width" }
        require(bands.sumOf { it.height.toLong() } * width <= 16_777_216) { "Raster receipt exceeds pixel budget" }
        val job = Job(null)
        job.command(27, 64)
        bands.forEach(job::raster)
        job.command(27, 100, profile.feedAfterPrint)
        if (profile.cutAfterPrint) job.command(29, 86, 1)
        return job.bytes.toByteArray()
    }

    /**
     * Encodes [receipt] with the printer's built-in fonts. Faster and smaller than raster output;
     * images, QR codes and barcodes are still sent as raster strips.
     */
    public fun encode(receipt: ReceiptDocument): ByteArray {
        val requestedColumns = receipt.meta?.width
        require(requestedColumns == null || requestedColumns > 0) { "Receipt width must be positive" }
        val job = Job(requestedColumns)
        job.command(27, 64)
        job.command(27, 116, profile.codeTable)
        receipt.blocks.forEach { job.block(it) }
        job.command(27, 100, profile.feedAfterPrint)
        if (profile.cutAfterPrint) job.command(29, 86, 1)
        return job.bytes.toByteArray()
    }

    private data class Font(val selection: Int, val scale: Int, val columns: Int)

    private fun font(style: ReceiptTextStyle, requestedColumns: Int?): Font {
        val small = style.font == null && style.size == ReceiptFontSize.SMALL && profile.supportsFontB
        val scale = style.font?.let {
            require(it.isFinite() && it > 0) { "Font size must be positive" }
            (it / profile.fontAHeightDots).roundToInt().coerceIn(1, 8)
        } ?: if (style.size == ReceiptFontSize.BIG) 2 else 1
        val glyph = if (small) profile.fontBWidthDots else profile.fontAWidthDots
        val physicalColumns = (profile.printableWidthDots / (glyph * scale)).coerceAtLeast(1)
        return Font(if (small) 1 else 0, scale, minOf(physicalColumns, requestedColumns ?: physicalColumns))
    }

    private inner class Job(private val requestedColumns: Int?) {
        val bytes = ByteArrayOutputStream()
        private fun font(style: ReceiptTextStyle) = this@EscPosEncoder.font(style, requestedColumns)
        fun command(vararg values: Int) { values.forEach(bytes::write) }

        private fun applyStyle(style: ReceiptTextStyle, alignment: ReceiptAlign = ReceiptAlign.LEFT): Font {
            val font = font(style)
            command(27, 97, alignment.ordinal)
            command(27, 77, font.selection)
            command(29, 33, (font.scale - 1) * 17)
            command(27, 69, if (style.bold) 1 else 0)
            command(27, 45, if (style.underline) 1 else 0)
            return font
        }

        private fun line(value: String) {
            require(value.none { it.code < 32 || it.code in 127..159 }) { "Text contains a printer control character" }
            val encoder = profile.charset.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val buffer = try { encoder.encode(CharBuffer.wrap(value)) } catch (error: java.nio.charset.CharacterCodingException) {
                throw IllegalArgumentException("Text cannot be encoded as ${profile.charset.name()}; select a matching code page or use a raster adapter", error)
            }
            val encoded = ByteArray(buffer.remaining()).also(buffer::get)
            bytes.write(encoded)
            command(10)
        }

        fun block(block: ReceiptBlock) {
            when (block) {
                is ReceiptBlock.Text -> {
                    val font = applyStyle(block.style, block.align)
                    TextLayout.wrap(block.text, font.columns).forEach(::line)
                }
                is ReceiptBlock.KeyValue -> {
                    val font = applyStyle(block.style)
                    TextLayout.keyValue(block.key, block.value, font.columns).forEach(::line)
                }
                is ReceiptBlock.Table -> {
                    // Native table columns need the same glyph metrics in header and body.
                    require(block.headers.isEmpty() || font(block.headerStyle) == font(block.rowStyle)) {
                        "Native table headers and rows must use matching font metrics"
                    }
                    val columns = font(block.rowStyle).columns
                    if (block.headers.isNotEmpty()) {
                        applyStyle(block.headerStyle)
                        TextLayout.table(listOf(block.headers), block.weights, block.alignments, columns).forEach(::line)
                    }
                    applyStyle(block.rowStyle)
                    TextLayout.table(block.rows, block.weights, block.alignments, columns).forEach(::line)
                }
                is ReceiptBlock.Row -> {
                    val font = applyStyle(ReceiptTextStyle())
                    TextLayout.table(listOf(block.columns), null, null, font.columns).forEach(::line)
                }
                is ReceiptBlock.Divider -> {
                    val font = applyStyle(ReceiptTextStyle(), ReceiptAlign.CENTER)
                    val count = block.length.coerceIn(0, font.columns)
                    val pattern = block.pattern.ifBlank { "-" }
                    line(buildString { while (length < count) append(pattern.take(count - length)) })
                }
                is ReceiptBlock.Spacer -> {
                    require(block.lines in 0..10000)
                    applyStyle(ReceiptTextStyle())
                    var remaining = block.lines
                    while (remaining > 0) { val feed = minOf(remaining, 255); command(27, 100, feed); remaining -= feed }
                }
                is ReceiptBlock.Image -> {
                    val image = requireNotNull(images(block.source)) { "Unresolved image: ${block.source}" }
                    raster(image.fit(block.width, block.height, profile.printableWidthDots))
                }
                is ReceiptBlock.QrCode -> raster(symbol(block.content, ZxingFormat.QR_CODE, block.size, block.size))
                is ReceiptBlock.Barcode -> raster(symbol(block.content, when (block.format) {
                    ReceiptBarcodeFormat.CODE128 -> ZxingFormat.CODE_128
                    ReceiptBarcodeFormat.CODE39 -> ZxingFormat.CODE_39
                    ReceiptBarcodeFormat.EAN13 -> ZxingFormat.EAN_13
                    ReceiptBarcodeFormat.EAN8 -> ZxingFormat.EAN_8
                    ReceiptBarcodeFormat.UPCA -> ZxingFormat.UPC_A
                    ReceiptBarcodeFormat.ITF -> ZxingFormat.ITF
                }, block.width, block.height))
            }
        }

        fun raster(image: MonochromeImage) {
            require(profile.supportsRaster) { "This profile does not support raster images or codes" }
            require(image.width <= profile.printableWidthDots)
            applyStyle(ReceiptTextStyle(), ReceiptAlign.CENTER)
            val stride = (image.width + 7) / 8
            var top = 0
            while (top < image.height) {
                val rows = minOf(profile.rasterSliceHeight, image.height - top)
                command(29, 118, 48, 0, stride and 255, stride ushr 8, rows and 255, rows ushr 8)
                for (y in top until top + rows) for (xByte in 0 until stride) {
                    var bits = 0
                    for (bit in 0..7) {
                        val x = xByte * 8 + bit
                        if (x < image.width && image.isBlack(x, y)) bits = bits or (128 ushr bit)
                    }
                    command(bits)
                }
                // Raster commands advance by their own height. Extra LF here introduces slice seams.
                top += rows
            }
        }
    }

    private fun symbol(content: String, format: ZxingFormat, width: Int, height: Int): MonochromeImage {
        require(width in 1..profile.printableWidthDots && height in 1..4096) { "Code dimensions exceed printable area" }
        require(content.isNotEmpty() && content.length <= 4096) { "Code content is empty or too long" }
        val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to if (format == ZxingFormat.QR_CODE) 4 else 10)
        val matrix = MultiFormatWriter().encode(content, format, width, height, hints)
        require(matrix.width <= profile.printableWidthDots) { "Code cannot fit without losing modules or quiet zones" }
        return MonochromeImage.fromPixels(matrix.width, matrix.height) { x, y -> matrix[x, y] }
    }
}
