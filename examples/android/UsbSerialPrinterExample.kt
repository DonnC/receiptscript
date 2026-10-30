package examples.android

import android.hardware.usb.UsbDeviceConnection
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.escpos.EscPosDriver
import zw.co.donnclab.rct.escpos.PrinterConnection
import zw.co.donnclab.rct.escpos.ReceiptPrinter

/** The host grants USB permission and selects the port. Serial settings come from the printer manual. */
suspend fun printUsbSerial(
    receipt: Receipt,
    port: UsbSerialPort,
    connection: UsbDeviceConnection,
    baudRate: Int
) = withContext(Dispatchers.IO) {
    // Android's UsbDeviceConnection is not Closeable; adapt it for exception-safe cleanup.
    java.io.Closeable { connection.close() }.use {
        port.use {
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            ReceiptPrinter(EscPosDriver(), PrinterConnection { bytes -> port.write(bytes, 5000) }).print(receipt)
        }
    }
}
