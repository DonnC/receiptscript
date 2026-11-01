package dev.receiptscript.sample

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import zw.co.donnclab.rct.ReceiptBlock

class SampleCatalogTest {
    private val assets = File("src/main/assets")
    private val catalog = SampleCatalog { assets.resolve(it).readText() }

    @Test fun `repository path matches string rendering for every example`() = kotlinx.coroutines.runBlocking {
        val templates = object : zw.co.donnclab.rct.ReceiptTemplateRepository {
            override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String =
                assets.resolve("receipts/$templateId.rct").readText()
        }
        SampleCatalog.examples.forEach { example ->
            assertEquals(catalog.open(example).receipt, catalog.open(example, templates).receipt)
        }
    }

    @Test fun `every gallery example renders its real asset and preview data`() {
        assertEquals(8, SampleCatalog.examples.size)
        SampleCatalog.examples.forEach { example ->
            val document = catalog.open(example)
            assertTrue(document.source.contains("receipt"))
            assertTrue(document.receipt.blocks.isNotEmpty())
            assertFalse(document.receipt.blocks.toString().contains("{{"))
        }
    }

    @Test fun `bank sample masks PAN and does not contain secrets`() {
        val receipt = catalog.open(SampleCatalog.examples.first { it.id == "bank" }).receipt
        val card = receipt.blocks.filterIsInstance<ReceiptBlock.KeyValue>().first { it.key == "CARD" }
        assertTrue(card.value.contains('*'))
        assertTrue(card.value.endsWith("7890"))
        assertFalse(receipt.toString().contains("4506123456787890"))
    }

    @Test fun `shared footer is last with the engine supplied gap`() {
        val blocks = catalog.open(SampleCatalog.examples.first { it.id == "shared" }).receipt.blocks
        assertEquals("Thank you for shopping locally!", (blocks.last() as ReceiptBlock.Text).text)
        assertEquals(ReceiptBlock.Spacer(2), blocks[blocks.lastIndex - 1])
        assertTrue(blocks.first() is ReceiptBlock.Image)
    }

    @Test fun `complex report retains currencies and many table rows`() {
        val blocks = catalog.open(SampleCatalog.examples.last()).receipt.blocks
        assertTrue(blocks.filterIsInstance<ReceiptBlock.Table>().sumOf { it.rows.size } >= 20)
        assertTrue(blocks.toString().contains("USD"))
        assertTrue(blocks.toString().contains("ZWG"))
    }

    @Test fun `fiscal demo includes a scannable verification payload`() {
        val blocks = catalog.open(SampleCatalog.examples.first { it.id == "fiscal" }).receipt.blocks
        assertTrue(blocks.any { it is ReceiptBlock.QrCode && it.content.startsWith("https://example.com/") })
        assertTrue(blocks.toString().contains("NOT A VALID FISCAL DOCUMENT"))
    }
}
