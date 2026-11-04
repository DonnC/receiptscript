package io.github.donnclab.rct.studio

import io.github.donnclab.rct.RctCompiler
import io.github.donnclab.rct.ReceiptBlock
import io.github.donnclab.rct.tooling.RctStatementForm

private val toolbarProperties = setOf("align", "style", "font", "bold", "underline")

/** Core remains the authority on which style properties a statement can write. */
internal fun studioToolbarKeys(statement: String): Set<String> =
    RctStatementForm.read(statement).properties.map { it.key }.toSet().intersect(toolbarProperties)

internal fun studioAllowsFormat(statement: String, property: String): Boolean =
    property in RctStatementForm.read(statement).bindingProperties &&
        statement.substringBefore(' ').removeSuffix(":") !in setOf("image", "qr", "barcode")

internal fun studioMaskError(args: List<String>): String? {
    val path = Regex("[A-Za-z_][A-Za-z0-9_.]*(?:\\[\\d+])?(?:\\.[A-Za-z_][A-Za-z0-9_]*)*")
    for (value in args.take(2)) {
        if (value.toIntOrNull()?.let { it >= 0 } != true && !path.matches(value)) return "Visible counts must be non-negative whole numbers or data fields."
    }
    val character = studioMaskCharacter(args.getOrNull(2))
    if (character.length != 1 && !path.matches(character)) return "Choose one mask character or a data field."
    if (character.any { it in ",|{}\"\r\n" }) return "This character cannot be used in a mask binding."
    return null
}

internal fun studioMaskCharacter(argument: String?): String =
    argument?.removeSurrounding("\\\"")?.removeSurrounding("\"") ?: "*"

internal fun studioMaskArguments(first: String, last: String, character: String): List<String> =
    listOf(first, last) + when {
        character == "*" -> emptyList()
        character.length == 1 -> listOf("\\\"$character\\\"")
        else -> listOf(character)
    }

/** Preview the actual core result, including masking; never duplicate formatting rules in Studio. */
internal fun studioValuePreview(value: String, data: Any?): String? = runCatching {
    (RctCompiler().render("receipt \"studio_field\"\nleft \"$value\"", data).blocks.single() as ReceiptBlock.Text).text.take(160)
}.getOrNull()
