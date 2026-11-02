package io.github.donnclab.rct

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The default [ReceiptImageResolver]: decodes `asset:<path>` references from your app's assets.
 *
 * With `directory = "receipts"`, the template line `image "asset:logos/shop.png" 96 64` loads
 * `assets/receipts/logos/shop.png`. Other references (URLs, database keys) return null, so wrap or
 * replace this resolver to support them. Images are decoded as-is; no resizing or network access.
 */
public class AssetImageResolver(
    context: Context,
    private val directory: String = "receipts"
) : ReceiptImageResolver {
    private val assets = context.applicationContext.assets

    init { require(isAssetPath(directory, allowEmpty = true)) { "Invalid asset directory: $directory" } }

    override suspend fun resolve(source: String): Bitmap? {
        if (!source.startsWith(PREFIX)) return null
        val path = source.removePrefix(PREFIX)
        if (!isAssetPath(path)) return null
        return withContext(Dispatchers.IO) {
            try {
                assets.open(listOf(directory, path).filter { it.isNotEmpty() }.joinToString("/"))
                    .use { BitmapFactory.decodeStream(it) }
            } catch (_: FileNotFoundException) {
                null
            }
        }
    }

    private companion object { const val PREFIX = "asset:" }
}
