package dev.receiptscript.sample

import android.content.Context
import zw.co.donnclab.rct.android.EscPosReceiptPrinter
import zw.co.donnclab.rct.android.ReceiptDriver
import zw.co.donnclab.rct.escpos.EscPosProfile
import zw.co.donnclab.rct.escpos.PrinterConnection

typealias SamplePrinter = ReceiptDriver

/** Supply an SDK implementation or an established ESC/POS connection. The gallery works without one. */
fun connectedPrinter(context: Context): ReceiptDriver? = null

fun escPosPrinter(connection: PrinterConnection,
                  profile: EscPosProfile = EscPosProfile.Portable58): ReceiptDriver =
    EscPosReceiptPrinter(connection, profile)
