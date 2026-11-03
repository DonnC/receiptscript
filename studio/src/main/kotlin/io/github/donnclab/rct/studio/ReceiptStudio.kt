package io.github.donnclab.rct.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.ReceiptScript
import io.github.donnclab.rct.compose.ReceiptPreview
import io.github.donnclab.rct.tooling.*
import kotlinx.coroutines.*

/**
 * A single-template workspace. The surrounding app owns identity, navigation, repositories,
 * image selection and persistence. This composable never installs a theme or stores a file.
 *
 * [onSave] receives the exact draft being saved. Throw to report failure; only a successful
 * return marks that snapshot saved. Newer edits stay dirty. Sample JSON may be an unfinished
 * draft when saving a valid template; preview and output always require valid JSON.
 *
 * [onPrint] and [onExport] receive the current core-rendered receipt, never a stale preview.
 * Supply neither for an editor-only integration. Hardware errors are reported, never retried.
 * [args] is forwarded unchanged to core for shared-fragment repository lookups.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptStudio(
    state: ReceiptStudioState,
    receiptScript: ReceiptScript,
    modifier: Modifier = Modifier,
    title: String = "Receipt Studio",
    args: Map<String, Any?> = emptyMap(),
    onClose: (() -> Unit)? = null,
    onSave: (suspend (ReceiptStudioDraft) -> Unit)? = null,
    onPrint: (suspend (Receipt) -> Unit)? = null,
    onExport: (suspend (Receipt) -> Unit)? = null,
    onPickImage: (suspend () -> String?)? = null
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sourceMode by rememberSaveable { mutableStateOf(false) }
    var selection by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var addSection by remember { mutableStateOf("Body") }
    var addParent by remember { mutableStateOf<Int?>(null) }
    var addError by remember { mutableStateOf<String?>(null) }
    var closing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var outputBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<RenderedDraft?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var dataError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val draft = state.draft
    val document = remember(draft.source) { runCatching { RctSourceDocument.parse(draft.source) }.getOrNull() }
    val sourceIssues = remember(draft.source) { if (document == null) RctLinter.lint(draft.source) else emptyList() }
    val selectedNode = document?.nodes?.firstOrNull { it.id == selection }
    val ready = result?.takeIf { it.draft == draft && it.engine === receiptScript && it.args == args && previewError == null && !rendering }?.receipt

    LaunchedEffect(draft, receiptScript, args) {
        rendering = true
        previewError = null
        dataError = null
        try {
            delay(300)
            val data = try { withContext(Dispatchers.Default) { StudioJson.parse(draft.dataJson) } }
                catch (failure: IllegalArgumentException) { dataError = failure.message; throw failure }
            require(document != null) { "Fix the template errors before previewing." }
            val receipt = receiptScript.renderSource(draft.source, data, args)
            ensureActive()
            result = RenderedDraft(draft, receipt, receiptScript, args.toMap())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            previewError = failure.message ?: "Preview could not be rendered."
        } finally {
            rendering = false
        }
    }

    fun commit(next: RctSourceDocument) {
        state.editSource(next.source)
        selection = null
    }
    fun output(action: suspend (Receipt) -> Unit) {
        val receipt = ready ?: return
        scope.launch {
            outputBusy = true
            try { action(receipt) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = "Output failed: ${failure.message}. Check the printer before retrying; part of the receipt may have printed." }
            finally { outputBusy = false }
        }
    }

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text(if (saving) "Saving..." else if (state.isDirty) "Unsaved changes" else "All changes saved", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (onClose != null) IconButton(enabled = !saving && !outputBusy, onClick = { if (state.isDirty) closing = true else onClose() }) { StudioIcon(StudioSymbol.BACK, "Back") }
                },
                actions = {
                    if (onSave != null) Button(shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(horizontal = 16.dp),
                        enabled = !saving && document != null && state.isDirty, onClick = {
                        val snapshot = state.draft
                        scope.launch {
                            saving = true
                            try { onSave(snapshot); state.markSaved(snapshot) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { message = failure.message ?: "Saving failed. Your edits are still here." }
                            finally { saving = false }
                        }
                    }) { Text("Save") }
                    Box {
                        IconButton(onClick = { menu = true }) { StudioIcon(StudioSymbol.MORE, "Studio options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Template settings") }, enabled = document != null, onClick = { menu = false; selection = null; settings = true })
                            DropdownMenuItem(text = { Text(if (sourceMode) "Visual designer" else "Template source") }, onClick = { menu = false; sourceMode = !sourceMode; selection = null; tab = 0 })
                            DropdownMenuItem(text = { Text("Receipt name") }, enabled = document != null, onClick = { menu = false; selection = document?.nodes?.firstOrNull()?.id; tab = 0 })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("Design", "Data", "Preview").forEachIndexed { index, name ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(name) })
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tab != 2) {
                    IconButton(onClick = { state.undo(); selection = null }, enabled = state.canUndo) { StudioIcon(StudioSymbol.UNDO, "Undo") }
                    IconButton(onClick = { state.redo(); selection = null }, enabled = state.canRedo) { StudioIcon(StudioSymbol.REDO, "Redo") }
                    VerticalDivider(Modifier.height(20.dp).padding(horizontal = 8.dp))
                }
                if (tab == 1) TextButton(onClick = {
                    try { state.editData(StudioJson.format(state.draft.dataJson)) }
                    catch (failure: IllegalArgumentException) { message = failure.message }
                }) { Text("Format JSON") }
                if (rendering) CircularProgressIndicator(Modifier.padding(horizontal = 8.dp).size(16.dp), strokeWidth = 1.5.dp)
                else if (ready != null) StudioIcon(StudioSymbol.CHECK, modifier = Modifier.padding(horizontal = 8.dp).size(18.dp))
                Text(when { rendering -> "Updating preview..."; ready != null -> if (tab == 2) "Preview up to date" else "Ready to preview"; else -> "Needs attention" },
                    Modifier.weight(1f).padding(horizontal = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
                val wide = maxWidth >= 1000.dp * textScale
                val split = maxWidth >= 700.dp * textScale && tab == 0
                val editorHeight = maxHeight
                when (tab) {
                    0 -> Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(if (split) 0.9f else 1f).fillMaxHeight()) {
                            if (sourceMode || document == null) {
                                sourceIssues.take(3).forEach { issue ->
                                    Text("Line ${issue.line}: ${issue.message}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error)
                                }
                                StudioCodeEditor(draft.source, state::editSource, json = false, modifier = Modifier.weight(1f).fillMaxWidth())
                            } else {
                                Outline(document, selection, { selection = it.id }, { section ->
                                    addParent = section.container?.id; addSection = section.title; adding = true; addError = null
                                }, { node, direction ->
                                    try { commit(document.move(node, direction)) }
                                    catch (failure: IllegalArgumentException) { message = failure.message }
                                }, Modifier.weight(1f))
                                Button(onClick = { addParent = studioSections(document)[1].container?.id; addSection = "Body"; adding = true; addError = null },
                                    shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) {
                                    StudioIcon(StudioSymbol.ADD); Spacer(Modifier.width(8.dp)); Text("Add block")
                                }
                            }
                        }
                        if (split) {
                            VerticalDivider()
                            PreviewPane(result?.receipt, ready != null, rendering, previewError, outputBusy,
                                onPrint?.let { { output(it) } }, onExport?.let { { output(it) } },
                                onFix = { tab = if (document == null) 0 else 1; sourceMode = document == null },
                                modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                        if (wide && selectedNode != null && !sourceMode) {
                            VerticalDivider()
                            StudioProperties(document, selectedNode, ::commit, { addParent = it.id; adding = true }, onPickImage,
                                Modifier.widthIn(max = 340.dp).weight(0.9f).fillMaxHeight(), draft.dataJson, onDismiss = { selection = null })
                        }
                    }
                    1 -> Column(Modifier.fillMaxSize()) {
                        Text("Sample data", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                        if (dataError != null) Text(dataError!!, Modifier.padding(16.dp).heightIn(max = 100.dp).verticalScroll(rememberScrollState()), color = MaterialTheme.colorScheme.error)
                        StudioCodeEditor(draft.dataJson, state::editData, json = true, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                    else -> PreviewPane(result?.receipt, ready != null, rendering, previewError, outputBusy,
                        onPrint?.let { { output(it) } }, onExport?.let { { output(it) } },
                        onFix = { tab = if (document == null) 0 else 1; sourceMode = document == null },
                        modifier = Modifier.fillMaxSize())
                }
                if (selectedNode != null && !(wide && tab == 0) && !adding) {
                    ModalBottomSheet(onDismissRequest = { selection = null }, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        StudioProperties(document, selectedNode, ::commit, { addParent = it.id; adding = true }, onPickImage,
                            Modifier.fillMaxWidth().heightIn(max = editorHeight).imePadding(), draft.dataJson, onDismiss = { selection = null })
                    }
                }
            }
        }
    }
    if (settings && document != null) ModalBottomSheet(onDismissRequest = { settings = false }, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        StudioSettings(document) { commit(it); settings = false }
    }
    if (adding && document != null) ModalBottomSheet(onDismissRequest = { adding = false }, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        StudioBlockPicker(document, addParent, addSection, { parent, section -> addParent = parent; addSection = section; addError = null }, { block ->
            try {
                val shared = block.source.startsWith("use ")
                val parent = if (shared) null else document.nodes.firstOrNull { it.id == addParent }
                val snippet = if (!shared && parent == null && addSection in setOf("Header", "Footer"))
                    "${addSection.lowercase()}:\n" + block.source.lines().joinToString("\n") { "  $it" } else block.source
                commit(document.append(snippet, parent))
                adding = false
            } catch (failure: IllegalArgumentException) { addError = failure.message }
        }, addError, { adding = false })
    }
    if (closing) AlertDialog(
        onDismissRequest = { closing = false }, title = { Text("Leave this draft?") },
        text = { Text("Changes have not been saved. Keep editing or leave without saving.") },
        confirmButton = { TextButton(onClick = { closing = false; onClose?.invoke() }) { Text("Leave") } },
        dismissButton = { TextButton(onClick = { closing = false }) { Text("Keep editing") } }
    )
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("Receipt Studio") },
        text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
}

private data class RenderedDraft(val draft: ReceiptStudioDraft, val receipt: Receipt, val engine: ReceiptScript, val args: Map<String, Any?>)

@Composable
private fun Outline(document: RctSourceDocument, selected: Int?, onSelect: (RctSourceNode) -> Unit,
                    onAdd: (StudioSection) -> Unit, onMove: (RctSourceNode, Int) -> Unit, modifier: Modifier) {
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val expanded = remember(document) { mutableStateListOf<Int>() }
    val collapsedSections = remember { mutableStateListOf<String>() }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        fun androidx.compose.foundation.lazy.LazyListScope.rows(nodes: List<RctSourceNode>, depth: Int = 0) {
            nodes.forEach { node ->
                item(key = node.id) {
                    val children = outlineChildren(document, node)
                    OutlinedCard(onClick = { onSelect(node) }, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(start = (depth.coerceAtMost(3) * 12).dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (selected == node.id)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f).compositeOver(MaterialTheme.colorScheme.surface) else MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.heightIn(min = 64.dp).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            StudioIcon(blockSymbol(node.statement))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(outlineTitle(document, node), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(outlineSubtitle(document, node), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.size(40.dp).semantics { contentDescription = "Hold and drag to reorder ${outlineTitle(document, node)}" }
                                .pointerInput(document, node) {
                                    var distance = 0f
                                    detectDragGesturesAfterLongPress(onDragStart = { distance = 0f },
                                        onDrag = { change, amount -> change.consume(); distance += amount.y },
                                        onDragEnd = { if (kotlin.math.abs(distance) > 24.dp.toPx()) onMove(node, if (distance < 0) -1 else 1) })
                                }.drawBehind {
                                    for (column in 0..1) for (row in 0..2) drawCircle(handleColor, 1.2.dp.toPx(),
                                        androidx.compose.ui.geometry.Offset(center.x + (column - 0.5f) * 5.dp.toPx(), center.y + (row - 1) * 5.dp.toPx()))
                                })
                            if (children.isNotEmpty()) IconButton(onClick = { if (node.id in expanded) expanded.remove(node.id) else expanded.add(node.id) }, modifier = Modifier.size(40.dp)) {
                                StudioIcon(if (node.id in expanded) StudioSymbol.EXPAND else StudioSymbol.CHEVRON,
                                    "${if (node.id in expanded) "Collapse" else "Expand"} ${outlineTitle(document, node)}")
                            } else StudioIcon(StudioSymbol.CHEVRON, modifier = Modifier.padding(end = 8.dp).size(16.dp))
                        }
                    }
                }
                if (node.id in expanded) rows(outlineChildren(document, node), depth + 1)
            }
        }
        studioSections(document).forEach { section ->
            item(key = "section-${section.title}") {
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(enabled = section.nodes.isNotEmpty()) {
                    if (section.title in collapsedSections) collapsedSections.remove(section.title) else collapsedSections.add(section.title)
                }.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(section.title.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (section.nodes.isEmpty()) TextButton(onClick = { onAdd(section) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Add ${section.title.lowercase()}", style = MaterialTheme.typography.labelMedium) }
                    else StudioIcon(if (section.title in collapsedSections) StudioSymbol.CHEVRON else StudioSymbol.EXPAND,
                        "${if (section.title in collapsedSections) "Expand" else "Collapse"} ${section.title}", Modifier.padding(8.dp).size(16.dp))
                }
            }
            if (section.title !in collapsedSections) rows(section.nodes)
        }
    }
}

@Composable
private fun PreviewPane(
    receipt: Receipt?, current: Boolean, loading: Boolean, error: String?, busy: Boolean,
    onPrint: (() -> Unit)?, onExport: (() -> Unit)?, onFix: () -> Unit, modifier: Modifier
) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    Column(modifier) {
        Text(if (current) "Preview with sample data" else if (loading) "Updating preview..." else "Previous preview · not current",
            Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (error != null) {
            Text(error, Modifier.padding(horizontal = 16.dp).heightIn(max = 100.dp).verticalScroll(rememberScrollState()), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onFix) { Text("Review template / data") }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).verticalScroll(rememberScrollState())) {
            val paperWidth = (maxWidth - 40.dp).coerceAtMost(380.dp).coerceAtLeast(40.dp)
            val viewportWidth = maxWidth
            if (receipt != null) {
                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
                    Box(Modifier.width((paperWidth * zoom + 40.dp).coerceAtLeast(viewportWidth)).padding(20.dp), contentAlignment = Alignment.TopCenter) {
                        Surface(Modifier.width(paperWidth * zoom), shape = MaterialTheme.shapes.extraSmall, shadowElevation = 4.dp) {
                            ReceiptPreview(receipt)
                        }
                    }
                }
            } else Text("Your rendered receipt will appear here.", Modifier.padding(24.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { zoom = 1f }, shape = MaterialTheme.shapes.small) { Text("Fit width", style = MaterialTheme.typography.labelMedium) }
            Spacer(Modifier.weight(1f))
            OutlinedCard(shape = MaterialTheme.shapes.small) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { zoom = (zoom - 0.25f).coerceAtLeast(0.5f) }) { StudioIcon(StudioSymbol.MINUS, "Zoom out") }
                    Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    IconButton(onClick = { zoom = (zoom + 0.25f).coerceAtMost(3f) }) { StudioIcon(StudioSymbol.ADD, "Zoom in") }
                }
            }
        }
        if (onPrint != null || onExport != null) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onExport != null) OutlinedButton(onClick = onExport, shape = MaterialTheme.shapes.small, enabled = current && !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Export") }
            if (onPrint != null) Button(onClick = onPrint, shape = MaterialTheme.shapes.small, enabled = current && !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (busy) "Working..." else "Print") }
        }
    }
}
