package io.github.donnclab.rct.escpos

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import java.io.IOException

/**
 * Sends jobs to a USB printer through its bulk OUT endpoint.
 *
 * Your app finds the device, requests USB permission, opens it with `UsbManager.openDevice`, claims
 * the printer interface and passes its bulk OUT endpoint here. Your app also closes the connection.
 *
 * For USB-to-serial adapters, use a serial library and wrap its write call in a [PrinterConnection].
 */
public class UsbPrinterConnection(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
    private val timeoutMillis: Int = 5000
) : PrinterConnection {
    init {
        require(timeoutMillis > 0) { "Timeout must be positive" }
        require(endpoint.direction == UsbConstants.USB_DIR_OUT) { "Endpoint must be an OUT endpoint" }
        require(endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) { "Endpoint must be a bulk endpoint" }
    }

    @Synchronized
    override fun send(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val requested = minOf(CHUNK, bytes.size - offset)
            val written = connection.bulkTransfer(endpoint, bytes, offset, requested, timeoutMillis)
            if (written <= 0 || written > requested) throw IOException("USB printer transfer failed at byte $offset")
            offset += written
        }
    }

    private companion object { const val CHUNK = 1024 }
}
