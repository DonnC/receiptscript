package io.github.donnclab.rct

internal interface RctParser {
    fun parse(source: String): RctDocument

    fun parseFragment(source: String): List<RctNode> {
        return parse(
            """
            receipt "__fragment__"
            $source
            """.trimIndent()
        ).body
    }
}

/** Thrown when template source is not valid RCT. The message includes the line number. */
public class RctParseException(message: String) : IllegalArgumentException(message)
