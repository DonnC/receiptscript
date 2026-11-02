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
 * [args] carries your own lookup options from `render(id, data, args)`, never receipt data. Use it
 * for anything your lookup needs to decide: a merchant, a locale, a version, or a flag that picks
 * between sources in a single repository:
 *
 * ```kotlin
 * val templates = ReceiptTemplateRepository { id, args ->
 *     if (args["source"] == "db") dao.templateSource(id) else assets.getTemplate(id)
 * }
 * rct.render("sale", sale, args = mapOf("source" to "db"))
 * ```
 *
 * The same [args] are passed when loading the template's fragments. Callers that have no options
 * can use the one-argument [getTemplate] extension.
 *
 * Contract:
 * - Return the UTF-8 template source. Throw if the template does not exist.
 * - You own I/O, caching and authentication. Errors and cancellation propagate to the caller.
 *
 * The default, `AssetTemplateRepository`, reads `assets/receipts/<id>.rct`.
 */
public fun interface ReceiptTemplateRepository {
    public suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String
}

/**
 * Loads [templateId] without lookup options. A `fun interface` method cannot declare a default
 * value, so this overload keeps `args` optional for callers while implementations stay lambdas.
 */
public suspend fun ReceiptTemplateRepository.getTemplate(templateId: String): String =
    getTemplate(templateId, emptyMap())
