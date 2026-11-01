package zw.co.donnclab.rct

import kotlin.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ReceiptRepositoryTest {
    @Test fun `repository may genuinely suspend without blocking the engine`() {
        var pending: Continuation<String>? = null
        var rendered: Receipt? = null
        var failure: Throwable? = null
        val repository = object : ReceiptTemplateRepository {
            override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String =
                suspendCoroutine { pending = it }
        }
        val job: suspend () -> Unit = { rendered = ReceiptScript().template("sale", repository).render(null) }
        job.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { failure = result.exceptionOrNull() }
        })
        assertNull(rendered)
        checkNotNull(pending).resume("receipt \"sale\"\nleft \"Ready\"")
        assertNull(failure)
        assertEquals("Ready", (checkNotNull(rendered).blocks.single() as ReceiptBlock.Text).text)
    }

    @Test fun `repository arguments reach template and fragments without retaining transaction data`() = immediate {
        val calls = mutableListOf<Pair<String, Map<String, Any?>>>()
        val repository = object : ReceiptTemplateRepository {
            override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String {
                calls += templateId to args
                return when (templateId) {
                    "sale" -> "receipt \"sale\"\nuse footer \"thanks\"\nleft \"{{name}}\""
                    else -> "center \"Thanks\""
                }
            }
        }
        val template = ReceiptScript().template("sale", repository, mapOf("merchant" to "M1"))
        assertEquals(listOf("sale", "thanks"), calls.map { it.first })
        assertTrue(calls.all { it.second == mapOf("merchant" to "M1") })
        assertEquals("A", (template.render(mapOf("name" to "A")).blocks.first() as ReceiptBlock.Text).text)
        assertEquals("B", (template.render(mapOf("name" to "B")).blocks.first() as ReceiptBlock.Text).text)
        assertEquals(2, calls.size)
    }

    @Test fun `fresh repository content produces a new fragment snapshot`() = immediate {
        var footer = "First"
        val repository = object : ReceiptTemplateRepository {
            override suspend fun getTemplate(templateId: String, args: Map<String, Any?>) = "center \"$footer\""
        }
        val engine = ReceiptScript()
        val source = "receipt \"sale\"\nuse footer \"footer\""
        val first = engine.compile(source, repository)
        footer = "Updated"
        val next = engine.compile(source, repository)
        assertEquals("First", (first.render(null).blocks.last() as ReceiptBlock.Text).text)
        assertEquals("Updated", (next.render(null).blocks.last() as ReceiptBlock.Text).text)
    }

    @Test fun `source only rendering has no repository dependency`() {
        val receipt = ReceiptScript().render("receipt \"sale\"\nleft \"{{name}}\"", mapOf("name" to "A"))
        assertEquals("A", (receipt.blocks.single() as ReceiptBlock.Text).text)
    }

    @Test fun `repository failures propagate unchanged`() = immediate {
        val failure = IllegalStateException("Offline")
        val repository = object : ReceiptTemplateRepository {
            override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String = throw failure
        }
        try {
            ReceiptScript().template("sale", repository)
            fail("Expected repository failure")
        } catch (actual: IllegalStateException) { assertSame(failure, actual) }
    }

    // These repositories complete immediately; no coroutine runtime is needed by the JVM engine.
    private fun immediate(block: suspend () -> Unit) {
        var outcome: Result<Unit>? = null
        block.startCoroutine(object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) { outcome = result }
        })
        checkNotNull(outcome).getOrThrow()
    }
}
