package zw.co.donnclab.rct.android

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.ReceiptMediaResolver
import zw.co.donnclab.rct.ReceiptTemplateRepository
import zw.co.donnclab.rct.ReceiptScript as TemplateEngine

/**
 * Configure once, then render by template ID. Storage, media and device integration are replaceable;
 * the returned [Receipt] works directly with the UI preview, printer and exporters.
 * Rendering does not print. Reuse one instance per physical printer to serialize its jobs.
 */
class ReceiptScript(
    context: Context,
    private val driver: ReceiptDriver? = null,
    private val repository: ReceiptTemplateRepository = AssetReceiptTemplateRepository(context),
    images: ReceiptMediaResolver<Bitmap> = AssetReceiptMediaResolver(context),
    options: ReceiptOptions = ReceiptOptions()
) {
    private val engine = TemplateEngine()
    private val renderer = AndroidReceiptRenderer(context, options, images)
    private val printLock = Mutex()

    /** Lookup arguments select source; [data] binds its fields. Current fragments are loaded too. */
    suspend fun render(templateId: String, data: Any?, args: Map<String, Any?> = emptyMap()): Receipt =
        withContext(Dispatchers.IO) {
            renderer.render(engine.template(templateId, repository, args).render(data))
        }

    /** Render an in-memory string, using the configured repository only for shared fragments. */
    suspend fun renderSource(source: String, data: Any?, args: Map<String, Any?> = emptyMap()): Receipt =
        withContext(Dispatchers.IO) {
            renderer.render(engine.compile(source, repository, args).render(data))
        }

    /** Prints the existing receipt without reloading, rebinding, scaling or retrying it. */
    suspend fun print(receipt: Receipt) = withContext(Dispatchers.IO) {
        val output = checkNotNull(driver) { "Configure a ReceiptDriver before printing" }
        printLock.withLock { output.print(receipt) }
    }
}
