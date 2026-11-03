package io.github.donnclab.rct.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.tooling.*
import io.github.donnclab.rct.ReceiptColumnAlign
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun StudioProperties(
    document: RctSourceDocument,
    node: RctSourceNode,
    onCommit: (RctSourceDocument) -> Unit,
    onAdd: (RctSourceNode) -> Unit,
    onPickImage: (suspend () -> String?)?,
    modifier: Modifier = Modifier,
    dataJson: String = "{}",
    onDismiss: (() -> Unit)? = null,
    onDelete: (RctSourceNode) -> Unit = {},
    onLocalize: ((String) -> Unit)? = null
) {
    val form = remember(node) { RctStatementForm.read(node.statement) }
    val values = remember(node) { mutableStateMapOf<String, String>().apply { form.properties.forEach { put(it.key, it.value) } } }
    var error by remember(node) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var picking by remember { mutableStateOf(false) }
    var actions by remember(node) { mutableStateOf(false) }
    val fieldErrors = remember(node) { mutableStateMapOf<String, String>() }
    val table = remember(node) { if (node.statement == "table:") StudioTableDraft(document, node) else null }
    val fields = remember(dataJson, document, node) { runCatching { studioDataFields(StudioJson.parse(dataJson), document, node) }.getOrDefault(emptyList()) }
    fun change(action: () -> RctSourceDocument) {
        try {
            val next = action()
            focus.clearFocus()
            onCommit(next)
        } catch (failure: IllegalArgumentException) { error = failure.message }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(outlineTitle(document, node), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Box {
                IconButton(onClick = { actions = true }) { StudioIcon(StudioSymbol.MORE, "Block actions") }
                DropdownMenu(actions, { actions = false }) {
                    listOf("Move up" to { change { document.move(node, -1) } },
                        "Move down" to { change { document.move(node, 1) } },
                        "Duplicate" to { change { document.duplicate(node) } },
                        "Delete block" to { onDelete(node) }).forEach { (label, action) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { actions = false; action() })
                    }
                }
            }
            if (onDismiss != null) IconButton(onClick = onDismiss) { StudioIcon(StudioSymbol.CLOSE, "Close properties") }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (table != null) TableProperties(table, fields, { key, failure -> if (failure == null) fieldErrors.remove(key) else fieldErrors[key] = failure })
        form.properties.sortedBy { when (it.key) { "bold", "underline" -> 2; "font" -> 1; else -> 0 } }.forEach { property -> key(node, property.key) {
            val value = values.getValue(property.key)
            val changeValue: (String) -> Unit = { values[property.key] = it }
            when {
                property.choices.toSet() == setOf("true", "false") -> StudioToggle(property.label, value == "true", { changeValue(it.toString()) })
                property.key in form.bindingProperties && property.multiline -> StudioCellsField(value, fields, changeValue,
                    { failure -> if (failure == null) fieldErrors.remove(property.key) else fieldErrors[property.key] = failure })
                property.key in form.bindingProperties -> StudioValueField(
                    if (property.key == "label") "Label" else if (property.key == "content") "Content" else if (node.statement.startsWith("kv ")) "Value" else "Text",
                    value, fields, changeValue, { failure -> if (failure == null) fieldErrors.remove(property.key) else fieldErrors[property.key] = failure })
                property.key in form.pathProperties -> StudioReferenceField(property.label, value, fields.filterNot { '[' in it.path }, changeValue)
                property.choices.isNotEmpty() && property.choices.size <= 4 -> StudioChoices(if (property.key == "style") "Text size" else property.label, value, property.choices, changeValue)
                property.choices.isNotEmpty() -> StudioDropdown(property.label, value, property.choices.map { it to it.uppercase() }, changeValue)
                else -> StudioField(if (property.key == "font") "Size in dots (optional)" else property.label, value, changeValue,
                    multiline = property.multiline, numeric = property.key in setOf("font", "width", "height", "length", "lines"),
                    placeholder = if (property.key == "font") "Automatic" else null)
            }
        } }
        if (node.statement.startsWith("use ") && onLocalize != null) {
            Text("This section comes from another template. Make a local copy to edit its blocks here without changing the shared original.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onLocalize(if (node.statement.startsWith("use header")) "header" else "footer") },
                enabled = values["id"] == form.properties.firstOrNull { it.key == "id" }?.value) { Text("Edit local copy") }
        }
        if (node.statement.startsWith("image ") && onPickImage != null) {
            OutlinedButton(onClick = {
                scope.launch {
                    picking = true
                    try { onPickImage()?.let { values["content"] = it } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "Image selection failed." }
                    finally { picking = false }
                }
            }, enabled = !picking) { Text(if (picking) "Selecting image..." else "Choose image") }
        }
        if (node.statement.endsWith(':') && node.statement != "meta:") {
            OutlinedButton(onClick = { onAdd(node) }) { Text("Add block inside") }
        }
        Spacer(Modifier.height(4.dp))
        }
        (error ?: fieldErrors.values.firstOrNull())?.let { Text(it, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = { change { table?.apply(document, node) ?: document.replaceStatement(node, form.write(values.toMap())) } },
            enabled = fieldErrors.isEmpty(), shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).heightIn(min = 48.dp)) { Text("Done") }
    }
}

