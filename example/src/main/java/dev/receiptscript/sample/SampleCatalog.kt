package dev.receiptscript.sample

import org.json.JSONArray
import org.json.JSONObject
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.ReceiptScript
import zw.co.donnclab.rct.ReceiptTemplateRepository

data class SampleExample(val id: String, val title: String, val description: String)
data class SampleDocument(val receipt: Receipt, val source: String, val contextJson: String)

/** The IDE and app consume the same templates and companion JSON, never a second mock layout. */
class SampleCatalog(private val readAsset: (String) -> String) {
    private val engine = ReceiptScript(loadTemplate = { id -> readAsset("receipts/$id.rct") })

    fun data(document: SampleDocument): Map<String, Any?> = objectMap(JSONObject(document.contextJson))

    fun open(example: SampleExample): SampleDocument {
        val source = readAsset("receipts/${example.id}.rct")
        val json = readAsset("receipts/${example.id}.preview.json")
        return SampleDocument(engine.compile(source).render(objectMap(JSONObject(json))), source, json)
    }

    /** Runtime loading exercises the same repository contract used by a database-backed host. */
    suspend fun open(example: SampleExample, templates: ReceiptTemplateRepository): SampleDocument {
        val source = templates.getTemplate(example.id)
        val json = readAsset("receipts/${example.id}.preview.json")
        val compiled = engine.compile(source, templates)
        return SampleDocument(compiled.render(objectMap(JSONObject(json))), source, json)
    }

    private fun objectMap(value: JSONObject): Map<String, Any?> = value.keys().asSequence()
        .associateWith { convert(value.get(it)) }

    private fun convert(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> objectMap(value)
        is JSONArray -> (0 until value.length()).map { convert(value.get(it)) }
        else -> value
    }

    companion object {
        val examples = listOf(
            SampleExample("basic", "A simple sale", "The smallest useful receipt. Start here."),
            SampleExample("bank", "Card payment", "Bank-style approval, masked PAN and trace references."),
            SampleExample("sme", "The neighbourhood store", "Itemised basket, quantities, tax and payment."),
            SampleExample("discount", "A better deal", "Line discounts, savings and final amount due."),
            SampleExample("shared", "One brand, many receipts", "Shared logo, header and footer fragments."),
            SampleExample("fiscal", "Fiscal-style invoice", "Synthetic fiscal fields and verification QR. Not certified."),
            SampleExample("daily-total", "Daily totals", "A compact multi-currency operations receipt."),
            SampleExample("complex", "The end-of-day report", "Long, multi-currency tables without mixing currency totals.")
        )
    }
}
