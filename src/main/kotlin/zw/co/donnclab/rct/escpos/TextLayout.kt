package zw.co.donnclab.rct.escpos

import zw.co.donnclab.rct.RctColumnAlignment

/** Shared character-grid layout for single-byte native printer fonts. */
internal object TextLayout {
    fun wrap(value: String, width: Int): List<String> {
        require(width > 0)
        return value.replace("\r\n", "\n").split('\n').flatMap { paragraph ->
            var remaining = paragraph
            val lines = mutableListOf<String>()
            while (remaining.length > width) {
                val space = remaining.lastIndexOf(' ', width)
                val cut = if (space > 0) space else width
                lines += remaining.take(cut)
                remaining = remaining.drop(cut).removePrefix(" ")
            }
            lines + remaining
        }
    }

    fun align(value: String, width: Int, alignment: RctColumnAlignment): String {
        require(value.length <= width)
        val gap = width - value.length
        val before = when (alignment) {
            RctColumnAlignment.START -> 0
            RctColumnAlignment.CENTER -> gap / 2
            RctColumnAlignment.END -> gap
        }
        return " ".repeat(before) + value + " ".repeat(gap - before)
    }

    fun keyValue(key: String, value: String, width: Int): List<String> =
        if (!key.contains('\n') && !value.contains('\n') && key.length + value.length < width) {
            listOf(key + " ".repeat(width - key.length - value.length) + value)
        } else wrap(key, width) + wrap(value, width).map { align(it, width, RctColumnAlignment.END) }

    fun table(rows: List<List<String>>, weights: List<Float>?, alignments: List<RctColumnAlignment>?, width: Int): List<String> {
        if (rows.isEmpty()) return emptyList()
        val count = rows.first().size
        require(count > 0 && rows.all { it.size == count }) { "Table rows must have matching columns" }
        require(weights == null || weights.size == count && weights.all { it.isFinite() && it > 0 })
        require(alignments == null || alignments.size == count)
        val available = width - count + 1
        require(available >= count) { "Too many table columns for $width characters" }
        val effective = weights ?: List(count) { 1f }
        val total = effective.sumOf { it.toDouble() }
        val widths = effective.map { 1 + ((available - count) * it / total).toInt() }.toMutableList()
        repeat(available - widths.sum()) { widths[it % count]++ }
        return rows.flatMap { row ->
            val cells = row.mapIndexed { index, value -> wrap(value, widths[index]) }
            List(cells.maxOf { it.size }) { line ->
                cells.mapIndexed { index, cell ->
                    align(cell.getOrElse(line) { "" }, widths[index], alignments?.get(index) ?: RctColumnAlignment.CENTER)
                }.joinToString(" ")
            }
        }
    }
}
