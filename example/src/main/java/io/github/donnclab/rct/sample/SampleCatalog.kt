package io.github.donnclab.rct.sample

import io.github.donnclab.rct.RctCompiler
import io.github.donnclab.rct.ReceiptDocument
import org.json.JSONArray
import org.json.JSONObject

data class SampleExample(val id: String, val title: String, val description: String)

/** A template and the sample data VS Code previews it with. */
data class SampleSource(val source: String, val contextJson: String)

/**
 * The gallery reads the same `.rct` templates and `.preview.json` data that VS Code previews,
 * so the app never needs a second mock layout. Rendering itself goes through `ReceiptScript`.
 */
class SampleCatalog(private val readAsset: (String) -> String) {
    fun open(example: SampleExample): SampleSource =
        SampleSource(readAsset("receipts/${example.id}.rct"), readAsset("receipts/${example.id}.preview.json"))

    /** Preview JSON converted to plain maps and lists, the shape the template engine binds. */
    fun data(sample: SampleSource): Map<String, Any?> = objectMap(JSONObject(sample.contextJson))

    /** Printer-neutral blocks without Android rendering, for JVM unit tests. */
    fun document(example: SampleExample): ReceiptDocument {
        val sample = open(example)
        val compiler = RctCompiler(loadTemplate = { id -> readAsset("receipts/$id.rct") })
        return compiler.compile(sample.source).render(data(sample))
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
