package zw.co.donnclab.rct.escpos

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.ReceiptAdapter

/** Sends one job. Successful return means bytes were sent, not that paper was printed. */
fun interface PrinterConnection {
    fun send(bytes: ByteArray)
}

/** Encodes the entire receipt before sending, so a template/media error cannot start a partial job. */
class ReceiptPrinter(private val driver: ReceiptAdapter<ByteArray>, private val connection: PrinterConnection) {
    @Synchronized fun print(receipt: Receipt) {
        connection.send(driver.render(receipt))
    }
}

/** Borrows an already connected stream (including Android BluetoothSocket.outputStream). */
class OutputStreamConnection(private val output: OutputStream, private val chunkSize: Int = 1024) : PrinterConnection {
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

/** One owned TCP socket per job. Use only on a worker thread; failures propagate without retry. */
class TcpPrinterConnection(
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
