package io.github.donnclab.rct.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.tooling.*

@Composable
internal fun StudioToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .semantics(mergeDescendants = true) { contentDescription = label }
        .toggleable(checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun StudioField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
                         multiline: Boolean = false, numeric: Boolean = false, placeholder: String? = null) {
    OutlinedTextField(value, onChange, modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        textStyle = MaterialTheme.typography.bodyMedium, shape = MaterialTheme.shapes.small,
        singleLine = !multiline, minLines = if (multiline) 2 else 1,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text))
}

@Composable
internal fun StudioChoices(label: String, value: String, choices: List<String>, onChange: (String) -> Unit) {
    val options = when {
        choices.contains("left") -> choices.filterNot { it == "text" }
        choices.containsAll(listOf("small", "normal", "big")) -> listOf("small", "normal", "big")
        else -> choices
    }
    val selected = if (value == "text" && choices.contains("left")) "left" else value
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(selected = selected == option, onClick = { onChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size, MaterialTheme.shapes.small), icon = {},
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary, inactiveContainerColor = MaterialTheme.colorScheme.surface)) {
                    Text(when (option) { "normal" -> "Normal"; "big" -> "Large"; "" -> "Automatic"; else -> option.replaceFirstChar { it.uppercase() } },
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioDropdown(label: String, value: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(choices.firstOrNull { it.first == value }?.second ?: value,
            {}, readOnly = true, label = { Text(label, style = MaterialTheme.typography.bodySmall) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            textStyle = MaterialTheme.typography.bodyMedium, shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded, { expanded = false }) {
            choices.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(id); expanded = false }) }
        }
    }
}

private val formatters = listOf("" to "None", "money" to "Money", "date" to "Date", "datetime" to "Date & time",
    "upper" to "Uppercase", "lower" to "Lowercase", "maskPan" to "Masked card number")

/** Advanced/composite expressions stay intact; simple bindings get the familiar field/format UI. */
@Composable
internal fun StudioValueField(label: String, initial: String, dataJson: String, onChange: (String) -> Unit, onError: (String?) -> Unit) {
    var binding by remember { mutableStateOf(RctBindingForm.read(initial)?.takeIf { it.filters.size <= 1 && (it.filters.isEmpty() || formatters.any { option -> option.first == it.filters[0].name }) }) }
    var literal by remember { mutableStateOf(initial) }
    val paths = remember(dataJson) { runCatching { samplePaths(StudioJson.parse(dataJson)) }.getOrDefault(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    fun update(next: RctSourceBinding) {
        binding = next
        try { onChange(RctBindingForm.write(next)); onError(null) }
        catch (failure: IllegalArgumentException) { onError(failure.message) }
    }
    val current = binding
    Box {
        OutlinedTextField(current?.path ?: literal, {
            if (current != null) update(current.copy(path = it)) else { literal = it; onChange(it) }
        }, label = { Text(if (current != null) "$label field" else label, style = MaterialTheme.typography.bodySmall) },
            leadingIcon = if (current != null) ({ StudioIcon(StudioSymbol.LINK) }) else null,
            trailingIcon = { IconButton(onClick = { picking = true }) { StudioIcon(StudioSymbol.LINK, "Choose $label data field") } },
            singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        DropdownMenu(picking, { picking = false }) {
            if (current != null) DropdownMenuItem(text = { Text("Use plain text instead") }, onClick = {
                literal = ""; binding = null; onChange(""); onError(null); picking = false
            })
            if (paths.isEmpty()) DropdownMenuItem(text = { Text("Add fields in the Data tab first") }, onClick = { picking = false })
            paths.forEach { path -> DropdownMenuItem(text = { Text(path) }, onClick = { update((current ?: RctSourceBinding(path)).copy(path = path)); picking = false }) }
        }
    }
    if (current != null) {
        val filter = current.filters.firstOrNull()
        val formatter = filter?.name.orEmpty()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioDropdown("Formatter", formatter, formatters, { chosen ->
                update(current.copy(filters = if (chosen.isEmpty()) emptyList() else listOf(RctSourceFilter(chosen))))
            }, Modifier.weight(1f))
            if (formatter == "money") StudioField("Currency field or code", filter!!.args.firstOrNull().orEmpty(), { currency ->
                update(current.copy(filters = listOf(filter.copy(args = if (currency.isBlank()) emptyList() else listOf(currency)))))
            }, Modifier.weight(1f))
        }
        if (formatter == "date" || formatter == "datetime") {
            var advanced by remember { mutableStateOf(false) }
            TextButton(onClick = { advanced = !advanced }, contentPadding = PaddingValues(0.dp)) { Text("Custom date formats") }
            if (advanced) {
                val args = filter!!.args
                StudioField("Output format (optional)", args.getOrNull(0).orEmpty(), { value ->
                    update(current.copy(filters = listOf(filter.copy(args = listOf(value) + args.drop(1)))))
                })
                StudioField("Input format (optional)", args.getOrNull(1).orEmpty(), { value ->
                    update(current.copy(filters = listOf(filter.copy(args = listOf(args.firstOrNull().orEmpty(), value)))))
                })
            }
        }
    }
}

private fun samplePaths(value: Map<String, Any?>): List<String> {
    val paths = mutableListOf<String>()
    fun visit(map: Map<*, *>, prefix: String) {
        map.forEach { (key, child) ->
            if (paths.size >= 100) return
            val path = if (prefix.isEmpty()) key.toString() else "$prefix.$key"
            if (child is Map<*, *>) visit(child, path) else paths += path
        }
    }
    visit(value, "")
    return paths
}
