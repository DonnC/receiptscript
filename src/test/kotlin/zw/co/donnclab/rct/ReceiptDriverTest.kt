package zw.co.donnclab.rct

import org.junit.Assert.*
import org.junit.Test

class ReceiptDriverTest {
    @Test fun `SDK driver dispatches every block in order`() {
        val calls = mutableListOf<String>()
        val driver = object : ReceiptDriver {
            override fun printText(block: ReceiptBlock.Text) { calls += "text" }
            override fun printKeyValue(block: ReceiptBlock.KeyValue) { calls += "kv" }
            override fun printDivider(block: ReceiptBlock.Divider) { calls += "divider" }
            override fun printRow(block: ReceiptBlock.Row) { calls += "row" }
            override fun printTable(block: ReceiptBlock.Table) { calls += "table" }
            override fun printImage(block: ReceiptBlock.Image) { calls += "image" }
            override fun printQrCode(block: ReceiptBlock.QrCode) { calls += "qr" }
            override fun printBarcode(block: ReceiptBlock.Barcode) { calls += "barcode" }
            override fun feed(block: ReceiptBlock.Spacer) { calls += "feed" }
        }
        val style = ReceiptTextStyle()
        val receipt = Receipt("all", null, listOf(ReceiptBlock.Text("x", RctAlign.LEFT, style),
            ReceiptBlock.KeyValue("a", "b", style), ReceiptBlock.Divider("-", 32),
            ReceiptBlock.Row(listOf("a", "b")), ReceiptBlock.Table(emptyList(), listOf(listOf("a", "b")), null, null, style, style),
            ReceiptBlock.Image("logo", null, null), ReceiptBlock.QrCode("x", 180),
            ReceiptBlock.Barcode("x", 300, 60, BarcodeFormat.CODE128), ReceiptBlock.Spacer(2)))
        receipt.render(driver)
        assertEquals(listOf("text", "kv", "divider", "row", "table", "image", "qr", "barcode", "feed"), calls)
        assertFalse(driver.supportsCut)
        assertThrows(UnsupportedOperationException::class.java) { driver.cutPaper() }
    }
}
