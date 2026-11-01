package zw.co.donnclab.rct.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileNotFoundException
import zw.co.donnclab.rct.ReceiptMediaResolver

/** Decodes `asset:logos/merchant.png` from merged APK assets. No resizing or network access. */
class AssetReceiptMediaResolver(context: Context, private val directory: String = "") : ReceiptMediaResolver<Bitmap> {
    private val assets = context.assets

    init { require(isAssetPath(directory, allowEmpty = true)) { "Invalid asset directory" } }

    override fun resolve(source: String): Bitmap? {
        if (!source.startsWith("asset:")) return null
        val path = source.removePrefix("asset:")
        if (!isAssetPath(path)) return null
        return try {
            assets.open(listOf(directory, path).filter { it.isNotEmpty() }.joinToString("/"))
                .use { BitmapFactory.decodeStream(it) }
        } catch (_: FileNotFoundException) {
            null
        }
    }
}
