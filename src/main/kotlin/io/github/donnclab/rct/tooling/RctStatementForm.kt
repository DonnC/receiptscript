package io.github.donnclab.rct.tooling

import io.github.donnclab.rct.ReceiptBarcodeFormat
import io.github.donnclab.rct.ReceiptFontScaleMode

/** A source property, not a resolved data value. Bindings and filters remain editable text. */
public data class RctSourceProperty(
    public val key: String,
    public val label: String,
    public val value: String,
    public val choices: List<String> = emptyList(),
    public val multiline: Boolean = false
)

/**
 * Core-owned property forms for supported source statements. [write] produces source only;
 * commit it through [RctSourceDocument] for contextual validation (for example table children).
 * Blank optional numeric fields mean inherit the engine default, not a Studio default.
 */
public class RctStatementForm private constructor(
    public val properties: List<RctSourceProperty>,
    private val encode: (Map<String, String>) -> String,
    /** Properties whose values are template strings, rather than literal settings. */
    public val bindingProperties: Set<String>,
    /** Properties that address context data directly, without interpolation braces. */
    public val pathProperties: Set<String>
) {
    public fun write(values: Map<String, String>): String = encode(
        properties.associate { it.key to it.value } + values
    )

    public companion object {
        /** Supported metadata plus existing host-specific keys, kept as raw RCT literals. */
        public fun metadata(document: RctSourceDocument): List<RctSourceProperty> {
            val meta = document.nodes.firstOrNull { it.depth == 0 && it.statement == "meta:" }
            val existing = document.nodes.filter { meta != null && it.parentId == meta.id }
                .associate { it.statement.substringBefore(':') to it.statement.substringAfter(':').trim() }
            val labels = linkedMapOf(
                "font" to "Base font size (dots)", "fontScaleMode" to "Font scaling",
                "width" to "Character columns", "paper" to "Paper label", "copy" to "Copy label",
                "templateVersion" to "Template version"
            )
            return (labels.keys + existing.keys).map { key ->
                RctSourceProperty(key, labels[key] ?: key, existing[key].orEmpty(),
                    if (key == "fontScaleMode") listOf("") + ReceiptFontScaleMode.entries.map { it.name.lowercase() } else emptyList())
            }
        }

        public fun read(statement: String): RctStatementForm {
            val kind = statement.substringBefore(' ').removeSuffix(":")
            val quoted = Regex("\"((?:\\\\.|[^\"])*)\"").findAll(statement).toList()
            val strings = quoted.map { it.groupValues[1] }
            val tail = quoted.lastOrNull()?.let { statement.substring(it.range.last + 1).trim() }.orEmpty()
            val fields = mutableListOf<RctSourceProperty>()
            fun field(key: String, label: String, value: String = "", choices: List<String> = emptyList(), multiline: Boolean = false) {
                fields += RctSourceProperty(key, label, value, choices, multiline)
            }
            fun quote(value: String): String {
                require(!value.contains('\n') && !value.contains('\r')) { "Use separate blocks for multiple text lines." }
                return "\"$value\""
            }
            fun optional(value: String): String = value.trim().let { if (it.isEmpty()) "" else " $it" }
            val writer: (Map<String, String>) -> String = when (kind) {
                "text", "left", "center", "right", "kv" -> {
                    val modifiers = statement.substringBefore('"').trim().split(Regex("\\s+")).drop(1)
                    if (kind != "kv") field("align", "Alignment", kind, listOf("left", "center", "right", "text"))
                    if (kind == "kv") {
                        field("label", "Label", strings.getOrElse(0) { "" })
                        field("value", "Value or binding", strings.getOrElse(1) { "" })
                    } else field("value", "Text or binding", strings.firstOrNull().orEmpty())
                    field("style", "Size style", modifiers.lastOrNull { it == "small" || it == "big" } ?: "normal", listOf("normal", "small", "big"))
                    field("bold", "Bold", ("bold" in modifiers).toString(), listOf("false", "true"))
                    field("underline", "Underline", ("underline" in modifiers).toString(), listOf("false", "true"))
                    field("font", "Explicit font size (dots, optional)", tail);
                    { values: Map<String, String> ->
                        buildString {
                            append(if (kind == "kv") "kv" else values.getValue("align"))
                            if (values["style"] != "normal") append(" ${values.getValue("style")}")
                            if (values["bold"] == "true") append(" bold")
                            if (values["underline"] == "true") append(" underline")
                            if (kind == "kv") append(" ${quote(values.getValue("label"))}")
                            append(" ${quote(values.getValue("value"))}")
                            append(optional(values.getValue("font")))
                        }
                    }
                }
                "image", "qr", "barcode" -> {
                    field("content", if (kind == "image") "Image reference or binding" else "Content or binding", strings.firstOrNull().orEmpty())
                    val numbers = statement.substring(quoted.firstOrNull()?.range?.last?.plus(1) ?: statement.length)
                        .substringBefore('"').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                    field("width", if (kind == "qr") "Size (dots, optional)" else "Width (dots, optional)", numbers.getOrElse(0) { "" })
                    if (kind != "qr") field("height", "Height (dots, optional)", numbers.getOrElse(1) { "" })
                    if (kind == "barcode") field("format", "Barcode format", strings.getOrElse(1) { "code128" }, ReceiptBarcodeFormat.entries.map { it.id });
                    { values: Map<String, String> ->
                        val width = values.getValue("width")
                        val height = values["height"].orEmpty()
                        require(height.isBlank() || width.isNotBlank()) { "Provide width before height." }
                        val dimensions = optional(width) + optional(height)
                        val format = if (kind == "barcode") " ${quote(values.getValue("format"))}" else ""
                        "$kind ${quote(values.getValue("content"))}$dimensions$format"
                    }
                }
                "divider" -> {
                    field("pattern", "Pattern (_ for a continuous line)", strings.firstOrNull() ?: "-")
                    field("length", "Character count (optional)", if (strings.isEmpty()) statement.removePrefix("divider").trim() else tail);
                    { values: Map<String, String> -> "divider ${quote(values.getValue("pattern"))}${optional(values.getValue("length"))}" }
                }
                "spacer" -> {
                    field("lines", "Blank lines", statement.substringAfter(' '));
                    { values: Map<String, String> -> "spacer ${values.getValue("lines")}" }
                }
                "receipt", "use" -> {
                    field("id", if (kind == "receipt") "Receipt name" else "Shared template ID", strings.firstOrNull().orEmpty())
                    val prefix = if (kind == "receipt") "receipt" else statement.substringBefore('"').trim();
                    { values: Map<String, String> -> "$prefix ${quote(values.getValue("id"))}" }
                }
                "row", "headers" -> {
                    field("columns", if (kind == "row") "Cells (one per line)" else "Headers (one per line)", strings.joinToString("\n"), multiline = true);
                    { values: Map<String, String> -> (if (kind == "row") "row " else "headers: ") + values.getValue("columns").lines().joinToString(" ", transform = ::quote) }
                }
                "if" -> {
                    field("path", "Show when this field is present", statement.removePrefix("if ").removeSuffix(":"));
                    { values: Map<String, String> -> "if ${values.getValue("path")}:" }
                }
                "for" -> {
                    field("item", "Item name", statement.removePrefix("for ").substringBefore(" in "))
                    field("path", "Repeat for collection", statement.substringAfter(" in ").removeSuffix(":"));
                    { values: Map<String, String> -> "for ${values.getValue("item")} in ${values.getValue("path")}:" }
                }
                else -> {
                    if (':' in statement && statement.substringAfter(':').isNotBlank()) {
                        val key = statement.substringBefore(':')
                        val choices = if (key == "fontScaleMode") listOf("relative", "fixed") else emptyList()
                        field("value", key.replaceFirstChar { it.uppercase() }, statement.substringAfter(':').trim(), choices);
                        { values: Map<String, String> -> "$key: ${values.getValue("value")}" }
                    } else {
                        { _: Map<String, String> -> statement }
                    }
                }
            }
            val bindings = when (kind) {
                "text", "left", "center", "right" -> setOf("value")
                "kv" -> setOf("label", "value")
                "image", "qr", "barcode" -> setOf("content")
                "row", "headers" -> setOf("columns")
                else -> emptySet()
            }
            return RctStatementForm(fields, writer, bindings, if (kind in setOf("if", "for")) setOf("path") else emptySet())
        }
    }
}
