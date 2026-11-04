package io.github.donnclab.rct.studio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The same searchable chooser serves text values, currency arguments and collection/condition paths. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudioReferenceField(label: String, value: String, fields: List<StudioDataField>, onChange: (String) -> Unit,
                                  modifier: Modifier = Modifier,
                                  onSelect: (String) -> Unit = onChange, onLiteral: (() -> Unit)? = null) {
    var picking by remember { mutableStateOf(false) }
    OutlinedTextField(value, onChange, label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        trailingIcon = { IconButton(onClick = { picking = true }) { StudioIcon(StudioSymbol.LINK, "Choose $label data field") } },
        singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = modifier.fillMaxWidth())
    if (picking) ModalBottomSheet(onDismissRequest = { picking = false }, containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        var query by remember { mutableStateOf("") }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Choose data field", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { picking = false }) { StudioIcon(StudioSymbol.CLOSE, "Close data fields") }
            }
            StudioField("Search fields", query, { query = it })
            if (onLiteral != null) TextButton(onClick = { onLiteral(); picking = false }) { Text("Use plain text instead") }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp)) {
                val matches = fields.filter { query.isBlank() || it.path.contains(query, ignoreCase = true) }
                if (matches.isEmpty()) item { Text(if (fields.isEmpty()) "Add valid sample JSON in Data to see fields." else "No matching fields.", Modifier.padding(16.dp)) }
                items(matches, key = { it.path }) { field ->
                    ListItem(headlineContent = { Text(field.path) }, supportingContent = { Text(field.type) },
                        leadingContent = { StudioIcon(StudioSymbol.LINK) }, trailingContent = if (field.path == value) ({ StudioIcon(StudioSymbol.CHECK) }) else null,
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(field.path); picking = false })
                }
            }
        }
    }
}

