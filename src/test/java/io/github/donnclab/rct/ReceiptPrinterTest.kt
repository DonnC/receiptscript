package io.github.donnclab.rct

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException
import java.io.ByteArrayOutputStream
import io.github.donnclab.rct.escpos.EscPosDriver
import io.github.donnclab.rct.escpos.OutputStreamConnection

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReceiptPrinterTest {
    @Test fun `bundled ESC POS printer accepts rendered output`() = runBlocking {
        val document = RctCompiler().compile("receipt \"test\"\nleft \"Receipt\"").render(null)
        val output = AndroidReceiptRenderer(RuntimeEnvironment.getApplication()).render(document)
        val bytes = ByteArrayOutputStream()
        EscPosDriver(OutputStreamConnection(bytes)).print(output)
        assertTrue(bytes.size() > 100)
    }
    @Test fun `printer receives the exact preview output`() = runBlocking {
        val document = RctCompiler().compile("receipt \"test\"\nleft \"Receipt\"").render(null)
        val output = AndroidReceiptRenderer(RuntimeEnvironment.getApplication()).render(document)
        var printed: Receipt? = null
        ReceiptDriver { printed = it }.print(output)
        assertSame(output, printed)
    }

    @Test fun `driver failures propagate without retry`() {
        assertThrows(IOException::class.java) {
            runBlocking {
                val document = RctCompiler().compile("receipt \"test\"\nleft \"Receipt\"").render(null)
                val output = AndroidReceiptRenderer(RuntimeEnvironment.getApplication()).render(document)
                ReceiptDriver { throw IOException("Paper out") }.print(output)
            }
        }
    }
}
