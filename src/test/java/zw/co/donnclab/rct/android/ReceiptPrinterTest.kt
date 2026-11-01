package zw.co.donnclab.rct.android

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import zw.co.donnclab.rct.ReceiptScript
import java.io.IOException
import java.io.ByteArrayOutputStream
import zw.co.donnclab.rct.escpos.OutputStreamConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReceiptPrinterTest {
    @Test fun `bundled ESC POS printer accepts rendered output`() = runBlocking {
        val document = ReceiptScript().compile("receipt \"test\"\nleft \"Receipt\"").render(null)
        val output = AndroidReceiptRenderer(RuntimeEnvironment.getApplication()).render(document)
        val bytes = ByteArrayOutputStream()
        output.print(EscPosReceiptPrinter(OutputStreamConnection(bytes)))
        assertTrue(bytes.size() > 100)
    }
    @Test fun `printer receives the exact preview output`() = runBlocking {
        val document = ReceiptScript().compile("receipt \"test\"\nleft \"Receipt\"").render(null)
        val output = AndroidReceiptRenderer(RuntimeEnvironment.getApplication()).render(document)
        var printed: RasterReceipt? = null
        output.print(ReceiptPrinter { printed = it })
        assertSame(output, printed)
    }

    @Test fun `driver failures propagate without retry`() {
        assertThrows(IOException::class.java) {
            runBlocking {
                val document = ReceiptScript().compile("receipt \"test\"\nleft \"Receipt\"").render(null)
                val output = AndroidReceiptRenderer(RuntimeEnvironment.getApplication()).render(document)
                output.print(ReceiptPrinter { throw IOException("Paper out") })
            }
        }
    }
}
