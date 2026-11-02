package examples

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.donnclab.rct.AssetImageResolver
import io.github.donnclab.rct.AssetTemplateRepository
import io.github.donnclab.rct.ReceiptImageResolver
import io.github.donnclab.rct.ReceiptScript
import io.github.donnclab.rct.ReceiptTemplateRepository

// Compiled examples for docs/guides/templates-and-images.md.

/** Stand-in for your Room DAO or other storage. */
interface TemplateDao {
    suspend fun source(id: String, merchantId: String?): String?
}

/** Stand-in for your HTTP client. */
interface Api {
    suspend fun template(id: String): String
    suspend fun bytes(url: String): ByteArray
}

/** Templates from a database. Throw when a template is missing. */
class DatabaseTemplates(private val dao: TemplateDao) : ReceiptTemplateRepository {
    override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String =
        dao.source(templateId, args["merchantId"] as String?)
            ?: throw NoSuchElementException("No template '$templateId'")
}

/** Mixed: database first, then the server, then templates bundled in the APK. */
fun mixedTemplates(context: Context, dao: TemplateDao, api: Api): ReceiptTemplateRepository {
    val bundled = AssetTemplateRepository(context)
    return ReceiptTemplateRepository { id, args ->
        dao.source(id, args["merchantId"] as String?)
            ?: runCatching { api.template(id) }.getOrNull()
            ?: bundled.getTemplate(id, args)
    }
}

/** Images: `asset:` references from the APK, `https:` references from the network (cached). */
fun images(context: Context, api: Api): ReceiptImageResolver {
    val assets = AssetImageResolver(context)
    val cache = HashMap<String, Bitmap>()
    return ReceiptImageResolver { source ->
        when {
            source.startsWith("https://") -> cache.getOrPut(source) {
                val bytes = api.bytes(source)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> assets.resolve(source)
        }
    }
}

fun configure(context: Context, dao: TemplateDao, api: Api): ReceiptScript =
    ReceiptScript(context, templates = mixedTemplates(context, dao, api), images = images(context, api))
