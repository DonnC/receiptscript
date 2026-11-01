package examples.android

import android.content.Context
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import examples.SaleData
import zw.co.donnclab.rct.ReceiptScript
import zw.co.donnclab.rct.escpos.*

/** The application supplies an already connected socket/stream and owns its lifecycle. */
suspend fun printSale(context: Context, output: OutputStream, data: SaleData) = withContext(Dispatchers.IO) {
    val source = context.assets.open("receipt_templates/sale.rct").bufferedReader().use { it.readText() }
    val receipt = ReceiptScript().compile(source).render(data)
    ReceiptPrinter(EscPosDriver(EscPosProfile.Portable58), OutputStreamConnection(output)).print(receipt)
}
