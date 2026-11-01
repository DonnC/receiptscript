package zw.co.donnclab.rct.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.ReceiptTemplateRepository

/** Reads `assets/receipts/<id>.rct` by default, including assets merged from client modules. */
class AssetReceiptTemplateRepository(
    context: Context,
    private val directory: String = "receipts",
    private val extension: String = ".rct"
) : ReceiptTemplateRepository {
    private val assets = context.assets

    init {
        require(isAssetPath(directory, allowEmpty = true)) { "Invalid asset directory" }
        require(extension.startsWith('.') && '/' !in extension && '\\' !in extension) { "Invalid template extension" }
    }

    /** Asset lookup has no tenant options; custom repositories may interpret [args]. */
    override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String =
        withContext(Dispatchers.IO) {
            require(isAssetPath(templateId)) { "Invalid template ID: $templateId" }
            assets.open(listOf(directory, templateId + extension).filter { it.isNotEmpty() }.joinToString("/"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
}

internal fun isAssetPath(path: String, allowEmpty: Boolean = false): Boolean =
    (allowEmpty && path.isEmpty()) || (path.isNotBlank() && !path.startsWith('/') &&
        '\\' !in path && ':' !in path && path.split('/').none { it.isEmpty() || it == "." || it == ".." })
