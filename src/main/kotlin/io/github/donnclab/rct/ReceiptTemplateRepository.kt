package io.github.donnclab.rct

/**
 * Where your templates come from: app assets, a database, a server, or a mix.
 *
 * ReceiptScript asks for templates by ID and passes the ID through unchanged, so it can be a file
 * name, a database key or anything else your lookup understands. Fragments referenced with
 * `use header "<id>"` and `use footer "<id>"` are loaded through the same repository.
 *
 * ```kotlin
 * val templates = ReceiptTemplateRepository { id, args -> dao.templateSource(id, args["merchantId"]) }
 * ```
 *
 * Contract:
 * - Return the UTF-8 template source. Throw if the template does not exist.
 * - [args] are lookup options from `render(id, data, args)` (a merchant, a locale, a version),
 *   never receipt data.
 * - You own I/O, caching and authentication. Errors and cancellation propagate to the caller.
 *
 * The default, `AssetTemplateRepository`, reads `assets/receipts/<id>.rct`.
 */
public fun interface ReceiptTemplateRepository {
    public suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String
}
