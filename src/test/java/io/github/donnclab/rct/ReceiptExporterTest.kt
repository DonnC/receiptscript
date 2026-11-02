package io.github.donnclab.rct

import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptExporterTest {
    private fun output(): Receipt {
        val receipt = RctCompiler().compile("""
            receipt "export"
            center bold "MERCHANT"
            divider "_"
            for item in items:
              kv "{{item}}" "USD 12.50"
        """.trimIndent()).render(mapOf("items" to List(40) { "INV-$it" }))
        return AndroidReceiptRenderer(RuntimeEnvironment.getApplication(), ReceiptOptions(bandHeight = 128)).render(receipt)
    }

    @Test fun `PNG export preserves every pixel and leaves cached bands alive`() {
        val rendered = output()
        val png = rendered.toImage()
        assertEquals("image/png", png.mimeType)
        assertEquals("png", png.extension)
        val decoded = BitmapFactory.decodeByteArray(png.bytes, 0, png.bytes.size)
        assertEquals(rendered.width, decoded.width)
        assertEquals(rendered.height, decoded.height)
        var y = 0
        rendered.bands.forEach { band ->
            assertFalse(band.isRecycled)
            val expected = IntArray(band.width * band.height)
            val actual = IntArray(expected.size)
            band.getPixels(expected, 0, band.width, 0, 0, band.width, band.height)
            decoded.getPixels(actual, 0, band.width, 0, y, band.width, band.height)
            assertArrayEquals(expected, actual)
            y += band.height
        }
        decoded.recycle()
    }

    @Test fun `JPEG export identifies its encoding and bitmap ownership belongs to caller`() {
        val rendered = output()
        val jpeg = ReceiptExporter.toImage(rendered, ReceiptImageFormat.JPEG, 90)
        assertEquals("image/jpeg", jpeg.mimeType)
        assertEquals("jpg", jpeg.extension)
        val bitmap = rendered.toBitmap()
        bitmap.recycle()
        assertTrue(rendered.bands.none { it.isRecycled })
    }

    @Test fun `PDF pagination is bounded and preserves the exact source extent`() {
        val rendered = output()
        val pages = ReceiptExporter.pages(rendered, ReceiptPdfOptions(pageHeightDots = 200))
        assertEquals(rendered.height, pages.sumOf { it.height })
        assertEquals(0, pages.first().offset)
        assertTrue(pages.zipWithNext().all { (left, right) -> left.offset + left.height == right.offset })
        assertThrows(IllegalArgumentException::class.java) { ReceiptPdfOptions(pageHeightDots = 0) }
    }
}
