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
        compose.onNodeWithContentDescription("Print receipt").assertIsEnabled()
        compose.runOnIdle { state.editData("{") }
        compose.onNodeWithContentDescription("Print receipt").assertIsNotEnabled()
        compose.runOnIdle { state.editData("{\"total\":20}") }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Preview up to date").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Print receipt").assertIsEnabled()
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
        compose.onNodeWithContentDescription("Print receipt").assertDoesNotExist()
        compose.onNodeWithContentDescription("Export receipt").assertDoesNotExist()
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

    @Test fun textToolbarTogglesPreserveBindingsWithoutAnOverflowMenu() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("TOTAL").performClick()
        compose.onNodeWithContentDescription("Bold").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Underline").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("Block actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Align left").assertDoesNotExist()
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
        compose.onNodeWithText("Format").performClick()
        compose.onNodeWithText("Money").performClick()
        compose.onNodeWithText("Currency field or code").performTextReplacement("USD")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(state.draft.source, state.draft.source.contains("{{total | money:USD}}")) }
    }

    @Test fun deleteRequiresConfirmationAndCanClearTheLastSectionBlock() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithContentDescription("Duplicate TOTAL").performClick()
        compose.runOnIdle { assertEquals(2, state.draft.source.lines().count { it.contains("kv ") }) }
        compose.onAllNodesWithContentDescription("Delete TOTAL")[0].performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(2, state.draft.source.lines().count { it.contains("kv ") }) }
        compose.onAllNodesWithContentDescription("Delete TOTAL")[0].performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Delete TOTAL").performClick()
        compose.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertFalse(state.draft.source.contains("body:")) }
    }

    @Test fun currencyUsesTheSameSearchableFieldSheetAsValues() {
        val state = ReceiptStudioState(ReceiptStudioDraft("receipt \"x\"\nkv \"TOTAL\" \"{{total | money:currency}}\"", "{\"total\":10,\"currency\":\"USD\",\"otherCurrency\":\"ZWG\"}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("TOTAL").performClick()
        compose.onNodeWithContentDescription("Choose Currency field or code data field").performClick()
        compose.onNodeWithText("Search fields").performTextReplacement("otherCurrency")
        compose.onNode(hasText("otherCurrency") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(state.draft.source.contains("money:otherCurrency")) }
    }

    @Test fun footerCanBeCreatedAndEditedIndependently() {
        val state = ReceiptStudioState(ReceiptStudioDraft("receipt \"x\"\nleft \"Body\"", "{}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithContentDescription("Add Footer block").performClick()
        compose.onNodeWithText("Text", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(state.draft.source.contains("footer:")); assertTrue(state.draft.source.contains("left \"Body\"")) }
        compose.onNodeWithContentDescription("Edit Footer section").performClick()
        compose.onNodeWithText("Edit Footer").assertExists()
    }

    @Test fun previewUsesSliderAndIconActionsInsteadOfFitWidth() {
        val state = ReceiptStudioState(ReceiptStudioDraft(source, "{\"total\":10}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine, onExport = {}) } }
        compose.onNodeWithText("Preview", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Fit width").assertDoesNotExist()
        compose.onNodeWithContentDescription("Receipt zoom").assertExists()
        compose.onNodeWithContentDescription("Export receipt").assertExists()
    }

    @Test fun draggingAcrossTwoRowsCommitsOneUndoableReorder() {
        val state = ReceiptStudioState(ReceiptStudioDraft("receipt \"drag\"\nbody:\n  kv \"FIRST\" \"1\"\n  kv \"SECOND\" \"2\"\n  kv \"THIRD\" \"3\"", "{}"))
        val original = state.draft.source
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        val first = compose.onNodeWithContentDescription("Drag to reorder FIRST", useUnmergedTree = true)
        val distance = compose.onNodeWithContentDescription("Drag to reorder THIRD", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y -
            first.fetchSemanticsNode().boundsInRoot.center.y
        first.performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, distance), 800) }
        compose.runOnIdle {
            assertTrue(state.draft.source, state.draft.source.indexOf("THIRD") < state.draft.source.indexOf("FIRST"))
            state.undo()
            assertEquals(original, state.draft.source)
        }
    }

    @Test fun toolbarAlignmentAndSizeWriteExistingSyntax() {
        val state = ReceiptStudioState(ReceiptStudioDraft("receipt \"x\"\ncenter small \"{{name}}\"", "{\"name\":\"Merchant\"}"))
        compose.setContent { MaterialTheme { ReceiptStudio(state, ReceiptScript(ApplicationProvider.getApplicationContext())) } }
        compose.onNodeWithText("Name", substring = false).performClick()
        val align = compose.onNodeWithContentDescription("Align right")
        val underline = compose.onNodeWithContentDescription("Underline")
        assertEquals(align.fetchSemanticsNode().boundsInRoot.center.y, underline.fetchSemanticsNode().boundsInRoot.center.y, 1f)
        align.performClick().assertIsOn()
        compose.onNodeWithContentDescription("Text size").performClick()
        compose.onNodeWithText("Large").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(state.draft.source.contains("right big \"{{name}}\"")) }
    }

    @Test fun maskControlsWorkForNonCardData() {
        val state = ReceiptStudioState(ReceiptStudioDraft("receipt \"x\"\nleft \"{{nationalId}}\"", "{\"nationalId\":\"63123456456\"}"))
        compose.setContent { MaterialTheme { ReceiptStudio(state, ReceiptScript(ApplicationProvider.getApplicationContext())) } }
        compose.onNodeWithText("National Id").performClick()
        compose.onNodeWithText("Format").performClick()
        compose.onNodeWithText("Mask", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Show first").performTextReplacement("2")
        compose.onNodeWithText("Show last").performTextReplacement("3")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(state.draft.source.contains("mask:2,3")) }
    }

    @Test fun imageSheetNeverOffersTextFormatting() {
        val state = ReceiptStudioState(ReceiptStudioDraft("receipt \"x\"\nimage \"{{logo}}\" 160 80", "{\"logo\":\"asset:logo.png\"}"))
        compose.setContent { MaterialTheme { ReceiptStudio(state, ReceiptScript(ApplicationProvider.getApplicationContext())) } }
        compose.onNodeWithText("Image", substring = false).performClick()
        compose.onNodeWithText("Image source").assertExists()
        compose.onNodeWithText("Format").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bold").assertDoesNotExist()
        compose.onNodeWithContentDescription("Block actions").assertDoesNotExist()
        compose.onNodeWithContentDescription("Choose Image source data field").assertExists()
    }

    @Test fun customSizeCanBeAppliedAndPresetClearsOverride() {
        val state = ReceiptStudioState(ReceiptStudioDraft("receipt \"x\"\nleft \"Title\" 21", "{}"))
        val engine = ReceiptScript(ApplicationProvider.getApplicationContext())
        compose.setContent { MaterialTheme { ReceiptStudio(state, engine) } }
        compose.onNodeWithText("Title").performClick()
        compose.onNodeWithContentDescription("Text size").performClick()
        compose.onNodeWithText("Custom: 21 dots").performClick()
        compose.onNodeWithText("Size in dots").performTextReplacement("25")
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(state.draft.source.contains("left \"Title\" 25")) }
        compose.onNodeWithText("Title").performClick()
        compose.onNodeWithContentDescription("Text size").performClick()
        compose.onNodeWithText("Small").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { assertTrue(state.draft.source.contains("left small \"Title\"")); assertFalse(state.draft.source.contains("25")) }
    }
}
