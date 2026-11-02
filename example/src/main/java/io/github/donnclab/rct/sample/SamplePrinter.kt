package io.github.donnclab.rct.sample

import android.content.Context
import io.github.donnclab.rct.ReceiptDriver
import io.github.donnclab.rct.escpos.EscPosDriver
import io.github.donnclab.rct.escpos.EscPosProfile
import io.github.donnclab.rct.escpos.PrinterConnection

/**
 * Connect your printer here. Return a driver for your SDK, or an [EscPosDriver] for an ESC/POS
 * printer, for example `escPosPrinter(BluetoothPrinterConnection(device))`.
 * The gallery previews and exports without one.
 */
fun connectedPrinter(context: Context): ReceiptDriver? = null

fun escPosPrinter(connection: PrinterConnection, profile: EscPosProfile = EscPosProfile.Portable58): ReceiptDriver =
    EscPosDriver(connection, profile)
