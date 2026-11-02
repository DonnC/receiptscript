package examples

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.ReceiptOptions
import io.github.donnclab.rct.ReceiptScript
import io.github.donnclab.rct.escpos.BluetoothPrinterConnection
import io.github.donnclab.rct.escpos.EscPosDriver
import io.github.donnclab.rct.escpos.EscPosMode
import io.github.donnclab.rct.escpos.EscPosProfile
import io.github.donnclab.rct.escpos.OutputStreamConnection
import io.github.donnclab.rct.escpos.PrinterConnection
import io.github.donnclab.rct.escpos.TcpPrinterConnection
import io.github.donnclab.rct.escpos.UsbPrinterConnection
import java.io.File
import java.io.OutputStream

// Compiled examples for README.md and docs/guides/printing.md.

data class Item(val name: String, val quantity: Int, val total: String)
data class Sale(val merchant: String, val reference: String, val currency: String, val total: String, val items: List<Item>)

/** A paired 58 mm Bluetooth printer. Request BLUETOOTH_CONNECT first on Android 12+. */
fun bluetoothReceipts(context: Context, device: BluetoothDevice): ReceiptScript =
    ReceiptScript(context, driver = EscPosDriver(BluetoothPrinterConnection(device)))

/** An 80 mm network printer. The receipt width must match the profile: 576 dots. */
fun networkReceipts(context: Context, host: String): ReceiptScript =
    ReceiptScript(
        context,
        driver = EscPosDriver(TcpPrinterConnection(host), EscPosProfile.Receipt80),
        options = ReceiptOptions(width = 576)
    )

/** A USB printer your app has already opened and claimed. */
fun usbDriver(connection: UsbDeviceConnection, endpoint: UsbEndpoint): EscPosDriver =
    EscPosDriver(UsbPrinterConnection(connection, endpoint))

/** A connection you keep open yourself, using the printer's built-in fonts. */
fun streamDriver(output: OutputStream): EscPosDriver =
    EscPosDriver(OutputStreamConnection(output), mode = EscPosMode.TEXT)

/** Any other transport: wrap its write call. */
fun customDriver(write: (ByteArray) -> Unit): EscPosDriver = EscPosDriver(PrinterConnection { write(it) })

suspend fun printSale(receipts: ReceiptScript, sale: Sale, cacheDir: File): Receipt {
    val receipt = receipts.render("sale", sale)
    File(cacheDir, "sale.pdf").writeBytes(receipt.toPdf().bytes)
    receipts.print(receipt)
    return receipt
}
