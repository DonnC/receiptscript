package zw.co.donnclab.rct

enum class RctHighlightTokenType {
    KEYWORD,
    IDENTIFIER,
    STRING,
    INTERPOLATION,
    NUMBER,
    COMMENT,
    PUNCTUATION,
    WHITESPACE,
    BAD_CHARACTER
}

data class RctLexeme(
    val type: RctHighlightTokenType,
    val start: Int,
    val end: Int
) {
    fun text(source: String): String = source.substring(start, end)
}

object RctLexerScanner {
    private val keywords = setOf(
        "receipt",
        "meta",
        "width",
        "paper",
        "font",
        "fontScaleMode",
        "header",
        "body",
        "footer",
        "use",
        "text",
        "left",
        "center",
        "right",
        "bold",
        "underline",
        "small",
        "big",
        "kv",
        "divider",
        "spacer",
        "if",
        "for",
        "in",
        "table",
        "row",
        "headers",
        "weights",
        "alignments",
        "qr",
        "barcode",
        "image"
    )

    fun scan(source: String): List<RctLexeme> {
        val tokens = mutableListOf<RctLexeme>()
        var index = 0

        while (index < source.length) {
            val start = index
            val current = source[index]

            when {
                current.isWhitespace() -> {
                    while (index < source.length && source[index].isWhitespace()) {
                        index++
                    }
                    tokens += RctLexeme(RctHighlightTokenType.WHITESPACE, start, index)
                }

                current == '#' -> {
                    while (index < source.length && source[index] != '\n') {
                        index++
                    }
                    tokens += RctLexeme(RctHighlightTokenType.COMMENT, start, index)
                }

                source.startsWith("{{", index) -> {
                    index = consumeInterpolation(source, index)
                    tokens += RctLexeme(
                        type = if (source.substring(start, index).endsWith("}}")) {
                            RctHighlightTokenType.INTERPOLATION
                        } else {
                            RctHighlightTokenType.BAD_CHARACTER
                        },
                        start = start,
                        end = index
                    )
                }

                current == '"' -> {
                    val stringTokens = consumeStringTokens(source, start)
                    tokens += stringTokens
                    index = stringTokens.last().end
                }

                current.isDigit() -> {
                    while (index < source.length && (source[index].isDigit() || source[index] == '.')) {
                        index++
                    }
                    tokens += RctLexeme(RctHighlightTokenType.NUMBER, start, index)
                }

                current.isIdentifierStart() -> {
                    while (index < source.length && source[index].isIdentifierPart()) {
                        index++
                    }
                    val word = source.substring(start, index)
                    tokens += RctLexeme(
                        type = if (word in keywords) {
                            RctHighlightTokenType.KEYWORD
                        } else {
                            RctHighlightTokenType.IDENTIFIER
                        },
                        start = start,
                        end = index
                    )
                }

                else -> {
                    index++
                    tokens += RctLexeme(
                        type = if (current in setOf(':', ',', '[', ']', '(', ')')) {
                            RctHighlightTokenType.PUNCTUATION
                        } else {
                            RctHighlightTokenType.BAD_CHARACTER
                        },
                        start = start,
                        end = index
                    )
                }
            }
        }

        return tokens
    }

    private fun consumeInterpolation(source: String, start: Int): Int {
        var index = start + 2
        while (index < source.length && !source.startsWith("}}", index)) {
            index++
        }
        return if (index < source.length) index + 2 else source.length
    }

    private fun consumeStringTokens(source: String, start: Int): List<RctLexeme> {
        val tokens = mutableListOf<RctLexeme>()
        var segmentStart = start
        var index = start + 1

        while (index < source.length) {
            when {
                source[index] == '\\' && index + 1 < source.length -> index += 2
                source.startsWith("{{", index) -> {
                    if (segmentStart < index) {
                        tokens += RctLexeme(RctHighlightTokenType.STRING, segmentStart, index)
                    }
                    val interpolationEnd = consumeInterpolation(source, index)
                    tokens += RctLexeme(
                        type = if (source.substring(index, interpolationEnd).endsWith("}}")) {
                            RctHighlightTokenType.INTERPOLATION
                        } else {
                            RctHighlightTokenType.BAD_CHARACTER
                        },
                        start = index,
                        end = interpolationEnd
                    )
                    index = interpolationEnd
                    segmentStart = index
                }

                source[index] == '"' -> {
                    tokens += RctLexeme(RctHighlightTokenType.STRING, segmentStart, index + 1)
                    return tokens
                }

                else -> index++
            }
        }

        tokens += RctLexeme(RctHighlightTokenType.BAD_CHARACTER, start, source.length)
        return tokens
    }
}

private fun Char.isIdentifierStart(): Boolean = isLetter() || this == '_' || this == '@'

private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_' || this == '@' || this == '.'
