package io.github.donnclab.rct

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ReceiptPdfTest {
    @Test fun exportedPdfHasEveryPageAndExpectedPhysicalWidth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val receipt = RctCompiler().compile("receipt \"long\"\nfor item in items:\n  left \"{{item}}\"")
            .render(mapOf("items" to List(100) { "Record $it" }))
        val rendered = AndroidReceiptRenderer(context).render(receipt)
        val pdf = rendered.toPdf()
        assertEquals("application/pdf", pdf.mimeType)
        assertEquals("%PDF", pdf.bytes.copyOfRange(0, 4).decodeToString())
        val file = File.createTempFile("receipt-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(pdf.bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { reader ->
                    assertEquals((rendered.height + 1119) / 1120, reader.pageCount)
                    reader.openPage(0).use { page -> assertEquals(137, page.width) }
                }
            }
        } finally {
            file.delete()
        }
        assertTrue(rendered.bands.none { it.isRecycled })
    }
}
