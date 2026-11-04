package io.github.donnclab.rct.studio

import io.github.donnclab.rct.tooling.RctBindingForm
import org.junit.Assert.*
import org.junit.Test

class StudioPropertyPolicyTest {
    @Test fun `text toolbar follows core supported properties`() {
        assertEquals(setOf("align", "style", "font", "bold", "underline"), studioToolbarKeys("center small \"Hi\""))
        assertEquals(setOf("style", "font", "bold", "underline"), studioToolbarKeys("kv \"A\" \"B\""))
        listOf("image \"logo\"", "qr \"url\"", "barcode \"123\"", "divider", "spacer 2", "table:", "row \"a\" \"b\"", "for item in items:", "if enabled:", "use header \"brand\"").forEach {
            assertTrue(it, studioToolbarKeys(it).isEmpty())
        }
    }

    @Test fun `media values accept data without showing text formatters`() {
        listOf("image \"{{logo}}\"", "qr \"{{url}}\"", "barcode \"{{reference}}\"").forEach {
            assertFalse(it, studioAllowsFormat(it, "content"))
        }
        assertTrue(studioAllowsFormat("kv \"Total\" \"{{total}}\"", "value"))
        assertTrue(studioAllowsFormat("row \"a\" \"b\"", "columns"))
        assertFalse(studioAllowsFormat("spacer 1", "lines"))
    }

    @Test fun `general mask validates and renders through core without exposing raw data`() {
        val binding = RctBindingForm.read("{{nationalId | mask:2,3}}")!!
        assertNull(studioMaskError(binding.filters.single().args))
        io.github.donnclab.rct.RctCompiler().render("receipt \"test\"\nleft \"${RctBindingForm.write(binding)}\"", mapOf("nationalId" to "63123456456"))
        assertEquals("63******456", studioValuePreview(RctBindingForm.write(binding), mapOf("nationalId" to "63123456456")))
        assertNotNull(studioMaskError(listOf("-1", "3", "*")))
        assertNotNull(studioMaskError(listOf("2", "3", "**")))
        assertEquals("{{nationalId | mask:2,3}}", RctBindingForm.write(binding))
        val custom = binding.copy(filters = listOf(binding.filters.single().copy(args = studioMaskArguments("2", "3", "#"))))
        assertEquals("63######456", studioValuePreview(RctBindingForm.write(custom), mapOf("nationalId" to "63123456456")))
    }

    @Test fun `missing preview values are unavailable rather than replaced with invented data`() {
        assertNull(studioValuePreview("{{missing}}", emptyMap<String, Any?>()))
        assertEquals("USD 10.00", studioValuePreview("{{total | money:USD}}", mapOf("total" to 10)))
    }
}
