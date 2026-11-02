package io.github.donnclab.rct.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import io.github.donnclab.rct.AndroidReceiptRenderer
import io.github.donnclab.rct.RctCompiler

@RunWith(AndroidJUnit4::class)
class ReceiptPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resolvedReceiptNeedsNoPrinterOrDriver() {
        val receipt = RctCompiler().compile("receipt \"preview\"\ncenter bold \"MERCHANT\"").render(null)
        val renderer = AndroidReceiptRenderer(InstrumentationRegistry.getInstrumentation().targetContext)
        val rendered = renderer.render(receipt)
        compose.setContent { ReceiptPreview(rendered) }
        compose.onNodeWithContentDescription("Receipt preview, part 1").assertIsDisplayed()
    }
}
