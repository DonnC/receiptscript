package io.github.donnclab.rct.tooling

import org.junit.Assert.*
import org.junit.Test

class RctBindingFormTest {
    @Test fun `single bindings expose formatter arguments through core`() {
        val binding = RctBindingForm.read("{{txn.total | money:txn.currency}}")!!
        assertEquals("txn.total", binding.path)
        assertEquals(listOf(RctSourceFilter("money", listOf("txn.currency"))), binding.filters)
        assertEquals("{{txn.total | money:txn.currency}}", RctBindingForm.write(binding))
    }

    @Test fun `mixed text is never mistaken for a replaceable single binding`() {
        assertNull(RctBindingForm.read("Total: {{txn.total}}"))
        assertNull(RctBindingForm.read("{{a}} {{b}}"))
        assertNull(RctBindingForm.read("Hello"))
    }

    @Test fun `all filters and custom arguments are preserved`() {
        val value = "{{customer.id | mask:2,4 | padLeft:12,0}}"
        assertEquals(value, RctBindingForm.write(RctBindingForm.read(value)!!))
    }
}
