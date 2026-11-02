package io.github.donnclab.rct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RctTemplateEngineTest {

    private val engine = DefaultRctTemplateEngine()

    private val context = RctRenderContext(
        root = mapOf(
            "merchant" to mapOf(
                "name" to "Test Merchant"
            ),
            "txn" to mapOf(
                "amount" to 20.0,
                "currency" to "USD",
                "date" to "2026-03-31T10:15:30",
                "invoiceId" to "INV-001"
            ),
            "items" to listOf(
                mapOf("label" to "Cash", "count" to 2),
                mapOf("label" to "Card", "count" to 5)
            ),
            "card" to mapOf(
                "pan" to "4506911234567890"
            )
        ),
        locals = mapOf(
            "item" to mapOf("label" to "Wallet", "count" to 1),
            "@index" to 0
        )
    )

    private data class Merchant(val name: String)
    private data class Txn(val amount: Double, val currency: String, val invoiceId: String)
    private data class ReceiptCtx(val merchant: Merchant, val txn: Txn)

    @Test
    fun `resolve should read nested root values`() {
        assertEquals("Test Merchant", engine.resolveValue("merchant.name", context))
        assertEquals("INV-001", engine.resolveValue("txn.invoiceId", context))
    }

    @Test
    fun `resolve should prefer locals and support list indexes`() {
        assertEquals("Wallet", engine.resolveValue("item.label", context))
        assertEquals("Card", engine.resolveValue("items[1].label", context))
        assertEquals(0, engine.resolveValue("@index", context))
        assertEquals("USD", engine.resolveValue("@root.txn.currency", context))
    }

    @Test
    fun `render should combine literals and bindings`() {
        val template = RctTemplateString(
            parts = listOf(
                RctLiteralPart("Hello "),
                RctBindingPart("merchant.name"),
                RctLiteralPart("!")
            )
        )

        assertEquals("Hello Test Merchant!", engine.render(template, context))
    }

    @Test
    fun `render should apply core filters`() {
        val template = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "txn.amount",
                    filters = listOf(
                        RctFilterCall("money", listOf("txn.currency")),
                        RctFilterCall("upper")
                    )
                )
            )
        )

        assertEquals("USD 20.00", engine.render(template, context))
    }

    @Test
    fun `render should support default and maskPan filters`() {
        val defaultTemplate = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "customer.name",
                    filters = listOf(RctFilterCall("default", listOf("\"Walk In\"")))
                )
            )
        )
        val panTemplate = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "card.pan",
                    filters = listOf(RctFilterCall("maskPan"))
                )
            )
        )

        assertEquals("Walk In", engine.render(defaultTemplate, context))
        assertEquals("450691******7890", engine.render(panTemplate, context))
    }

    @Test
    fun `render should support configurable general masking`() {
        val maskDefault = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "security.pin",
                    filters = listOf(RctFilterCall("mask"))
                )
            )
        )
        val maskLastTwo = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "customer.mobile",
                    filters = listOf(RctFilterCall("mask", listOf("0", "2")))
                )
            )
        )
        val maskFirstAndLast = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "customer.nationalId",
                    filters = listOf(RctFilterCall("mask", listOf("2", "3")))
                )
            )
        )
        val maskWithCustomCharacter = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "security.password",
                    filters = listOf(RctFilterCall("mask", listOf("1", "1", "\"#\"")))
                )
            )
        )

        val maskContext = RctRenderContext(
            root = mapOf(
                "security" to mapOf(
                    "pin" to "1234",
                    "password" to "secret"
                ),
                "customer" to mapOf(
                    "mobile" to "0778006236",
                    "nationalId" to "63-123456A75"
                )
            )
        )

        assertEquals("****", engine.render(maskDefault, maskContext))
        assertEquals("********36", engine.render(maskLastTwo, maskContext))
        assertEquals("63*******A75", engine.render(maskFirstAndLast, maskContext))
        assertEquals("s####t", engine.render(maskWithCustomCharacter, maskContext))
    }

    @Test
    fun `mask filter should not reveal short values when visible edges exceed value length`() {
        val template = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "secret",
                    filters = listOf(RctFilterCall("mask", listOf("4", "4")))
                )
            )
        )

        assertEquals("****", engine.render(template, RctRenderContext(root = mapOf("secret" to "1234"))))
    }

    @Test
    fun `render should support date datetime and padding filters`() {
        val dateTemplate = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "txn.date",
                    filters = listOf(RctFilterCall("date"))
                )
            )
        )
        val dateTimeTemplate = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "txn.date",
                    filters = listOf(RctFilterCall("datetime"))
                )
            )
        )
        val customDateTimeTemplate = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "txn.compactDate",
                    filters = listOf(RctFilterCall("datetime", listOf("\\\"dd MMM yyyy HH:mm\\\"", "\\\"yyyyMMddHHmmss\\\"")))
                )
            )
        )
        val padTemplate = RctTemplateString(
            parts = listOf(
                RctBindingPart(
                    path = "txn.invoiceId",
                    filters = listOf(RctFilterCall("padRight", listOf("10", "\".\"")))
                )
            )
        )

        val root = context.root as Map<*, *>
        val txn = root["txn"] as Map<*, *>
        val customContext = context.copy(
            root = root + mapOf(
                "txn" to txn + mapOf("compactDate" to "20260331101530")
            )
        )

        assertEquals("31/03/2026", engine.render(dateTemplate, context))
        assertEquals("31/03/2026 10:15:30", engine.render(dateTimeTemplate, context))
        assertEquals("31 Mar 2026 10:15", engine.render(customDateTimeTemplate, customContext))
        assertEquals("INV-001...", engine.render(padTemplate, context))
    }

    @Test
    fun `truthy should follow receipt friendly semantics`() {
        assertTrue(engine.isTruthy("merchant.name", context))
        assertTrue(engine.isTruthy("items", context))
        assertFalse(engine.isTruthy("customer.name", context))
        assertFalse(engine.isTruthy("empty", RctRenderContext(root = mapOf("empty" to ""))))
    }

    @Test
    fun `resolve should return null for missing paths`() {
        assertNull(engine.resolveValue("merchant.address", context))
        assertNull(engine.resolveValue("items[5].label", context))
    }

    @Test
    fun `resolve should support object based root contexts`() {
        val objectContext = RctRenderContext(
            root = ReceiptCtx(
                merchant = Merchant(name = "Object Merchant"),
                txn = Txn(amount = 12.5, currency = "USD", invoiceId = "INV-OBJ-1")
            )
        )

        assertEquals("Object Merchant", engine.resolveValue("merchant.name", objectContext))
        assertEquals("INV-OBJ-1", engine.resolveValue("txn.invoiceId", objectContext))
        assertEquals("USD 12.50", engine.render(
            RctTemplateString(
                listOf(RctBindingPart("txn.amount", listOf(RctFilterCall("money", listOf("txn.currency")))))
            ),
            objectContext
        ))
    }
}
