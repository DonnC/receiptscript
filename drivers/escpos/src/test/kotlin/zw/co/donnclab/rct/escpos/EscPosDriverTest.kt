package zw.co.donnclab.rct.escpos

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test
import zw.co.donnclab.rct.*

class EscPosDriverTest {
    private fun receipt(vararg blocks: ReceiptBlock) = Receipt("test", null, blocks.toList())

    @Test fun `portable defaults initialize but never cut paper`() {
        val bytes = EscPosDriver().render(receipt(ReceiptBlock.Text("HELLO", RctAlign.CENTER, ReceiptTextStyle(bold = true, underline = true))))
        assertArrayEquals(byteArrayOf(27, 64), bytes.take(2).toByteArray())
        assertTrue(bytes.containsSequence(byteArrayOf(27, 97, 1)))
        assertTrue(bytes.containsSequence(byteArrayOf(27, 69, 1)))
        assertTrue(bytes.containsSequence(byteArrayOf(27, 45, 1)))
        assertFalse(bytes.containsSequence(byteArrayOf(29, 86)))
    }

    @Test fun `eighty millimetre profile cuts only when explicitly requested`() {
        val profile = EscPosProfile.Receipt80.copy(cutAfterPrint = true)
        assertTrue(EscPosDriver(profile).render(receipt()).containsSequence(byteArrayOf(29, 86, 1)))
        assertThrows(IllegalArgumentException::class.java) { EscPosProfile.Portable58.copy(cutAfterPrint = true) }
    }

    @Test fun `table wrapping preserves text and fits the configured columns`() {
        val table = ReceiptBlock.Table(listOf("ITEM", "QTY", "TOTAL"),
            listOf(listOf("Long product description", "2", "USD 123.45")), listOf(3f, 1f, 2f),
            listOf(RctColumnAlignment.START, RctColumnAlignment.CENTER, RctColumnAlignment.END),
            ReceiptTextStyle(), ReceiptTextStyle())
        val output = PlainTextDriver(columns = 32).render(receipt(table)).toString(Charsets.US_ASCII)
        assertTrue(output.lines().all { it.length <= 32 })
        assertTrue(output.contains("description"))
        assertTrue(output.contains("USD 123.45"))
    }

    @Test fun `font expansion reduces available text columns`() {
        val block = ReceiptBlock.Text("12345678901234567890123456789012", RctAlign.LEFT, ReceiptTextStyle(size = RctFontSize.BIG))
        val output = EscPosDriver().render(receipt(block))
        assertTrue(output.containsSequence(byteArrayOf(29, 33, 17)))
        assertTrue(output.toString(Charsets.US_ASCII).contains("1234567890123456\n"))
    }

    @Test fun `raster rows are bit packed and sliced without adding line feeds`() {
        val image = MonochromeImage.fromArgb(9, 3, IntArray(27) { if (it % 9 == 0 || it % 9 == 8) 0xff000000.toInt() else -1 })
        val bytes = EscPosDriver(EscPosProfile.Portable58.copy(rasterSliceHeight = 2), images = { image })
            .render(receipt(ReceiptBlock.Image("logo", null, null)))
        assertTrue(bytes.containsSequence(byteArrayOf(29, 118, 48, 0, 2, 0, 2, 0, 128.toByte(), 128.toByte(), 128.toByte(), 128.toByte())))
        assertTrue(bytes.containsSequence(byteArrayOf(29, 118, 48, 0, 2, 0, 1, 0, 128.toByte(), 128.toByte())))
    }

    @Test fun `QR and every barcode format produce real raster payloads`() {
        val values = mapOf(BarcodeFormat.CODE128 to "INV-123", BarcodeFormat.CODE39 to "RCT-123",
            BarcodeFormat.EAN13 to "5901234123457", BarcodeFormat.EAN8 to "96385074",
            BarcodeFormat.UPCA to "012345678905", BarcodeFormat.ITF to "12345678")
        values.forEach { (format, content) ->
            assertTrue(EscPosDriver().render(receipt(ReceiptBlock.Barcode(content, 300, 60, format))).containsSequence(byteArrayOf(29, 118, 48)))
        }
        val first = EscPosDriver().render(receipt(ReceiptBlock.QrCode("first", 180)))
        val second = EscPosDriver().render(receipt(ReceiptBlock.QrCode("second", 180)))
        assertTrue(first.containsSequence(byteArrayOf(29, 118, 48)))
        assertFalse(first.contentEquals(second))
    }

