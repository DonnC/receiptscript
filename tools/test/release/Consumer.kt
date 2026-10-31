package receiptscript.releasecheck

import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import zw.co.donnclab.rct.ReceiptBlock
import zw.co.donnclab.rct.ReceiptScript
import zw.co.donnclab.rct.escpos.*

data class Item(val name: String, val quantity: Int, val total: BigDecimal)
data class Sale(val merchant: String, val reference: String, val currency: String, val total: BigDecimal, val items: List<Item>)

/** Compiled against packaged JARs, never the production classes directory. */
fun main(args: Array<String>) {
    val data = Sale("Coffee & Code", "RCT-001", "USD", BigDecimal("10.00"), listOf(
        Item("Latte", 1, BigDecimal("4.50")), Item("Brownie", 1, BigDecimal("3.00")),
        Item("Filter coffee", 1, BigDecimal("2.50"))
    ))
    val receipt = ReceiptScript().compile(File(args.single()).readText()).render(data)
    check(receipt.blocks.filterIsInstance<ReceiptBlock.Table>().single().rows.size == 3)
    check(receipt.blocks.filterIsInstance<ReceiptBlock.KeyValue>().last().value == "USD 10.00")
    val output = ByteArrayOutputStream()
    ReceiptPrinter(EscPosDriver(), OutputStreamConnection(output)).print(receipt)
    check(output.size() > 100)
    check(output.toByteArray().take(2) == listOf(27.toByte(), 64.toByte()))
    println("Packaged JAR consumer passed: ${receipt.blocks.size} blocks, ${output.size()} ESC/POS bytes")
}
