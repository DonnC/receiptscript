package io.github.donnclab.rct.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.tooling.*

@Composable
internal fun StudioBlockPicker(document: RctSourceDocument, parentId: Int?, section: String,
                              onDestination: (Int?, String) -> Unit, onPick: (RctBlockExample) -> Unit,
                              error: String?, onClose: () -> Unit) {
    val sections = studioSections(document)
    val containers = document.nodes.filter { it.statement.endsWith(':') && it.statement !in setOf("meta:", "header:", "body:", "footer:") }
    val sectionMatch = sections.firstOrNull { parentId != null && it.container?.id == parentId }
    val selected = sectionMatch?.title ?: if (parentId == null) section else "node:$parentId"
    val destinations = sections.map { it.title to it.title } + containers.map { "node:${it.id}" to outlineTitle(document, it) }
    val table = document.nodes.firstOrNull { it.id == parentId }?.statement == "table:"
    val blocks = RctBlockPalette.entries.filter {
        it.title !in setOf("Header", "Body", "Footer") &&
            (!it.title.startsWith("Shared ") || parentId == null || sectionMatch != null) &&
            (it.title != "Shared header" || selected == "Header") && (it.title != "Shared footer" || selected == "Footer") &&
            (!table || it.title in setOf("Row", "Show when", "Repeat for"))
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Add a block", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onClose) { StudioIcon(StudioSymbol.CLOSE, "Close block picker") }
        }
        StudioDropdown("Add to", selected, destinations, { destination ->
            val target = sections.firstOrNull { it.title == destination }
            if (target != null) onDestination(target.container?.id, target.title)
            else onDestination(destination.removePrefix("node:").toInt(), section)
        })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyVerticalGrid(GridCells.Adaptive(140.dp), Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(blocks, key = { it.title }) { block ->
                OutlinedCard(onClick = { onPick(block) }, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 96.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StudioIcon(blockSymbol(block.source.lineSequence().first()))
                        Text(block.title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