    @Test fun `invalid media unsupported glyphs and command injection fail before sending`() {
        var sent = false
        val printer = ReceiptPrinter(EscPosDriver(), PrinterConnection { sent = true })
        assertThrows(IllegalArgumentException::class.java) { printer.print(receipt(ReceiptBlock.Text("PAY\u001b@", RctAlign.LEFT, ReceiptTextStyle()))) }
        assertFalse(sent)
        assertThrows(IllegalArgumentException::class.java) { EscPosDriver().render(receipt(ReceiptBlock.Text("\u4e2d", RctAlign.LEFT, ReceiptTextStyle()))) }
        assertThrows(IllegalArgumentException::class.java) { EscPosDriver().render(receipt(ReceiptBlock.Barcode("BAD", 300, 60, BarcodeFormat.EAN13))) }
        assertThrows(IllegalArgumentException::class.java) { EscPosDriver().render(receipt(ReceiptBlock.Image("missing", null, null))) }
        assertThrows(IllegalArgumentException::class.java) { EscPosDriver().render(receipt(ReceiptBlock.QrCode("x", 500))) }
    }

    @Test fun `stream connections chunk data flush and leave borrowed stream open`() {
        val writes = mutableListOf<Int>()
        var flushed = false
        var closed = false
        val stream = object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) { writes += len }
            override fun flush() { flushed = true }
            override fun close() { closed = true }
        }
        OutputStreamConnection(stream, chunkSize = 4).send(ByteArray(10))
        assertEquals(listOf(4, 4, 2), writes)
        assertTrue(flushed)
        assertFalse(closed)
    }

    @Test fun `plain text media is rejected instead of silently dropped`() {
        assertThrows(IllegalArgumentException::class.java) { PlainTextDriver().render(receipt(ReceiptBlock.QrCode("x", 180))) }
    }

    @Test fun `one rendered job is sent unchanged`() {
        val out = ByteArrayOutputStream()
        val driver = EscPosDriver()
        val input = receipt(ReceiptBlock.KeyValue("TOTAL", "USD 25.25", ReceiptTextStyle()))
        ReceiptPrinter(driver, OutputStreamConnection(out)).print(input)
        assertArrayEquals(driver.render(input), out.toByteArray())
    }

    @Test fun `raster QR can be reconstructed and decoded`() {
        val bytes = EscPosDriver().render(receipt(ReceiptBlock.QrCode("receipt:INV-001", 180)))
        val marker = byteArrayOf(29, 118, 48, 0)
        var position = (0..bytes.size - marker.size).first { i -> marker.indices.all { bytes[i + it] == marker[it] } }
        val pixels = mutableListOf<Int>()
        var width = 0
        while (position + 8 <= bytes.size && marker.indices.all { bytes[position + it] == marker[it] }) {
            val stride = (bytes[position + 4].toInt() and 255) + ((bytes[position + 5].toInt() and 255) shl 8)
            val height = (bytes[position + 6].toInt() and 255) + ((bytes[position + 7].toInt() and 255) shl 8)
            width = stride * 8
            for (i in 0 until stride * height) for (bit in 0..7) {
                pixels += if (bytes[position + 8 + i].toInt() and (128 ushr bit) != 0) 0xff000000.toInt() else -1
            }
            position += 8 + stride * height
        }
        val source = com.google.zxing.RGBLuminanceSource(width, pixels.size / width, pixels.toIntArray())
        val bitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        assertEquals("receipt:INV-001", com.google.zxing.MultiFormatReader().decode(bitmap).text)
    }

    @Test fun `stream failure propagates and is never retried`() {
        var writes = 0
        val failure = java.io.IOException("disconnected")
        val stream = object : OutputStream() {
            override fun write(b: Int) = Unit
            override fun write(b: ByteArray, off: Int, len: Int) { writes++; throw failure }
        }
        assertSame(failure, assertThrows(java.io.IOException::class.java) {
            OutputStreamConnection(stream, 4).send(ByteArray(10))
        })
        assertEquals(1, writes)
    }

    @Test fun `transparent pixels remain white and invalid profiles fail early`() {
        val image = MonochromeImage.fromArgb(2, 1, intArrayOf(0, 0xff000000.toInt()))
        assertFalse(image.isBlack(0, 0))
        assertTrue(image.isBlack(1, 0))
        assertThrows(IllegalArgumentException::class.java) { EscPosProfile.Portable58.copy(printableWidthDots = 385) }
        assertThrows(IllegalArgumentException::class.java) { EscPosProfile.Portable58.copy(charset = Charsets.UTF_8) }
    }
}

private fun ByteArray.containsSequence(needle: ByteArray): Boolean =
    (0..(size - needle.size)).any { start -> needle.indices.all { this[start + it] == needle[it] } }
