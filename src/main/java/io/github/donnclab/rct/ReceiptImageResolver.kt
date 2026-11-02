package io.github.donnclab.rct

import android.graphics.Bitmap

/**
 * Turns an image reference from a template, such as `image "asset:logos/shop.png" 96 64` or
 * `image "{{merchant.logoUrl}}"`, into a [Bitmap].
 *
 * ```kotlin
 * val images = ReceiptImageResolver { source -> logoCache.get(source) }
 * ```
 *
 * Contract:
 * - Return null when you do not recognise [source]; rendering then fails with "Unresolved image".
 * - Throw for real failures (I/O, network). The error propagates to `render`.
 * - Suspending is allowed, so network or database lookups are fine.
 * - Keep returned bitmaps alive and unrecycled until rendering finishes. ReceiptScript never
 *   recycles them.
 *
 * Each distinct reference is resolved once per render. The default, [AssetImageResolver], decodes
 * `asset:` references from your app's assets.
 */
public fun interface ReceiptImageResolver {
    public suspend fun resolve(source: String): Bitmap?
}
