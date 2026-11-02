package io.github.donnclab.rct.escpos

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Test
import io.github.donnclab.rct.*

class EscPosEncoderTest {
    @org.junit.Test
    fun `raster receipts preserve width and finish only once`() {
        val driver = EscPosEncoder()
        val bands = listOf(MonochromeImage.fromPixels(384, 2) { _, _ -> true },
            MonochromeImage.fromPixels(384, 3) { _, _ -> false })
        val bytes = driver.encodeRaster(bands)
        org.junit.Assert.assertEquals(2, bytes.toList().windowed(2).count { it == listOf(29.toByte(), 118.toByte()) })
        org.junit.Assert.assertEquals(1, bytes.toList().windowed(2).count { it == listOf(27.toByte(), 64.toByte()) })
        val firstHeader = listOf(29, 118, 48, 0, 48, 0, 2, 0).map(Int::toByte)
        val secondHeader = listOf(29, 118, 48, 0, 48, 0, 3, 0).map(Int::toByte)
        val first = bytes.toList().windowed(8).indexOf(firstHeader) + 8
        val second = bytes.toList().windowed(8).indexOf(secondHeader) + 8
        org.junit.Assert.assertArrayEquals(ByteArray(96) { 255.toByte() }, bytes.copyOfRange(first, first + 96))
        org.junit.Assert.assertArrayEquals(ByteArray(144), bytes.copyOfRange(second, second + 144))
        org.junit.Assert.assertEquals(0, bytes.count { it == 10.toByte() })
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            driver.encodeRaster(listOf(bands.first(), MonochromeImage.fromPixels(200, 2) { _, _ -> false }))
        }
    }
    private fun receipt(vararg blocks: ReceiptBlock) = ReceiptDocument("test", null, blocks.toList())

    @Test fun `receipt metadata limits native text and table columns`() {
        val input = RctCompiler().compile("receipt \"narrow\"\nmeta:\n  width: 16\nleft \"12345678901234567890\"\ntable:\n  headers: \"A\" \"B\"\n  row \"Long product description\" \"12\"").render(null)
        val bytes = EscPosEncoder().encode(input).toString(Charsets.US_ASCII)
        assertTrue(bytes.contains("1234567890123456\n"))
        assertTrue(bytes.contains("7890\n"))
        val plain = PlainTextEncoder(columns = 32).encode(input).toString(Charsets.US_ASCII)
        assertTrue(plain.lines().all { it.length <= 16 })
    }

    @Test fun `metadata never widens the physical printer`() {
        val input = ReceiptDocument("wide", ReceiptMeta(width = 80), listOf(ReceiptBlock.Text("X".repeat(40), ReceiptAlign.LEFT, ReceiptTextStyle())))
        assertTrue(EscPosEncoder().encode(input).toString(Charsets.US_ASCII).contains("X".repeat(32) + "\n"))
        assertThrows(IllegalArgumentException::class.java) { EscPosEncoder().encode(input.copy(meta = ReceiptMeta(width = 0))) }
    }

    @Test fun `portable defaults initialize but never cut paper`() {
        val bytes = EscPosEncoder().encode(receipt(ReceiptBlock.Text("HELLO", ReceiptAlign.CENTER, ReceiptTextStyle(bold = true, underline = true))))
        assertArrayEquals(byteArrayOf(27, 64), bytes.take(2).toByteArray())
        assertTrue(bytes.containsSequence(byteArrayOf(27, 97, 1)))
        assertTrue(bytes.containsSequence(byteArrayOf(27, 69, 1)))
        assertTrue(bytes.containsSequence(byteArrayOf(27, 45, 1)))
        assertFalse(bytes.containsSequence(byteArrayOf(29, 86)))
    }

    @Test fun `eighty millimetre profile cuts only when explicitly requested`() {
        val profile = EscPosProfile.Receipt80.copy(cutAfterPrint = true)
        assertTrue(EscPosEncoder(profile).encode(receipt()).containsSequence(byteArrayOf(29, 86, 1)))
        assertThrows(IllegalArgumentException::class.java) { EscPosProfile.Portable58.copy(cutAfterPrint = true) }
    }

    @Test fun `table wrapping preserves text and fits the configured columns`() {
        val table = ReceiptBlock.Table(listOf("ITEM", "QTY", "TOTAL"),
            listOf(listOf("Long product description", "2", "USD 123.45")), listOf(3f, 1f, 2f),
            listOf(ReceiptColumnAlign.START, ReceiptColumnAlign.CENTER, ReceiptColumnAlign.END),
            ReceiptTextStyle(), ReceiptTextStyle())
        val output = PlainTextEncoder(columns = 32).encode(receipt(table)).toString(Charsets.US_ASCII)
        assertTrue(output.lines().all { it.length <= 32 })
        assertTrue(output.contains("description"))
        assertTrue(output.contains("USD 123.45"))
    }

    @Test fun `font expansion reduces available text columns`() {
        val block = ReceiptBlock.Text("12345678901234567890123456789012", ReceiptAlign.LEFT, ReceiptTextStyle(size = ReceiptFontSize.BIG))
        val output = EscPosEncoder().encode(receipt(block))
        assertTrue(output.containsSequence(byteArrayOf(29, 33, 17)))
        assertTrue(output.toString(Charsets.US_ASCII).contains("1234567890123456\n"))
    }

    @Test fun `raster rows are bit packed and sliced without adding line feeds`() {
        val image = MonochromeImage.fromArgb(9, 3, IntArray(27) { if (it % 9 == 0 || it % 9 == 8) 0xff000000.toInt() else -1 })
        val bytes = EscPosEncoder(EscPosProfile.Portable58.copy(rasterSliceHeight = 2), images = { image })
            .encode(receipt(ReceiptBlock.Image("logo", null, null)))
        assertTrue(bytes.containsSequence(byteArrayOf(29, 118, 48, 0, 2, 0, 2, 0, 128.toByte(), 128.toByte(), 128.toByte(), 128.toByte())))
        assertTrue(bytes.containsSequence(byteArrayOf(29, 118, 48, 0, 2, 0, 1, 0, 128.toByte(), 128.toByte())))
    }

    @Test fun `QR and every barcode format produce real raster payloads`() {
        val values = mapOf(ReceiptBarcodeFormat.CODE128 to "INV-123", ReceiptBarcodeFormat.CODE39 to "RCT-123",
            ReceiptBarcodeFormat.EAN13 to "5901234123457", ReceiptBarcodeFormat.EAN8 to "96385074",
            ReceiptBarcodeFormat.UPCA to "012345678905", ReceiptBarcodeFormat.ITF to "12345678")
        values.forEach { (format, content) ->
            assertTrue(EscPosEncoder().encode(receipt(ReceiptBlock.Barcode(content, 300, 60, format))).containsSequence(byteArrayOf(29, 118, 48)))
        }
        val first = EscPosEncoder().encode(receipt(ReceiptBlock.QrCode("first", 180)))
        val second = EscPosEncoder().encode(receipt(ReceiptBlock.QrCode("second", 180)))
        assertTrue(first.containsSequence(byteArrayOf(29, 118, 48)))
        assertFalse(first.contentEquals(second))
    }

    @Test fun `invalid media unsupported glyphs and command injection fail before sending`() {
        var sent = false
        val connection = PrinterConnection { sent = true }
        assertThrows(IllegalArgumentException::class.java) {
            connection.send(EscPosEncoder().encode(receipt(ReceiptBlock.Text("PAY\u001b@", ReceiptAlign.LEFT, ReceiptTextStyle()))))
        }
        assertFalse(sent)
        assertThrows(IllegalArgumentException::class.java) { EscPosEncoder().encode(receipt(ReceiptBlock.Text("\u4e2d", ReceiptAlign.LEFT, ReceiptTextStyle()))) }
        assertThrows(IllegalArgumentException::class.java) { EscPosEncoder().encode(receipt(ReceiptBlock.Barcode("BAD", 300, 60, ReceiptBarcodeFormat.EAN13))) }
        assertThrows(IllegalArgumentException::class.java) { EscPosEncoder().encode(receipt(ReceiptBlock.Image("missing", null, null))) }
        assertThrows(IllegalArgumentException::class.java) { EscPosEncoder().encode(receipt(ReceiptBlock.QrCode("x", 500))) }
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
        assertThrows(IllegalArgumentException::class.java) { PlainTextEncoder().encode(receipt(ReceiptBlock.QrCode("x", 180))) }
    }

    @Test fun `one rendered job is sent unchanged`() {
        val out = ByteArrayOutputStream()
        val driver = EscPosEncoder()
        val input = receipt(ReceiptBlock.KeyValue("TOTAL", "USD 25.25", ReceiptTextStyle()))
        OutputStreamConnection(out).send(driver.encode(input))
        assertArrayEquals(driver.encode(input), out.toByteArray())
    }

    @Test fun `raster QR can be reconstructed and decoded`() {
        val bytes = EscPosEncoder().encode(receipt(ReceiptBlock.QrCode("receipt:INV-001", 180)))
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