internal class StudioTableColumn(heading: String, width: String, alignment: ReceiptColumnAlign) {
    var heading by mutableStateOf(heading)
    var width by mutableStateOf(width)
    var alignment by mutableStateOf(alignment)
}

internal class StudioTableDraft(document: RctSourceDocument, node: RctSourceNode) {
    private val children = document.nodes.filter { it.parentId == node.id }
    private val headers = children.firstOrNull { it.statement.startsWith("headers:") }?.let { RctStatementForm.read(it.statement).properties.first().value.lines() }.orEmpty()
    private val widths = children.firstOrNull { it.statement.startsWith("weights:") }?.statement?.substringAfter(':')?.trim()?.split(Regex("\\s+")).orEmpty()
    private val aligns = children.firstOrNull { it.statement.startsWith("alignments:") }?.statement?.substringAfter(':')?.trim()?.split(Regex("\\s+")).orEmpty()
    private val rowSize = document.nodes.firstOrNull { it.id > node.id && it.id < (document.nodes.firstOrNull { next -> next.id > node.id && next.depth <= node.depth }?.id ?: Int.MAX_VALUE) && it.statement.startsWith("row ") }
        ?.let { RctStatementForm.read(it.statement).properties.first().value.lines().size } ?: 2
    var showHeaders by mutableStateOf(headers.isNotEmpty())
    var equalWidths by mutableStateOf(widths.isEmpty())
    var defaultAlignment by mutableStateOf(aligns.isEmpty())
    val columns = mutableStateListOf<StudioTableColumn>().apply {
        repeat(maxOf(2, headers.size, widths.size, aligns.size, rowSize)) { index ->
            add(StudioTableColumn(headers.getOrElse(index) { "Column ${index + 1}" }, widths.getOrElse(index) { "1" }, when (aligns.getOrNull(index)?.lowercase()) {
                "start", "left" -> ReceiptColumnAlign.START
                "end", "right" -> ReceiptColumnAlign.END
                else -> ReceiptColumnAlign.CENTER
            }))
        }
    }
    fun apply(document: RctSourceDocument, node: RctSourceNode): RctSourceDocument {
        return document.withTableOptions(node, if (showHeaders) columns.map { it.heading } else null,
            if (equalWidths) null else columns.map { it.width.toFloatOrNull() ?: throw IllegalArgumentException("Enter a numeric width for each column.") },
            if (defaultAlignment) null else columns.map { it.alignment })
    }
}

@Composable
private fun TableProperties(state: StudioTableDraft, fields: List<StudioDataField>, onError: (String, String?) -> Unit) {
    val headingErrors = remember(state) { mutableStateMapOf<StudioTableColumn, String>() }
    fun reportHeadings() = onError("headings", headingErrors.values.firstOrNull())
    StudioToggle("Show column headings", state.showHeaders, {
        state.showHeaders = it
        if (!it) { headingErrors.clear(); reportHeadings() }
    })
    StudioToggle("Equal column widths", state.equalWidths, { state.equalWidths = it })
    StudioToggle("Default alignment (centre)", state.defaultAlignment, { state.defaultAlignment = it })
    state.columns.forEachIndexed { index, column ->
        OutlinedCard(shape = MaterialTheme.shapes.small) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Column ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    if (state.columns.size > 2) IconButton(onClick = { state.columns.remove(column); headingErrors.remove(column); reportHeadings() }) { StudioIcon(StudioSymbol.CLOSE, "Remove column ${index + 1}") }
                }
                if (state.showHeaders) key(column) { StudioValueField("Heading", column.heading, fields, { column.heading = it }, {
                    if (it == null) headingErrors.remove(column) else headingErrors[column] = it
                    reportHeadings()
                }) }
                if (!state.equalWidths) StudioField("Relative width", column.width, { column.width = it }, numeric = true)
                if (!state.defaultAlignment) StudioDropdown("Alignment", column.alignment.name,
                    ReceiptColumnAlign.entries.map { it.name to when (it) { ReceiptColumnAlign.START -> "Left"; ReceiptColumnAlign.CENTER -> "Centre"; ReceiptColumnAlign.END -> "Right" } },
                    { column.alignment = ReceiptColumnAlign.valueOf(it) })
            }
        }
    }
    OutlinedButton(onClick = { state.columns.add(StudioTableColumn("Column ${state.columns.size + 1}", "1", ReceiptColumnAlign.CENTER)) }, shape = MaterialTheme.shapes.small) { Text("Add column") }
    Text("Keep row cells in the same order as these columns.", style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun StudioCellsField(value: String, fields: List<StudioDataField>, onChange: (String) -> Unit, onError: (String?) -> Unit) {
    class Cell(initial: String) { var text by mutableStateOf(initial) }
    val cells = remember { mutableStateListOf<Cell>().apply { value.lines().forEach { add(Cell(it)) } } }
    val errors = remember { mutableStateMapOf<Cell, String>() }
    cells.forEachIndexed { index, cell -> key(cell) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Column ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                if (cells.size > 2) IconButton(onClick = { cells.remove(cell); errors.remove(cell); onError(errors.values.firstOrNull()); onChange(cells.joinToString("\n") { it.text }) }) {
                    StudioIcon(StudioSymbol.CLOSE, "Remove cell ${index + 1}")
                }
            }
            StudioValueField("Cell value", cell.text, fields, { next -> cell.text = next; onChange(cells.joinToString("\n") { it.text }) },
                { failure -> if (failure == null) errors.remove(cell) else errors[cell] = failure; onError(errors.values.firstOrNull()) })
        }
    } }
    OutlinedButton(onClick = { cells.add(Cell("")); onChange(cells.joinToString("\n") { it.text }) }) { Text("Add column") }
}

