package io.github.donnclab.rct.sample

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import io.github.donnclab.rct.AssetImageResolver
import io.github.donnclab.rct.ReceiptImageResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The host supports bundled logos and documents chosen through Android's picker. */
internal class SampleImages(context: Context) : ReceiptImageResolver {
    private val assets = AssetImageResolver(context, directory = "receipts")
    private val resolver = context.applicationContext.contentResolver

    override suspend fun resolve(source: String): Bitmap? {
        if (!source.startsWith("content://")) return assets.resolve(source)
        return withContext(Dispatchers.IO) {
            val uri = Uri.parse(source)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a supported image document." }
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (bounds.outWidth / options.inSampleSize > 2048 || bounds.outHeight / options.inSampleSize > 2048) options.inSampleSize *= 2
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    }
}

@Composable
internal fun rememberStudioImagePicker(): suspend () -> String? {
    val resolver = LocalContext.current.contentResolver
    var pending by remember { mutableStateOf<CompletableDeferred<String?>?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val request = pending
        pending = null
        try {
            if (uri != null) resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            request?.complete(uri?.toString())
        } catch (failure: Exception) { request?.completeExceptionally(failure) }
    }
    DisposableEffect(Unit) { onDispose { pending?.cancel() } }
    return {
        check(pending == null) { "An image picker is already open." }
        val request = CompletableDeferred<String?>()
        pending = request
        try { picker.launch(arrayOf("image/*")); request.await() }
        finally { if (pending === request) pending = null }
    }
}
