package io.github.donnclab.rct.studio

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.donnclab.rct.ReceiptScript
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiptStudioTest {
    @get:Rule val compose = createComposeRule()
    private val source = "receipt \"test\"\nbody:\n  kv \"TOTAL\" \"{{total}}\""

    @Test fun invalidJsonDisablesPrintUntilCurrentRevisionRenders() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine, onPrint = {}) } }
        compose.onNodeWithText("Preview", useUnmergedTree = true).performClick()
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Preview up to date").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Print").assertIsEnabled()
        compose.runOnIdle { state.editData("{") }
        compose.onNodeWithText("Print").assertIsNotEnabled()
        compose.runOnIdle { state.editData("{\"total\":20}") }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Preview up to date").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Print").assertIsEnabled()
    }

    @Test fun propertiesEditTheSourceRatherThanRenderedValues() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("TOTAL").performClick()
        compose.onNodeWithText("Label").performTextReplacement("AMOUNT")
        compose.onNodeWithText("Label").assertTextContains("AMOUNT")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertTrue(state.draft.source, state.draft.source.contains("kv \"AMOUNT\" \"{{total}}\""))
            assertTrue(state.isDirty)
        }
    }

    @Test fun absentOutputCallbacksDoNotShowUnusableActions() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("Preview", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Print").assertDoesNotExist()
        compose.onNodeWithText("Export").assertDoesNotExist()
    }

    @Test fun jsonEditorRetainsInvalidDraftsAndFormatsValidData() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("Data", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("{")
        compose.runOnIdle { assertEquals("{", state.draft.dataJson) }
        compose.onNodeWithText("Format JSON").performClick()
        compose.onNodeWithText("OK").assertExists().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("{\"total\":20}")
        compose.onNodeWithText("Format JSON").performClick()
        compose.runOnIdle { assertTrue(state.draft.dataJson.contains('\n')) }
    }

    @Test fun booleanPropertiesAreAccessibleSwitchesAndPreserveBindings() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("TOTAL").performClick()
        compose.onNodeWithContentDescription("Bold").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Underline").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("On").assertDoesNotExist()
        compose.onNodeWithText("Off").assertDoesNotExist()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle {
            assertTrue(state.draft.source.contains("kv bold underline \"TOTAL\" \"{{total}}\""))
        }
    }

    @Test fun formatterControlsWriteCoreBindingsWithoutRequiringScriptSyntax() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("TOTAL").performClick()
        compose.onNodeWithText("Formatter").performClick()
        compose.onNodeWithText("Money").performClick()
        compose.onNodeWithText("Currency field or code").performTextReplacement("USD")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(state.draft.source, state.draft.source.contains("{{total | money:USD}}")) }
    }
}
