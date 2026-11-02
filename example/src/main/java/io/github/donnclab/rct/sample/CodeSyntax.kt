package io.github.donnclab.rct.sample

import io.github.donnclab.rct.tooling.RctHighlightTokenType
import io.github.donnclab.rct.tooling.RctLexerScanner

enum class CodeLanguage { RCT, JSON }
enum class CodeKind { KEYWORD, STRING, BINDING, NUMBER, COMMENT, PROPERTY, LITERAL, PLAIN, ERROR }
data class CodeToken(val start: Int, val end: Int, val kind: CodeKind)

/** Highlighting only: source stays intact and template validation belongs to the engine. */
object CodeSyntax {
    private val jsonToken = Regex(""""(?:[^"\\]|\\.)*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b""")

    fun tokens(source: String, language: CodeLanguage): List<CodeToken> = when (language) {
        CodeLanguage.RCT -> RctLexerScanner.scan(source).map { token ->
            CodeToken(token.start, token.end, when (token.type) {
                RctHighlightTokenType.KEYWORD -> CodeKind.KEYWORD
                RctHighlightTokenType.STRING -> CodeKind.STRING
                RctHighlightTokenType.INTERPOLATION -> CodeKind.BINDING
                RctHighlightTokenType.NUMBER -> CodeKind.NUMBER
                RctHighlightTokenType.COMMENT -> CodeKind.COMMENT
                RctHighlightTokenType.BAD_CHARACTER -> CodeKind.ERROR
                else -> CodeKind.PLAIN
            })
        }
        CodeLanguage.JSON -> jsonToken.findAll(source).map { token ->
            val quoted = token.value.startsWith('"')
            val property = quoted && source.drop(token.range.last + 1).dropWhile(Char::isWhitespace).startsWith(':')
            CodeToken(token.range.first, token.range.last + 1, when {
                property -> CodeKind.PROPERTY
                quoted -> CodeKind.STRING
                token.value in setOf("true", "false", "null") -> CodeKind.LITERAL
                else -> CodeKind.NUMBER
            })
        }.toList()
    }
}
