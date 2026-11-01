package zw.co.donnclab.rct.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import zw.co.donnclab.rct.ReceiptMediaResolver
import zw.co.donnclab.rct.ReceiptTemplateRepository

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptScriptClientTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test fun `one client serializes complete printer jobs`() = runBlocking {
        val events = mutableListOf<String>()
        val client = ReceiptScript(context, ReceiptDriver {
            events += "start"
            delay(5)
            events += "end"
        })
        val receipt = client.renderSource("receipt \"test\"\nleft \"Hello\"", null)
        coroutineScope {
            repeat(2) { launch { client.print(receipt) } }
        }
        assertEquals(listOf("start", "end", "start", "end"), events)
    }

    @Test fun `one configured client renders previews exports and prints the same receipt`() = runBlocking {
        var printed: Receipt? = null
        val logo = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val lookups = mutableListOf<String>()
        val repository = object : ReceiptTemplateRepository {
            override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String {
                assertEquals("M1", args["merchant"])
                lookups += templateId
                return when (templateId) {
                    "sale" -> "receipt \"sale\"\nuse footer \"footer\"\nimage \"logo\" 8 8\nkv \"REF\" \"{{reference}}\""
                    else -> "center \"Thank you!\""
                }
            }
        }
        val client = ReceiptScript(context, ReceiptDriver { printed = it }, repository,
            ReceiptMediaResolver { if (it == "logo") logo else null })
        val receipt: Receipt = client.render("sale", mapOf("reference" to "INV-1"), mapOf("merchant" to "M1"))
        assertNull(printed)
        assertEquals(listOf("sale", "footer"), lookups)
        assertEquals("sale", receipt.name)
        val image = receipt.toImageBytes()
        assertNotNull(BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size))
        val bitmap = receipt.toBitmap()
        assertEquals(receipt.height, bitmap.height)
        bitmap.recycle()
        client.print(receipt)
        assertSame(receipt, printed)
    }

    @Test fun `default assets require only an Android context`() = runBlocking {
        val client = ReceiptScript(context)
        val receipt = client.render("sale", mapOf("merchant" to "Coffee", "reference" to "INV-1"))
        assertTrue(receipt.height > 0)
        assertThrows(IllegalStateException::class.java) { runBlocking { client.print(receipt) } }
        Unit
    }

    @Test fun `raw strings use the same client without repository main template lookup`() = runBlocking {
        val receipt = ReceiptScript(context).renderSource("receipt \"note\"\nleft \"{{text}}\"", mapOf("text" to "Hello"))
        assertEquals("note", receipt.name)
    }

    @Test fun `driver faults and cancellation propagate without retry`() = runBlocking {
        for (fault in listOf(IOException("Paper out"), CancellationException("cancelled"))) {
            var calls = 0
            val client = ReceiptScript(context, ReceiptDriver { calls++; throw fault })
            val receipt = client.renderSource("receipt \"test\"\nleft \"Hello\"", null)
            try { client.print(receipt); fail("Expected fault") }
            catch (actual: Exception) {
                assertEquals(fault.javaClass, actual.javaClass)
                assertEquals(fault.message, actual.message)
            }
            assertEquals(1, calls)
        }
    }
}
