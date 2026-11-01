package dev.receiptscript.sample

import android.content.Context
import zw.co.donnclab.rct.android.EscPosReceiptPrinter
import zw.co.donnclab.rct.android.ReceiptPrinter
import zw.co.donnclab.rct.escpos.EscPosProfile
import zw.co.donnclab.rct.escpos.PrinterConnection

typealias SamplePrinter = ReceiptPrinter

/** Supply an SDK implementation or an established ESC/POS connection. The gallery works without one. */
fun connectedPrinter(context: Context): ReceiptPrinter? = null

fun escPosPrinter(connection: PrinterConnection,
                  profile: EscPosProfile = EscPosProfile.Portable58): ReceiptPrinter =
    EscPosReceiptPrinter(connection, profile)
