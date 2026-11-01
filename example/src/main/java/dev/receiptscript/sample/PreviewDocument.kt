package dev.receiptscript.sample

import android.content.Context
import zw.co.donnclab.rct.android.AssetReceiptMediaResolver
import zw.co.donnclab.rct.android.AndroidReceiptRenderer
import zw.co.donnclab.rct.android.ReceiptOptions
import zw.co.donnclab.rct.android.Receipt

data class PreviewDocument(val document: SampleDocument, val receipt: Receipt)

/** All sample output targets one 384-dot mechanism; metadata cannot change that hardware width. */
fun renderPreview(context: Context, document: SampleDocument): PreviewDocument {
    val renderer = AndroidReceiptRenderer(context, ReceiptOptions(width = 384),
        images = AssetReceiptMediaResolver(context, "receipts"))
    return PreviewDocument(document, document.receipt.render(renderer))
}
