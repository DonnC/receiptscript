package io.github.donnclab.rct

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

    @Test fun `default asset sources render shared sections and the logo`() = runBlocking {
        val receipt = ReceiptScript(context).render("sale", mapOf("merchant" to "Coffee & Code", "reference" to "INV-1"))
        assertEquals("Thank you!", (receipt.blocks.last() as ReceiptBlock.Text).text)
        assertTrue(receipt.blocks.first() is ReceiptBlock.Image)
        assertNotNull(AssetImageResolver(context).resolve("asset:logos/merchant.png"))
        assertTrue(receipt.height > 64)
    }

    @Test fun `template assets load utf8 and reject traversal`() = runBlocking {
        val assets = AssetTemplateRepository(context, "licenses", ".txt")
        assertTrue(assets.getTemplate("ReceiptScript-MIT").contains("MIT License"))
        try {
            assets.getTemplate("../outside")
            fail("Expected invalid identifier")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun `a lambda image resolver is used for every image reference`() = runBlocking {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val requested = mutableListOf<String>()
        val rct = ReceiptScript(context, images = ReceiptImageResolver { requested += it; bitmap })
        val receipt = rct.renderSource("receipt \"image\"\nimage \"db:logo\" 8 8\nimage \"db:logo\" 8 8", null)
        assertEquals(listOf("db:logo"), requested)
        assertTrue(receipt.height > 0)
    }

    @Test fun `asset media resolver rejects unsupported schemes and traversal`() = runBlocking {
        val resolver = AssetImageResolver(context)
        assertNull(resolver.resolve("https://example.com/logo.png"))
        assertNull(resolver.resolve("asset:../logo.png"))
        assertNull(resolver.resolve("asset:missing.png"))
    }
}
