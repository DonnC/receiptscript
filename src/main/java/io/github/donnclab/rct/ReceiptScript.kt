package io.github.donnclab.rct

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The ReceiptScript engine: turns a template and your data into a [Receipt], and prints it.
 *
 * Create one instance per printer and reuse it. You supply up to three things; everything else
 * (parsing, binding, layout, preview, export, ESC/POS) is handled for you:
 *
 * | You provide | Default |
 * | --- | --- |
 * | [ReceiptTemplateRepository]: where templates come from | [AssetTemplateRepository], `assets/receipts/<id>.rct` |
 * | [ReceiptImageResolver]: how `image "..."` references become bitmaps | [AssetImageResolver], `asset:` references |
 * | [ReceiptDriver]: how a receipt reaches your printer | none; preview and export work without one |
 *
 * ```kotlin
 * val rct = ReceiptScript(context, driver = EscPosDriver(BluetoothPrinterConnection(device)))
 * val receipt = rct.render("sale", sale)   // preview it, export it, or…
 * rct.print(receipt)                       // …print exactly what was previewed
 * ```
 *
 * All functions are main-safe: work runs on a background dispatcher. Rendering never prints.
 *
 * @param driver prints receipts; required only for [print].
 * @param templates loads template source by ID, including `use header`/`use footer` fragments.
 * @param images resolves image references used by the template.
 * @param options paper width and raster layout; match `width` to your printer.
 */
public class ReceiptScript(
    context: Context,
    private val driver: ReceiptDriver? = null,
    private val templates: ReceiptTemplateRepository = AssetTemplateRepository(context),
    private val images: ReceiptImageResolver = AssetImageResolver(context),
    options: ReceiptOptions = ReceiptOptions()
) {
    private val compiler = RctCompiler()
    private val renderer = AndroidReceiptRenderer(context.applicationContext, options)
    private val printLock = Mutex()

    /** Loads raw source through this instance's repository, including reusable header/footer fragments. */
    public suspend fun templateSource(templateId: String, args: Map<String, Any?> = emptyMap()): String =
        withContext(Dispatchers.IO) { templates.getTemplate(templateId, args) }

    /**
     * Loads template [templateId] from the repository and binds [data] to it.
     *
     * @param data a `Map` or any Kotlin/Java object whose properties match the template bindings.
     * @param args lookup options passed unchanged to [ReceiptTemplateRepository.getTemplate]
     *   (for example a merchant ID); not visible to the template.
     */
    public suspend fun render(templateId: String, data: Any?, args: Map<String, Any?> = emptyMap()): Receipt =
        withContext(Dispatchers.IO) {
            layout(compiler.template(templateId, templates, args).render(data))
        }

    /** Like [render], but takes template [source] directly. Fragments still come from the repository. */
    public suspend fun renderSource(source: String, data: Any?, args: Map<String, Any?> = emptyMap()): Receipt =
        withContext(Dispatchers.IO) {
            layout(compiler.compile(source, templates, args).render(data))
        }

    /**
     * Sends [receipt] to the configured driver. Jobs on one instance run one at a time.
     * Failures propagate and are never retried, so a failed job cannot print twice.
     */
    public suspend fun print(receipt: Receipt) {
        val output = checkNotNull(driver) { "Pass a ReceiptDriver to ReceiptScript(...) before printing" }
        withContext(Dispatchers.IO) { printLock.withLock { output.print(receipt) } }
    }

    /** Renders template [templateId] and prints it in one call. Returns the printed receipt. */
    public suspend fun print(templateId: String, data: Any?, args: Map<String, Any?> = emptyMap()): Receipt =
        render(templateId, data, args).also { print(it) }

    private suspend fun layout(document: ReceiptDocument): Receipt {
        val sources = document.blocks.filterIsInstance<ReceiptBlock.Image>().map { it.source }.distinct()
        val bitmaps = LinkedHashMap<String, Bitmap>(sources.size)
        for (source in sources) {
            bitmaps[source] = requireNotNull(images.resolve(source)) { "Unresolved image: $source" }
        }
        return renderer.render(document, bitmaps)
    }
}
