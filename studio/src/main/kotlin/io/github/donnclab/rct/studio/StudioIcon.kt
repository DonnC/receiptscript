package io.github.donnclab.rct.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class StudioSymbol { BACK, UNDO, REDO, MORE, CHECK, CHEVRON, ADD, MINUS, TEXT, TABLE, IMAGE, QR, BARCODE, LINK, SPACE, LINE, VALUE, CLOSE, EXPAND, SAVE, EXPORT, PRINT, DUPLICATE, DELETE, EDIT, ZOOM }

/** Small, theme-coloured geometric glyphs; no icon font or expanded icon dependency. */
@Composable
internal fun StudioIcon(symbol: StudioSymbol, description: String? = null, modifier: Modifier = Modifier.size(20.dp)) {
    val color = LocalContentColor.current
    Canvas(if (description == null) modifier else modifier.semantics { contentDescription = description }) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(x, y), Offset(x2, y2), 1.7f, StrokeCap.Round)
            fun box(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(1.5f), style = stroke)
            when (symbol) {
                StudioSymbol.BACK -> { line(19f,12f,5f,12f); line(5f,12f,11f,6f); line(5f,12f,11f,18f) }
                StudioSymbol.CHEVRON -> { line(9f,6f,15f,12f); line(15f,12f,9f,18f) }
                StudioSymbol.EXPAND -> { line(6f,9f,12f,15f); line(12f,15f,18f,9f) }
                StudioSymbol.MORE -> (0..2).forEach { drawCircle(color, 1.7f, Offset(12f, 5f + it * 7f)) }
                StudioSymbol.CHECK -> { drawCircle(color, 9f, Offset(12f,12f), style = stroke); line(7f,12f,10f,15f); line(10f,15f,17f,8f) }
                StudioSymbol.ADD -> { line(5f,12f,19f,12f); line(12f,5f,12f,19f) }
                StudioSymbol.MINUS -> line(5f,12f,19f,12f)
                StudioSymbol.CLOSE -> { line(6f,6f,18f,18f); line(6f,18f,18f,6f) }
                StudioSymbol.UNDO, StudioSymbol.REDO -> {
                    val redo = symbol == StudioSymbol.REDO
                    fun x(value: Float) = if (redo) 24f - value else value
                    val path = Path().apply { moveTo(x(5f),9f); lineTo(x(14f),9f); cubicTo(x(23f),9f,x(23f),19f,x(14f),19f); lineTo(x(11f),19f) }
                    drawPath(path, color, style = stroke); line(x(5f),9f,x(10f),4f); line(x(5f),9f,x(10f),14f)
                }
                StudioSymbol.TEXT -> { line(5f,5f,19f,5f); line(12f,5f,12f,19f); line(8f,19f,16f,19f) }
                StudioSymbol.TABLE -> { box(3f,4f,18f,16f); line(3f,10f,21f,10f); line(10f,4f,10f,20f); line(16f,4f,16f,20f) }
                StudioSymbol.IMAGE -> { box(3f,3f,18f,18f); drawCircle(color,2f,Offset(8f,8f),style=stroke); line(4f,19f,11f,12f); line(11f,12f,15f,16f); line(15f,16f,19f,12f) }
                StudioSymbol.QR -> { box(3f,3f,7f,7f); box(14f,3f,7f,7f); box(3f,14f,7f,7f); box(15f,15f,3f,3f); line(21f,14f,21f,21f); line(14f,21f,18f,21f) }
                StudioSymbol.BARCODE -> listOf(3f,6f,8f,12f,15f,17f,21f).forEach { line(it,4f,it,20f) }
                StudioSymbol.LINK -> { box(3f,9f,11f,8f); box(10f,4f,11f,8f); line(8f,14f,16f,9f) }
                StudioSymbol.SPACE -> { line(4f,4f,20f,4f); line(4f,20f,20f,20f); line(12f,7f,12f,17f); line(9f,9f,12f,6f); line(12f,6f,15f,9f); line(9f,15f,12f,18f); line(12f,18f,15f,15f) }
                StudioSymbol.LINE -> line(3f,12f,21f,12f)
                StudioSymbol.VALUE -> { line(4f,7f,10f,7f); line(15f,7f,20f,7f); line(4f,17f,10f,17f); line(15f,17f,20f,17f) }
                StudioSymbol.SAVE -> { box(4f,3f,16f,18f); box(8f,3f,8f,6f); box(8f,14f,8f,7f) }
                StudioSymbol.EXPORT -> { line(5f,13f,5f,21f); line(5f,21f,20f,21f); line(20f,21f,20f,13f); line(12f,16f,12f,3f); line(12f,3f,7f,8f); line(12f,3f,17f,8f) }
                StudioSymbol.PRINT -> { box(3f,8f,18f,10f); box(6f,3f,12f,5f); box(6f,14f,12f,7f) }
                StudioSymbol.DUPLICATE -> { box(8f,8f,13f,13f); line(4f,16f,4f,3f); line(4f,3f,17f,3f) }
                StudioSymbol.DELETE -> { box(6f,7f,12f,14f); line(4f,6f,20f,6f); line(9f,3f,15f,3f); line(10f,10f,10f,18f); line(14f,10f,14f,18f) }
                StudioSymbol.EDIT -> { line(5f,17f,17f,5f); line(8f,20f,20f,8f); line(17f,5f,20f,8f); line(5f,17f,4f,21f); line(4f,21f,8f,20f) }
                StudioSymbol.ZOOM -> { drawCircle(color,7f,Offset(10f,10f),style=stroke); line(15f,15f,21f,21f) }
            }
        }
    }
}

internal fun blockSymbol(statement: String): StudioSymbol = when (statement.substringBefore(' ').removeSuffix(":")) {
    "kv" -> StudioSymbol.VALUE
    "table", "row", "headers", "weights", "alignments" -> StudioSymbol.TABLE
    "image" -> StudioSymbol.IMAGE
    "qr" -> StudioSymbol.QR
    "barcode" -> StudioSymbol.BARCODE
    "use" -> StudioSymbol.LINK
    "spacer" -> StudioSymbol.SPACE
    "divider" -> StudioSymbol.LINE
    else -> StudioSymbol.TEXT
}
