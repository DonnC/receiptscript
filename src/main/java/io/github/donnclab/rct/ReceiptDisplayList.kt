package io.github.donnclab.rct

import android.graphics.Canvas

/** Screen drawing commands captured by the same layout pass that generates printer bands. */
internal class ReceiptDisplayList(private val width: Int, private val marks: List<ReceiptDrawMark>) {
    fun draw(canvas: Canvas, top: Int, height: Int) {
        canvas.save()
        canvas.clipRect(0, 0, width, height)
        canvas.translate(0f, -top.toFloat())
        marks.forEach { if (it.bottom > top && it.top < top + height) it.draw(canvas) }
        canvas.restore()
    }
}

internal data class ReceiptDrawMark(val top: Int, val bottom: Int, val draw: (Canvas) -> Unit)
