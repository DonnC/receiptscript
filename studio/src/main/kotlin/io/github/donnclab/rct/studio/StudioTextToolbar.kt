package io.github.donnclab.rct.studio

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Familiar editor glyphs as vectors, without shipping an entire expanded icon library. */
private fun editorIcon(name: String, path: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    .addPath(PathParser().parsePathString(path).toNodes(), pathFillType = PathFillType.EvenOdd, fill = SolidColor(Color.Black)).build()

private val alignLeft = editorIcon("AlignLeft", "M3,3H21V5H3Z M3,7H15V9H3Z M3,11H21V13H3Z M3,15H15V17H3Z M3,19H21V21H3Z")
private val alignCenter = editorIcon("AlignCenter", "M3,3H21V5H3Z M6,7H18V9H6Z M3,11H21V13H3Z M6,15H18V17H6Z M3,19H21V21H3Z")
private val alignRight = editorIcon("AlignRight", "M3,3H21V5H3Z M9,7H21V9H9Z M3,11H21V13H3Z M9,15H21V17H9Z M3,19H21V21H3Z")
private val boldIcon = editorIcon("Bold", "M7,4H13A5,5 0,0 1,16.5 12A5,5 0,0 1,13 20H7Z M10,7V10.5H13A1.75,1.75 0,0 0,13 7Z M10,13.5V17H13A1.75,1.75 0,0 0,13 13.5Z")
private val underlineIcon = editorIcon("Underline", "M6,3H9V11A3,3 0,0 0,15 11V3H18V11A6,6 0,0 1,6 11Z M4,20H20V22H4Z")
private val sizeIcon = editorIcon("TextSize", "M2,4H15V7H10V20H7V7H2Z M14,10H23V12H20V20H17V12H14Z")

@Composable
internal fun StudioTextToolbar(values: Map<String, String>, keys: Set<String>, onChange: (String, String) -> Unit) {
    var sizing by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf("") }
    var sizeError by remember { mutableStateOf<String?>(null) }
    val selectedColors = IconButtonDefaults.iconToggleButtonColors(
        checkedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        checkedContentColor = MaterialTheme.colorScheme.primary)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
        Row(Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "Text formatting" }, verticalAlignment = Alignment.CenterVertically) {
            if ("align" in keys) {
                listOf(Triple("left", "Align left", alignLeft), Triple("center", "Align centre", alignCenter), Triple("right", "Align right", alignRight)).forEach { (value, label, icon) ->
                    IconToggleButton(checked = (values["align"].let { if (it == "text") "left" else it }) == value,
                        onCheckedChange = { onChange("align", value) }, colors = selectedColors, modifier = Modifier.weight(1f)) { Icon(icon, label, Modifier.size(22.dp)) }
                }
                VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            }
            Box(Modifier.weight(1.35f), contentAlignment = Alignment.Center) {
                IconButton(onClick = { sizing = true }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(sizeIcon, "Text size", Modifier.size(22.dp)); StudioIcon(StudioSymbol.EXPAND, modifier = Modifier.size(12.dp)) }
                }
                DropdownMenu(sizing, { sizing = false }) {
                    listOf("small" to "Small", "normal" to "Normal", "big" to "Large").forEach { (value, label) ->
                        DropdownMenuItem(text = { Text(label) }, trailingIcon = if (values["font"].isNullOrBlank() && values["style"] == value) ({ StudioIcon(StudioSymbol.CHECK) }) else null,
                            onClick = { onChange("style", value); onChange("font", ""); sizing = false })
                    }
                    DropdownMenuItem(text = { Text(if (values["font"].isNullOrBlank()) "Custom dots..." else "Custom: ${values["font"]} dots") },
                        onClick = { size = values["font"].orEmpty(); sizeError = null; sizing = false; custom = true })
                }
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
            listOf(Triple("bold", "Bold", boldIcon), Triple("underline", "Underline", underlineIcon)).forEach { (key, label, icon) ->
                if (key in keys) IconToggleButton(checked = values[key] == "true", onCheckedChange = { onChange(key, it.toString()) },
                    colors = selectedColors, modifier = Modifier.weight(1f)) { Icon(icon, label, Modifier.size(22.dp)) }
            }
        }
    }
    if (custom) AlertDialog(onDismissRequest = { custom = false }, title = { Text("Custom text size") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioField("Size in dots", size, { size = it; sizeError = null }, numeric = true, placeholder = "Inherit template size")
            Text("An explicit size overrides the size style. Leave blank to inherit.", style = MaterialTheme.typography.bodySmall)
            sizeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            if (size.isBlank() || size.toFloatOrNull()?.let { it.isFinite() && it > 0 } == true) { onChange("font", size.trim()); custom = false }
            else sizeError = "Enter a positive size in dots."
        }) { Text("Apply") } }, dismissButton = { TextButton(onClick = { custom = false }) { Text("Cancel") } })
}