internal fun blockTitle(statement: String): String = when (statement.substringBefore(' ').removeSuffix(":")) {
    "meta" -> "Template settings"
    "receipt" -> "Receipt name"
    "text", "left", "center", "right" -> "Text"
    "kv" -> "Label and value"
    "if" -> "Show when"
    "for" -> "Repeat for"
    "use" -> if (statement.startsWith("use header")) "Shared header" else "Shared footer"
    "qr" -> "QR code"
    "headers" -> "Table headers"
    "weights" -> "Column widths"
    "alignments" -> "Column alignments"
    else -> statement.substringBefore(' ').removeSuffix(":").replaceFirstChar { it.uppercase() }
}

internal fun blockSummary(document: RctSourceDocument, node: RctSourceNode): String {
    val properties = RctStatementForm.read(node.statement).properties.associate { it.key to it.value }
    fun readable(value: String): String = Regex("\\{\\{(.*?)\\}\\}").replace(value) { match ->
        match.groupValues[1].substringBefore('|').trim().split('.').joinToString(" ") { part ->
            part.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ')
        }.replaceFirstChar { it.uppercase() }
    }
    return when {
        node.statement == "meta:" -> "Font, columns and template defaults"
        node.statement == "header:" -> "Always rendered first"
        node.statement == "footer:" -> "Always rendered last, after footer spacing"
        node.statement == "body:" -> "Receipt content"
        node.statement == "table:" -> document.nodes.firstOrNull { it.parentId == node.id && it.statement.startsWith("headers:") }
            ?.let { RctStatementForm.read(it.statement).properties.first().value.replace("\n", " | ") } ?: "Rows with equally spaced, centred columns by default"
        node.statement.startsWith("divider") -> if (properties["pattern"] == "_") "Continuous line" else "Repeated ${properties["pattern"]} ${properties["length"].orEmpty()}".trim()
        node.statement.startsWith("spacer ") -> "${properties["lines"]} blank line(s)"
        node.statement.startsWith("if ") -> "${properties["path"]} is present"
        node.statement.startsWith("for ") -> "${properties["path"]} as ${properties["item"]}"
        else -> properties.filterKeys { it in setOf("label", "value", "content", "id", "columns") }
            .values.joinToString(" | ") { readable(it).replace("\n", " | ") }.ifBlank { node.statement }
    }
}

@Composable
internal fun StudioSettings(document: RctSourceDocument, onCommit: (RctSourceDocument) -> Unit) {
    val fields = remember(document) { RctStatementForm.metadata(document) }
    val values = remember(document) { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, it.value) } } }
    var error by remember(document) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Template settings", style = MaterialTheme.typography.titleLarge)
        Text("Blank values inherit core defaults. Inline font sizes override these settings.", style = MaterialTheme.typography.bodyMedium)
        fields.forEach { field ->
            if (field.choices.isEmpty()) OutlinedTextField(values.getValue(field.key), { values[field.key] = it }, label = { Text(field.label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            else {
                Text(field.label, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    field.choices.forEach { choice ->
                        FilterChip(selected = values[field.key] == choice, onClick = { values[field.key] = choice }, label = { Text(choice.ifEmpty { "Inherit" }) })
                    }
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            try { onCommit(document.withMetadata(values.mapValues { it.value.takeIf(String::isNotBlank) })) }
            catch (failure: IllegalArgumentException) { error = failure.message }
        }, modifier = Modifier.fillMaxWidth()) { Text("Apply settings") }
    }
}
