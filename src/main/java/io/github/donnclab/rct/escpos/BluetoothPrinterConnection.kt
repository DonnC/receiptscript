package io.github.donnclab.rct.escpos

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import java.util.UUID

/**
 * Sends each job to a paired Bluetooth Classic (SPP) printer: connect, write, close.
 *
 * Your app owns discovery, pairing and permissions. Before printing, request
 * `BLUETOOTH_CONNECT` on Android 12+ (or `BLUETOOTH` on older versions); without it this
 * connection throws `SecurityException`.
 *
 * Connecting per job is the most reliable option for printers that sleep or drop idle links. To
 * keep one socket open across jobs, wrap its stream with [OutputStreamConnection] instead.
 *
 * Bluetooth LE printers are not supported by this class.
 */
public class BluetoothPrinterConnection(
    private val device: BluetoothDevice,
    private val uuid: UUID = SERIAL_PORT_PROFILE
) : PrinterConnection {

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun send(bytes: ByteArray) {
        device.createRfcommSocketToServiceRecord(uuid).use { socket ->
            socket.connect()
            OutputStreamConnection(socket.outputStream).send(bytes)
        }
    }

    public companion object {
        /** The standard Serial Port Profile UUID used by Bluetooth thermal printers. */
        public val SERIAL_PORT_PROFILE: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
