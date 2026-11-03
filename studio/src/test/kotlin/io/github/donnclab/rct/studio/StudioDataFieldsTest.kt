package io.github.donnclab.rct.studio

import io.github.donnclab.rct.tooling.RctSourceDocument
import org.junit.Assert.*
import org.junit.Test

class StudioDataFieldsTest {
    @Test fun `every key including arrays and nulls is indexed without a hundred key cutoff`() {
        val data = (0..130).associate { "field$it" to it } + mapOf("items" to listOf(mapOf("name" to "A", "optional" to null)))
        val fields = studioDataFields(data)
        assertTrue(fields.any { it.path == "field130" })
        assertTrue(fields.any { it.path == "items[0].name" })
        assertTrue(fields.any { it.path == "items[0].optional" })
        assertTrue(fields.any { it.path == "items" && it.type == "Array" })
    }

    @Test fun `repeating block properties can select item aliases from heterogeneous samples`() {
        val document = RctSourceDocument.parse("receipt \"x\"\nfor item in items:\n  kv \"Name\" \"{{item.name}}\"")
        val data = mapOf("items" to listOf(mapOf("name" to "A"), mapOf("amount" to 10)))
        val paths = studioDataFields(data, document, document.nodes.last()).map { it.path }
        assertTrue(paths.containsAll(listOf("item.name", "item.amount", "items[0].name", "items[1].amount")))
    }

    @Test fun `new JSON revisions expose new keys immediately`() {
        assertFalse(studioDataFields(StudioJson.parse("{}" )).any { it.path == "currency" })
        assertTrue(studioDataFields(StudioJson.parse("{\"currency\":\"USD\"}")).any { it.path == "currency" })
    }
}
