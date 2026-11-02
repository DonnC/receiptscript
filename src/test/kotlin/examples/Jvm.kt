package examples

import io.github.donnclab.rct.RctCompiler
import io.github.donnclab.rct.ReceiptDocument
import io.github.donnclab.rct.escpos.EscPosEncoder
import io.github.donnclab.rct.escpos.EscPosProfile
import io.github.donnclab.rct.escpos.TcpPrinterConnection

// Compiled example for docs/guides/jvm.md: the engine without Android.

fun renderOnServer(source: String, data: Any?): ReceiptDocument = RctCompiler().compile(source).render(data)

fun printFromServer(document: ReceiptDocument, host: String) {
    val bytes = EscPosEncoder(EscPosProfile.Receipt80).encode(document)
    TcpPrinterConnection(host).send(bytes)
}
