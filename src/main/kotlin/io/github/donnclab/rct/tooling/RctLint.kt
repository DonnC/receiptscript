package io.github.donnclab.rct.tooling

import io.github.donnclab.rct.DefaultRctParser
import io.github.donnclab.rct.RctParseException

/** A problem found in template source, with a 1-based [line] and [column]. */
public data class RctLintIssue(
    val message: String,
    val line: Int,
    val column: Int,
    val severity: RctLintSeverity = RctLintSeverity.ERROR
)

public enum class RctLintSeverity {
    WARNING,
    ERROR
}

/** Checks `.rct` source without rendering it, for editors and CI. */
public object RctLinter {
    public fun lint(source: String): List<RctLintIssue> {
        val issues = mutableListOf<RctLintIssue>()

        try {
            DefaultRctParser().parse(source)
        } catch (e: RctParseException) {
            issues += parseIssueFromException(e)
        }

        issues += scanLexicalIssues(source)
        return issues.distinct()
    }

    private fun scanLexicalIssues(source: String): List<RctLintIssue> {
        val lines = source.lines()
        return RctLexerScanner.scan(source)
            .filter { it.type == RctHighlightTokenType.BAD_CHARACTER }
            .map { token ->
                val (line, column) = locate(lines, token.start)
                RctLintIssue(
                    message = "Unterminated string or malformed interpolation.",
                    line = line,
                    column = column
                )
            }
    }

    private fun parseIssueFromException(exception: RctParseException): RctLintIssue {
        val line = Regex("""[Ll]ine (\d+)""").find(exception.message.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 1
        return RctLintIssue(
            message = exception.message ?: "Invalid RCT template.",
            line = line,
            column = 1
        )
    }

    private fun locate(lines: List<String>, offset: Int): Pair<Int, Int> {
        var remaining = offset
        lines.forEachIndexed { index, line ->
            val lineLengthWithBreak = line.length + 1
            if (remaining <= line.length) {
                return index + 1 to remaining + 1
            }
            remaining -= lineLengthWithBreak
        }
        return lines.size.coerceAtLeast(1) to 1
    }
}
