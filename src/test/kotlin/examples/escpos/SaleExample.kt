package examples

import java.io.OutputStream
import java.math.BigDecimal
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.ReceiptScript
import zw.co.donnclab.rct.escpos.*

data class SaleData(val merchant: Merchant, val sale: Sale, val items: List<SaleItem>)
data class Merchant(val name: String)
data class Sale(val reference: String, val date: String, val currency: String, val total: BigDecimal)
data class SaleItem(val name: String, val quantity: Int, val total: BigDecimal)

/** Works with assets, DB sources, and strings: the caller provides already loaded source. */
fun createSaleReceipt(source: String): Receipt = ReceiptScript().compile(source).render(
    SaleData(
        merchant = Merchant("Example Store"),
        sale = Sale("INV-001", "2026-10-30T10:15:00", "USD", BigDecimal("25.25")),
        items = listOf(SaleItem("Coffee", 1, BigDecimal("4.50")), SaleItem("Lunch", 1, BigDecimal("20.75")))
    )
)

/** The caller owns the connected stream and its lifecycle. Invoke from a worker thread. */
fun printPortable(receipt: Receipt, connectedOutput: OutputStream) {
    ReceiptPrinter(
        driver = EscPosDriver(EscPosProfile.Portable58),
        connection = OutputStreamConnection(connectedOutput)
    ).print(receipt)
}

fun printNetworkReceipt(receipt: Receipt, printerHost: String) {
    ReceiptPrinter(
        driver = EscPosDriver(EscPosProfile.Receipt80),
        connection = TcpPrinterConnection(printerHost)
    ).print(receipt)
}
