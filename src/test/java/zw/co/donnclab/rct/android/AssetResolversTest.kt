package zw.co.donnclab.rct.android

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AssetResolversTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun `readme assets flow renders shared sections and logo with defaults`() = runBlocking {
        val templates = AssetReceiptTemplateRepository(context)
        val engine = zw.co.donnclab.rct.ReceiptScript()
        val source = templates.getTemplate("sale")
        val compiled = engine.compile(source, templates)
        val receipt = compiled.render(mapOf("merchant" to "Coffee & Code", "reference" to "INV-1"))
        assertEquals("Thank you!", (receipt.blocks.last() as zw.co.donnclab.rct.ReceiptBlock.Text).text)
        assertNotNull(AssetReceiptMediaResolver(context).resolve("asset:logos/merchant.png"))
        assertTrue(receipt.render(AndroidReceiptRenderer(context)).height > 64)
    }

    @Test fun `template assets load utf8 and reject traversal`() = runBlocking {
        val assets = AssetReceiptTemplateRepository(context, "licenses", ".txt")
        assertTrue(assets.getTemplate("ReceiptScript-MIT").contains("MIT License"))
        try {
            assets.getTemplate("../outside")
            fail("Expected invalid identifier")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun `media interface can be passed directly to the renderer`() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val resolver = zw.co.donnclab.rct.ReceiptMediaResolver<Bitmap> { bitmap }
        val receipt = zw.co.donnclab.rct.ReceiptScript().render("receipt \"image\"\nimage \"asset:logo.png\" 8 8", null)
        assertTrue(receipt.render(AndroidReceiptRenderer(context, images = resolver)).height > 0)
    }

    @Test fun `asset media resolver rejects unsupported schemes and traversal`() {
        val resolver = AssetReceiptMediaResolver(context)
        assertNull(resolver.resolve("https://example.com/logo.png"))
        assertNull(resolver.resolve("asset:../logo.png"))
        assertNull(resolver.resolve("asset:missing.png"))
    }
}
