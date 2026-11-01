package examples.android

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.escpos.*

/** The host selects an already paired SPP printer and requests permissions before this call. */
suspend fun printPairedBluetooth(context: Context, device: BluetoothDevice, receipt: Receipt) =
    withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Grant BLUETOOTH_CONNECT before printing")
        }
        require(device.bondState == BluetoothDevice.BOND_BONDED) { "Select an already paired printer" }
        val spp = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        device.createRfcommSocketToServiceRecord(spp).use { socket ->
            socket.connect()
            ReceiptPrinter(EscPosDriver(), OutputStreamConnection(socket.outputStream)).print(receipt)
        }
    }
