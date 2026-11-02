package io.github.donnclab.rct

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The default [ReceiptTemplateRepository]: reads `assets/<directory>/<id><extension>`, so
 * `render("sale", data)` loads `assets/receipts/sale.rct`. Assets from library modules are merged in.
 *
 * Template IDs may contain `/` for sub-folders (`"stores/harare/sale"`) but not `..`, `\` or `:`.
 * [ReceiptTemplateRepository.getTemplate]'s `args` are ignored.
 */
public class AssetTemplateRepository(
    context: Context,
    private val directory: String = "receipts",
    private val extension: String = ".rct"
) : ReceiptTemplateRepository {
    private val assets = context.applicationContext.assets

    init {
        require(isAssetPath(directory, allowEmpty = true)) { "Invalid asset directory: $directory" }
        require(extension.startsWith('.') && '/' !in extension && '\\' !in extension) { "Invalid template extension: $extension" }
    }

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
