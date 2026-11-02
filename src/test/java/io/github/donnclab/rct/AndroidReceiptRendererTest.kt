package io.github.donnclab.rct

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.BitmapFactory
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import io.github.donnclab.rct.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidReceiptRendererTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val document = RctCompiler().compile("""
        receipt "test"
        meta:
          width: 32
          font: 24
        center bold "MERCHANT"
        kv "DATE" "31/10/2026 10:45:00"
        divider "_"
        table:
          headers: "ITEM" "QTY" "TOTAL"
          weights: 3 1 2
          row "Long item description that wraps" "2" "USD 12.50"
        spacer 2
        center "Thank you!"
    """.trimIndent()).render(emptyMap<String, Any>())

    @Test fun `bands contain exactly the same pixels as a single image`() {
        val whole = AndroidReceiptRenderer(context, ReceiptOptions(bandHeight = 8192)).render(document)
        val banded = AndroidReceiptRenderer(context, ReceiptOptions(bandHeight = 64)).render(document)
        assertEquals(384, whole.width)
        assertEquals(whole.height, banded.height)
        assertTrue(banded.bands.size > 1)
        assertTrue(banded.bands.all { it.height <= 64 && !it.isMutable })
        assertArrayEquals(pixels(whole), pixels(banded))
        assertTrue(pixels(banded).all { it == Color.BLACK || it == Color.WHITE })
    }

    @Test fun `rendered QR is actually decodable`() {
        val receipt = ReceiptDocument("qr", null, listOf(ReceiptBlock.QrCode("https://example.com/RCT42", 240)))
        val image = AndroidReceiptRenderer(context).render(receipt)
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels(image)))))
        assertEquals("https://example.com/RCT42", decoded.text)
    }

    @Test fun `images rows and barcode are supported without a second layout`() {
        val receipt = ReceiptDocument("media", null, listOf(
            ReceiptBlock.Image("logo", 64, 64), ReceiptBlock.Row(listOf("A", "B")),
            ReceiptBlock.Barcode("RCT42", 300, 60, ReceiptBarcodeFormat.CODE128)
        ))
        val logo = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
        val result = AndroidReceiptRenderer(context).render(receipt, mapOf("logo" to logo))
        assertTrue(result.height > 124)
        assertTrue(pixels(result).any { it == Color.BLACK })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `missing images fail before printing`() {
        AndroidReceiptRenderer(context).render(ReceiptDocument("bad", null, listOf(ReceiptBlock.Image("missing", 32, 32))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `long receipts are bounded before bitmap allocation`() {
        AndroidReceiptRenderer(context, ReceiptOptions(maxHeight = 100)).render(document)
    }

    @Test fun `all starter templates render and daily totals produce an inspectable raster`() {
        val directory = File("example/src/main/assets/receipts")
        val fixtures = requireNotNull(directory.listFiles()).filter { it.name.endsWith(".preview.json") }
        assertEquals(8, fixtures.size)
        val engine = RctCompiler(loadTemplate = { id -> directory.resolve("$id.rct").readText() })
        val renderer = AndroidReceiptRenderer(context)
        fixtures.forEach { fixture ->
            val id = fixture.name.removeSuffix(".preview.json")
            val document = engine.template(id).render(jsonValue(JSONObject(fixture.readText())))
            val images = document.blocks.filterIsInstance<ReceiptBlock.Image>().associate { image ->
                image.source to BitmapFactory.decodeFile(directory.resolve(image.source.removePrefix("asset:")).path)
            }
            val rendered = renderer.render(document, images)
            assertTrue("$id must have visible content", pixels(rendered).any { it == Color.BLACK })
            if (id == "daily-total") {
                val image = Bitmap.createBitmap(pixels(rendered), rendered.width, rendered.height, Bitmap.Config.ARGB_8888)
                val output = File("build/preview-check/daily-total.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        }
    }

    private fun jsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { jsonValue(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { jsonValue(value.get(it)) }
        else -> value
    }

    private fun pixels(receipt: Receipt): IntArray = receipt.bands.flatMap { bitmap ->
        IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }.asIterable()
    }.toIntArray()
}
