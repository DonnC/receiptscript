package zw.co.donnclab.rct

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DefaultRctTemplateEngine : RctTemplateEngine {

    override fun render(template: RctTemplateString, context: RctRenderContext): String {
        return buildString {
            template.parts.forEach { part ->
                when (part) {
                    is RctLiteralPart -> append(part.text)
                    is RctBindingPart -> {
                        val resolved = resolveBinding(part, context)
                        if (resolved != null) {
                            append(stringify(resolved))
                        }
                    }
                }
            }
        }
    }

    override fun resolveValue(path: String, context: RctRenderContext): Any? {
        val trimmedPath = path.trim()
        if (trimmedPath.isEmpty()) return null

        if (trimmedPath == "@root") return context.root
        if (trimmedPath.startsWith("@root.")) {
            return resolveSegments(
                current = context.root,
                segments = parsePath(trimmedPath.removePrefix("@root."))
            )
        }
        if (context.locals.containsKey(trimmedPath)) return context.locals[trimmedPath]

        val segments = parsePath(trimmedPath)
        if (segments.isEmpty()) return null

        val first = segments.first()
        var current: Any? = when {
            context.locals.containsKey(first.name) -> context.locals[first.name]
            context.root is Map<*, *> -> context.root[first.name]
            context.root != null -> RctObjectAccess.read(context.root, first.name)
            else -> null
        }

        if (first.index != null) current = indexInto(current, first.index)
        return resolveSegments(current, segments.drop(1))
    }

    override fun isTruthy(path: String, context: RctRenderContext): Boolean {
        return isTruthy(resolveValue(path, context))
    }

    private fun resolveBinding(part: RctBindingPart, context: RctRenderContext): Any? {
        var value = resolveValue(part.path, context)
        part.filters.forEach { filter ->
            value = applyFilter(filter, value, context)
        }
        return value
    }

    private fun applyFilter(
        filter: RctFilterCall,
        value: Any?,
        context: RctRenderContext
    ): Any? {
        return when (filter.name) {
            "upper" -> stringify(value).uppercase()
            "lower" -> stringify(value).lowercase()
            "default" -> if (isTruthy(value)) value else resolveArg(filter.args.firstOrNull(), context)
            "maskPan" -> maskPan(stringify(value))
            "mask" -> mask(stringify(value), filter.args, context)
            "money" -> formatMoney(value, filter.args.firstOrNull()?.let { resolveArg(it, context) })
            "date" -> formatDate(value, filter.args, context)
            "datetime" -> formatDateTime(value, filter.args, context)
            "padLeft" -> pad(stringify(value), filter.args, context, PadDirection.LEFT)
            "padRight" -> pad(stringify(value), filter.args, context, PadDirection.RIGHT)
            "padCenter" -> pad(stringify(value), filter.args, context, PadDirection.CENTER)
            else -> throw RctParseException("Unsupported filter: ${filter.name}")
        }
    }

    private fun formatMoney(value: Any?, currency: Any?): String {
        val amount = when (value) {
            is BigDecimal -> value
            is Number -> value.toString().toBigDecimalOrNull()
            is String -> value.toBigDecimalOrNull()
            else -> null
        } ?: return stringify(value)

        val code = stringify(currency).ifBlank { "" }
        val normalized = amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
        return if (code.isBlank()) normalized else "$code $normalized"
    }

    private fun formatDate(
        value: Any?,
        args: List<String>,
        context: RctRenderContext
    ): String {
        val outputPattern = resolveDatePattern(args.getOrNull(0), context) ?: DEFAULT_DATE_PATTERN
        val inputPattern = resolveDatePattern(args.getOrNull(1), context)
        return toLocalDateTime(value, inputPattern)?.toLocalDate()?.format(DateTimeFormatter.ofPattern(outputPattern))
            ?: stringify(value)
    }

    private fun formatDateTime(
        value: Any?,
        args: List<String>,
        context: RctRenderContext
    ): String {
        val outputPattern = resolveDatePattern(args.getOrNull(0), context) ?: DEFAULT_DATETIME_PATTERN
        val inputPattern = resolveDatePattern(args.getOrNull(1), context)
        return toLocalDateTime(value, inputPattern)?.format(DateTimeFormatter.ofPattern(outputPattern))
            ?: stringify(value)
    }

    private fun toLocalDateTime(value: Any?, inputPattern: String? = null): LocalDateTime? {
        return when (value) {
            is LocalDateTime -> value
            is LocalDate -> value.atStartOfDay()
            is Instant -> LocalDateTime.ofInstant(value, ZoneId.systemDefault())
            is Number -> LocalDateTime.ofInstant(
                Instant.ofEpochMilli(value.toLong()),
                ZoneId.systemDefault()
            )

            is String -> parseDateTime(value, inputPattern)
            else -> null
        }
    }

    private fun parseDateTime(raw: String, inputPattern: String? = null): LocalDateTime? {
        return inputPattern?.let { parseDateTimeWithPattern(raw, it) }
            ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDate.parse(raw).atStartOfDay() }.getOrNull()
    }

    private fun parseDateTimeWithPattern(raw: String, pattern: String): LocalDateTime? {
        val formatter = DateTimeFormatter.ofPattern(pattern)
        return runCatching { LocalDateTime.parse(raw, formatter) }.getOrNull()
            ?: runCatching { LocalDate.parse(raw, formatter).atStartOfDay() }.getOrNull()
    }

    private fun resolveDatePattern(arg: String?, context: RctRenderContext): String? {
        return resolveArg(arg, context)?.toString()?.takeIf { it.isNotBlank() }
    }

    private fun maskPan(raw: String): String {
        val digits = raw.filter(Char::isDigit)
        if (digits.length < 10) return raw
        val first = digits.take(6)
        val last = digits.takeLast(4)
        return "$first******$last"
    }

    private fun mask(
        raw: String,
        args: List<String>,
        context: RctRenderContext
    ): String {
        if (raw.isEmpty()) return raw

        val showFirst = resolveArg(args.getOrNull(0), context)
            ?.toString()
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0
        val showLast = resolveArg(args.getOrNull(1), context)
            ?.toString()
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0
        val maskChar = resolveArg(args.getOrNull(2), context)
            ?.toString()
            ?.firstOrNull()
            ?: '*'

        val visibleCount = showFirst + showLast
        if (visibleCount <= 0 || visibleCount >= raw.length) {
            return maskChar.toString().repeat(raw.length)
        }

        val prefix = raw.take(showFirst)
        val suffix = raw.takeLast(showLast)
        val hiddenCount = raw.length - visibleCount
        return prefix + maskChar.toString().repeat(hiddenCount) + suffix
    }

    private fun pad(
        input: String,
        args: List<String>,
        context: RctRenderContext,
        direction: PadDirection
    ): String {
        val targetWidth = resolveArg(args.firstOrNull(), context)?.toString()?.toIntOrNull()
            ?: return input
        val padChar = resolveArg(args.getOrNull(1), context)?.toString()?.firstOrNull() ?: ' '
        if (input.length >= targetWidth) return input

        val missing = targetWidth - input.length
        return when (direction) {
            PadDirection.LEFT -> padChar.toString().repeat(missing) + input
            PadDirection.RIGHT -> input + padChar.toString().repeat(missing)
            PadDirection.CENTER -> {
                val left = missing / 2
                val right = missing - left
                padChar.toString().repeat(left) + input + padChar.toString().repeat(right)
            }
        }
    }

    private fun resolveArg(arg: String?, context: RctRenderContext): Any? {
        if (arg == null) return null
        val trimmed = arg.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        if (trimmed.startsWith("\\\"") && trimmed.endsWith("\\\"") && trimmed.length >= 4) {
            return trimmed.substring(2, trimmed.length - 2)
        }
        if (trimmed.toIntOrNull() != null) return trimmed.toInt()
        if (trimmed.toDoubleOrNull() != null) return trimmed.toDouble()
        return resolveValue(trimmed, context) ?: trimmed
    }

    private fun stringify(value: Any?): String = when (value) {
        null -> ""
        else -> value.toString()
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is String -> value.isNotBlank()
        is Collection<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        is Number -> value.toDouble() != 0.0
        else -> true
    }

    private fun descend(current: Any?, key: String): Any? {
        return when (current) {
            is Map<*, *> -> current[key]
            null -> null
            else -> RctObjectAccess.read(current, key)
        }
    }

    private fun indexInto(current: Any?, index: Int): Any? {
        return when (current) {
            is List<*> -> current.getOrNull(index)
            is Array<*> -> current.getOrNull(index)
            else -> null
        }
    }

    private fun resolveSegments(current: Any?, segments: List<PathSegment>): Any? {
        var value = current
        for (segment in segments) {
            value = descend(value, segment.name)
            if (segment.index != null) value = indexInto(value, segment.index)
            if (value == null) return null
        }
        return value
    }

    private fun parsePath(path: String): List<PathSegment> {
        return path.split('.')
            .filter { it.isNotBlank() }
            .map { rawSegment ->
                val match = PATH_SEGMENT.matchEntire(rawSegment)
                    ?: throw RctParseException("Invalid binding path: $path")
                PathSegment(
                    name = match.groupValues[1],
                    index = match.groupValues[3].takeIf { it.isNotBlank() }?.toInt()
                )
            }
    }

    private data class PathSegment(
        val name: String,
        val index: Int? = null
    )

    private enum class PadDirection {
        LEFT,
        RIGHT,
        CENTER
    }

    private companion object {
        private const val DEFAULT_DATE_PATTERN = "dd/MM/yyyy"
        private const val DEFAULT_DATETIME_PATTERN = "dd/MM/yyyy HH:mm:ss"
        private val PATH_SEGMENT = Regex("""([@A-Za-z_][@A-Za-z0-9_]*)(\[(\d+)])?""")
    }
}
