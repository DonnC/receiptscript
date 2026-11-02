package io.github.donnclab.rct.sample

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import io.github.donnclab.rct.RctCompiler
import io.github.donnclab.rct.ReceiptBlock
import io.github.donnclab.rct.ReceiptTemplateRepository

class SampleCatalogTest {
    private val assets = File("src/main/assets")
    private val catalog = SampleCatalog { assets.resolve(it).readText() }

    @Test fun `repository path matches string rendering for every example`() = kotlinx.coroutines.runBlocking {
        val templates = ReceiptTemplateRepository { id, _ -> assets.resolve("receipts/$id.rct").readText() }
        SampleCatalog.examples.forEach { example ->
            val sample = catalog.open(example)
            val viaRepository = RctCompiler().template(example.id, templates, emptyMap()).render(catalog.data(sample))
            assertEquals(catalog.document(example), viaRepository)
        }
    }

    @Test fun `every gallery example renders its real asset and preview data`() {
        assertEquals(8, SampleCatalog.examples.size)
        SampleCatalog.examples.forEach { example ->
            assertTrue(catalog.open(example).source.contains("receipt"))
            val document = catalog.document(example)
            assertTrue(document.blocks.isNotEmpty())
            assertFalse(document.blocks.toString().contains("{{"))
        }
    }

    @Test fun `bank sample masks PAN and does not contain secrets`() {
        val receipt = catalog.document(SampleCatalog.examples.first { it.id == "bank" })
        val card = receipt.blocks.filterIsInstance<ReceiptBlock.KeyValue>().first { it.key == "CARD" }
        assertTrue(card.value.contains('*'))
        assertTrue(card.value.endsWith("7890"))
        assertFalse(receipt.toString().contains("4506123456787890"))
    }

    @Test fun `shared footer is last with the engine supplied gap`() {
        val blocks = catalog.document(SampleCatalog.examples.first { it.id == "shared" }).blocks
        assertEquals("Thank you for shopping locally!", (blocks.last() as ReceiptBlock.Text).text)
        assertEquals(ReceiptBlock.Spacer(2), blocks[blocks.lastIndex - 1])
        assertTrue(blocks.first() is ReceiptBlock.Image)
    }

    @Test fun `complex report retains currencies and many table rows`() {
        val blocks = catalog.document(SampleCatalog.examples.last()).blocks
        assertTrue(blocks.filterIsInstance<ReceiptBlock.Table>().sumOf { it.rows.size } >= 20)
        assertTrue(blocks.toString().contains("USD"))
        assertTrue(blocks.toString().contains("ZWG"))
    }

    @Test fun `fiscal demo includes a scannable verification payload`() {
        val blocks = catalog.document(SampleCatalog.examples.first { it.id == "fiscal" }).blocks
        assertTrue(blocks.any { it is ReceiptBlock.QrCode && it.content.startsWith("https://example.com/") })
        assertTrue(blocks.toString().contains("NOT A VALID FISCAL DOCUMENT"))
    }
}
