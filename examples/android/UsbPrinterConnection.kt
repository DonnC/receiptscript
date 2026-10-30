package examples.android

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.IOException
import zw.co.donnclab.rct.escpos.PrinterConnection

/** The host selects/claims the printer interface and provides its bulk OUT endpoint. */
class UsbPrinterConnection(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
    private val timeoutMillis: Int = 5000
) : PrinterConnection {
    init {
        require(timeoutMillis > 0)
        require(endpoint.direction == android.hardware.usb.UsbConstants.USB_DIR_OUT)
        require(endpoint.type == android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK)
    }

    @Synchronized override fun send(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val requested = minOf(1024, bytes.size - offset)
            val written = connection.bulkTransfer(endpoint, bytes, offset, requested, timeoutMillis)
            if (written <= 0 || written > requested) throw IOException("USB printer transfer failed at byte $offset")
            offset += written
        }
    }
}
