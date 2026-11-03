package io.github.donnclab.rct.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.tooling.*

internal fun deleteStudioBlock(document: RctSourceDocument, node: RctSourceNode): RctSourceDocument {
    val parent = document.nodes.firstOrNull { it.id == node.parentId }
    return if (parent?.statement in setOf("header:", "body:", "footer:") &&
        document.nodes.count { it.parentId == parent?.id } == 1) document.remove(parent!!) else document.remove(node)
}

@Composable
internal fun StudioSectionEditor(document: RctSourceDocument, section: String, onCommit: (RctSourceDocument) -> Unit,
                                 onAdd: (String) -> Unit, onLocalize: (String) -> Unit, onSelect: (RctSourceNode) -> Unit = {}) {
    val original = remember(document, section) { document.sectionSource(section) }
    var source by remember(document, section) { mutableStateOf(original) }
    val originalIds = remember(document, section) { document.nodes.filter { it.parentId == null && it.statement.startsWith("use $section ") }
        .map { RctStatementForm.read(it.statement).properties.single().value } }
    val ids = remember(document, section) { mutableStateListOf<String>().apply { addAll(originalIds) } }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Int?>(null) }
    val local = studioSections(document).first { it.title.equals(section, ignoreCase = true) }.nodes.filterNot { it.statement.startsWith("use ") }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 20.dp)) {
        Text("Edit ${section.replaceFirstChar { it.uppercase() }}", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Local blocks", style = MaterialTheme.typography.labelLarge)
            if (local.isEmpty()) Text("This section is empty. Add a block to start.", style = MaterialTheme.typography.bodySmall)
            local.forEach { node -> OutlinedCard(Modifier.fillMaxWidth().clickable { onSelect(node) }, shape = MaterialTheme.shapes.small) {
                Text(outlineTitle(document, node), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            } }
            OutlinedButton(onClick = { onAdd(section) }, shape = MaterialTheme.shapes.small) { StudioIcon(StudioSymbol.ADD); Spacer(Modifier.width(8.dp)); Text("Add $section block") }
            if (section != "body") {
                HorizontalDivider()
                Text("Reusable templates", style = MaterialTheme.typography.labelLarge)
                ids.forEachIndexed { index, id -> Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioField("Template ID", id, { ids[index] = it }, Modifier.weight(1f))
                    IconButton(onClick = { removing = index }) { StudioIcon(StudioSymbol.DELETE, "Remove shared $section ${index + 1}") }
                } }
                OutlinedButton(onClick = { ids.add("") }, shape = MaterialTheme.shapes.small) { Text("Add reusable $section") }
                if (originalIds.isNotEmpty()) {
                    Text("Shared templates are loaded through your repository. A local copy lets you edit their blocks here without modifying the original.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onLocalize(section) }, enabled = ids.toList() == originalIds) { Text("Edit shared blocks locally") }
                }
            }
            HorizontalDivider()
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide section source" else "Edit section source") }
            if (advanced) StudioCodeEditor(source, { source = it }, json = false, modifier = Modifier.fillMaxWidth().height(240.dp))
            if (original.isNotBlank()) TextButton(onClick = { clearing = true }) { Text("Clear local blocks") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = {
            try { onCommit(document.withSection(section, source, if (section == "body") null else ids.toList())) }
            catch (failure: IllegalArgumentException) { error = failure.message }
        }, enabled = ids.all { it.isNotBlank() }, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Text("Done") }
    }
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("Clear local $section blocks?") },
        text = { Text("Reusable references are kept. You can undo this change.") },
        confirmButton = { TextButton(onClick = { onCommit(document.withSection(section, "")); clearing = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } })
    removing?.let { index -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("Remove this shared $section?") },
        confirmButton = { TextButton(onClick = { ids.removeAt(index); removing = null }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } }) }
}
