package examples.readme

import java.io.OutputStream
import java.math.BigDecimal
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.ReceiptScript
import zw.co.donnclab.rct.escpos.*

data class Item(val name: String, val quantity: Int, val total: BigDecimal)
data class SaleReceipt(val merchant: String, val reference: String, val currency: String, val total: BigDecimal, val items: List<Item>)

fun renderSampleReceipt(source: String): Receipt = ReceiptScript().compile(source).render(
    SaleReceipt("Coffee & Code", "RCT-001", "USD", BigDecimal("10.00"), listOf(
        Item("Latte", 1, BigDecimal("4.50")),
        Item("Brownie", 1, BigDecimal("3.00")),
        Item("Filter coffee", 1, BigDecimal("2.50"))
    ))
)

/** The host supplies a connected stream and calls this on its worker dispatcher. */
fun printReceipt(receipt: Receipt, output: OutputStream) {
    ReceiptPrinter(EscPosDriver(EscPosProfile.Portable58), OutputStreamConnection(output)).print(receipt)
}
