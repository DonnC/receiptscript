package examples.android

import examples.BuiltInPrinterDriver
import examples.TerminalPrinter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.Receipt

/** The SDK wrapper converts its completion/error callbacks to awaitCompletion. Failures propagate. */
suspend fun printBuiltIn(receipt: Receipt, sdk: TerminalPrinter, cut: Boolean = false) = withContext(Dispatchers.IO) {
    val driver = BuiltInPrinterDriver(sdk)
    require(!cut || driver.supportsCut) { "This terminal has no cutter" }
    receipt.render(driver)
    if (cut) driver.cutPaper()
    sdk.awaitCompletion()
}
