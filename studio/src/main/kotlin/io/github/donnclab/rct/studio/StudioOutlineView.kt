package io.github.donnclab.rct.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.donnclab.rct.tooling.*

private data class StudioDragHandle(val id: Int, val group: List<Int>, val bounds: Rect)

@Composable
internal fun Outline(document: RctSourceDocument, selected: Int?, onSelect: (RctSourceNode) -> Unit,
                     onAdd: (StudioSection) -> Unit, onReorder: (List<RctSourceNode>) -> Unit,
                     onDelete: (RctSourceNode) -> Unit, onDuplicate: (RctSourceNode) -> Unit,
                     onSection: (StudioSection) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    val drag = remember(document) { StudioDragState() }
    val expanded = remember(document) { mutableStateListOf<Int>() }
    val collapsed = remember { mutableStateListOf<String>() }
    val density = LocalDensity.current
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val handles = remember(document) { mutableMapOf<Int, StudioDragHandle>() }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun slots() = list.layoutInfo.visibleItemsInfo.mapNotNull { info -> (info.key as? Int)?.let { StudioDragSlot(it, info.offset, info.size) } }
    fun ordered(nodes: List<RctSourceNode>): List<RctSourceNode> {
        val replacements = drag.order.mapNotNull { id -> nodes.firstOrNull { it.id == id } }.iterator()
        return nodes.map { if (it.id in drag.order && replacements.hasNext()) replacements.next() else it }
    }
    LaunchedEffect(drag.activeId) {
        while (drag.activeId != null) {
            withFrameNanos { }
            val info = list.layoutInfo
            val edge = with(density) { 56.dp.toPx() }
            val speed = when {
                drag.pointerY < info.viewportStartOffset + edge -> -with(density) { 10.dp.toPx() }
                drag.pointerY > info.viewportEndOffset - edge -> with(density) { 10.dp.toPx() }
                else -> 0f
            }
            if (speed != 0f) list.scrollBy(speed)
            drag.updateTargets(slots())
        }
    }
    // Keep the gesture on the stable list, not an item that changes position during dragging.
    // Only handle touches are consumed; the rest of the list retains native scrolling/clicks.
    val gestures = Modifier.onGloballyPositioned { coordinates = it }.pointerInput(document) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val rootPosition = coordinates?.localToRoot(down.position)
            val handle = handles.values.firstOrNull { rootPosition != null && it.bounds.contains(rootPosition) }
            val slot = handle?.let { target -> slots().firstOrNull { it.id == target.id } }
            if (handle != null && slot != null) {
                down.consume()
                drag.start(handle.id, handle.group, slot.top, slot.height)
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            drag.finish()?.let { ids -> onReorder(ids.map { id -> document.nodes.first { it.id == id } }) }
                            break
                        }
                        drag.dragBy(change.positionChange().y, slots())
                        change.consume()
                    }
                } finally { drag.cancel() }
            }
        }
    }
    LazyColumn(modifier.fillMaxWidth().then(gestures), state = list, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fun LazyListScope.rows(nodes: List<RctSourceNode>, depth: Int = 0) {
            ordered(nodes).forEach { node ->
                item(key = node.id) {
                    DisposableEffect(node) { onDispose { handles.remove(node.id) } }
                    val active = drag.activeId == node.id
                    val animation = if (active) Modifier else Modifier.animateItem()
                    val children = outlineChildren(document, node)
                    OutlinedCard(onClick = { onSelect(node) }, shape = MaterialTheme.shapes.small,
                        modifier = animation.fillMaxWidth().padding(start = (depth.coerceAtMost(3) * 10).dp).zIndex(if (active) 1f else 0f)
                            .semantics {
                                val siblings = nodes.filter { it.parentId == node.parentId }
                                val index = siblings.indexOfFirst { it.id == node.id }
                                customActions = listOf(-1 to "Move up", 1 to "Move down").mapNotNull { (direction, label) ->
                                    val target = index + direction
                                    if (target !in siblings.indices) null else CustomAccessibilityAction(label) {
                                        onReorder(siblings.toMutableList().apply { this[index] = siblings[target]; this[target] = node })
                                        true
                                    }
                                }
                            }
                            .graphicsLayer {
                                translationY = if (active) drag.desiredTop - (list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == node.id }?.offset ?: 0) else 0f
                                shadowElevation = if (active) 8.dp.toPx() else 0f
                            },
                        colors = CardDefaults.outlinedCardColors(containerColor = if (active || selected == node.id)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.heightIn(min = 64.dp).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioIcon(blockSymbol(node.statement), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(outlineTitle(document, node), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(outlineSubtitle(document, node), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDuplicate(node) }) { StudioIcon(StudioSymbol.DUPLICATE, "Duplicate ${outlineTitle(document, node)}", Modifier.size(18.dp)) }
                            IconButton(onClick = { onDelete(node) }) { StudioIcon(StudioSymbol.DELETE, "Delete ${outlineTitle(document, node)}", Modifier.size(18.dp)) }
                            Box(Modifier.size(48.dp).semantics { contentDescription = "Drag to reorder ${outlineTitle(document, node)}" }
                                .onGloballyPositioned {
                                    handles[node.id] = StudioDragHandle(node.id, nodes.filter { sibling -> sibling.parentId == node.parentId }.map { sibling -> sibling.id }, it.boundsInRoot())
                                }.drawBehind {
                                    for (column in 0..1) for (row in 0..2) drawCircle(handleColor, 1.2.dp.toPx(),
                                        androidx.compose.ui.geometry.Offset(center.x + (column - 0.5f) * 5.dp.toPx(), center.y + (row - 1) * 5.dp.toPx()))
                                })
                            if (children.isNotEmpty()) IconButton(onClick = { if (node.id in expanded) expanded.remove(node.id) else expanded.add(node.id) }) {
                                StudioIcon(if (node.id in expanded) StudioSymbol.EXPAND else StudioSymbol.CHEVRON, "${if (node.id in expanded) "Collapse" else "Expand"} ${outlineTitle(document, node)}")
                            }
                        }
                    }
                }
                if (node.id in expanded) rows(outlineChildren(document, node), depth + 1)
            }
        }
        studioSections(document).forEach { section ->
            item(key = "section-${section.title}") {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.title.uppercase(), Modifier.weight(1f).clickable { if (section.title in collapsed) collapsed.remove(section.title) else collapsed.add(section.title) }.padding(vertical = 16.dp), style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { onAdd(section) }) { StudioIcon(StudioSymbol.ADD, "Add ${section.title} block") }
                    IconButton(onClick = { onSection(section) }) { StudioIcon(StudioSymbol.EDIT, "Edit ${section.title} section") }
                    IconButton(onClick = { if (section.title in collapsed) collapsed.remove(section.title) else collapsed.add(section.title) }) {
                        StudioIcon(if (section.title in collapsed) StudioSymbol.CHEVRON else StudioSymbol.EXPAND, "${if (section.title in collapsed) "Expand" else "Collapse"} ${section.title}")
                    }
                }
            }
            if (section.title !in collapsed) rows(section.nodes)
        }
    }
}
