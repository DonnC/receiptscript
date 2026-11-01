package zw.co.donnclab.rct

/**
 * Resolves a template's media reference into the output adapter's image type (Bitmap on Android).
 * Return null for an unknown reference; throw for an operational failure. Called synchronously
 * during layout on the rendering worker: download remote media before rendering. The resolver
 * owns returned images; they must remain valid for the duration of the render.
 */
fun interface ReceiptMediaResolver<T> : (String) -> T? {
    fun resolve(source: String): T?
    override fun invoke(source: String): T? = resolve(source)
}
