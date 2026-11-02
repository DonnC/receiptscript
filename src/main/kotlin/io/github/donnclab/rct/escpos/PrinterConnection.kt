package io.github.donnclab.rct.escpos

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * A transport for ESC/POS bytes: Bluetooth, USB, TCP, serial, or your own.
 *
 * [send] receives one complete job and should block until the bytes are written. Returning
 * normally means the bytes were sent, not that paper came out. Throw on failure; jobs are never
 * retried automatically because a retry could print a duplicate receipt.
 *
 * Ready-made connections: [OutputStreamConnection], [TcpPrinterConnection], and on Android
 * `BluetoothPrinterConnection` and `UsbPrinterConnection`.
 */
public fun interface PrinterConnection {
    public fun send(bytes: ByteArray)
}

/** Writes to a stream you already opened and own, such as `BluetoothSocket.outputStream`. Never closes it. */
public class OutputStreamConnection(private val output: OutputStream, private val chunkSize: Int = 1024) : PrinterConnection {
    init { require(chunkSize in 1..65536) }

    @Synchronized override fun send(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = minOf(chunkSize, bytes.size - offset)
            output.write(bytes, offset, count)
            offset += count
        }
        output.flush()
    }
}

/** Opens a TCP socket to a network printer (port 9100 by default) for each job, then closes it. */
public class TcpPrinterConnection(
    private val host: String,
    private val port: Int = 9100,
    private val connectTimeoutMillis: Int = 5000
) : PrinterConnection {
    init { require(host.isNotBlank() && port in 1..65535 && connectTimeoutMillis > 0) }

    @Synchronized override fun send(bytes: ByteArray) {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            socket.tcpNoDelay = true
            OutputStreamConnection(socket.getOutputStream()).send(bytes)
        }
    }
}
