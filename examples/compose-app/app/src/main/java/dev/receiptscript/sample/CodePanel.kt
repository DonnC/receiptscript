package dev.receiptscript.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CodeBackground = Color(0xFF18232A)
private val CodeForeground = Color(0xFFE2E9E9)

@Composable
fun CodePanel(source: String, filename: String, language: CodeLanguage) {
    val highlighted = remember(source, language) {
        buildAnnotatedString {
            append(source.trimEnd('\n', '\r'))
            CodeSyntax.tokens(source, language).forEach { token ->
                val end = token.end.coerceAtMost(length)
                if (token.start < end) addStyle(SpanStyle(color = when (token.kind) {
                    CodeKind.KEYWORD -> Color(0xFF7DDCC6)
                    CodeKind.STRING -> Color(0xFFE8C695)
                    CodeKind.BINDING -> Color(0xFF9CCBFF)
                    CodeKind.PROPERTY -> Color(0xFF9CCBFF)
                    CodeKind.NUMBER -> Color(0xFFFFB996)
                    CodeKind.COMMENT -> Color(0xFF8EA6A4)
                    CodeKind.LITERAL -> Color(0xFF7DDCC6)
                    CodeKind.ERROR -> Color(0xFFFF9B9B)
                    CodeKind.PLAIN -> CodeForeground
                }), token.start, end)
            }
        }
    }
    val lineCount = source.trimEnd('\n', '\r').count { it == '\n' } + 1
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(CodeBackground)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF24333C)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(filename, color = CodeForeground, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(language.name, color = Color(0xFF7DDCC6), fontSize = 11.sp)
        }
        Row(Modifier.padding(vertical = 16.dp)) {
            Text((1..lineCount).joinToString("\n"), Modifier.padding(start = 12.dp, end = 12.dp),
                color = Color(0xFF8EA6A4), fontSize = 12.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)
            SelectionContainer(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Text(highlighted, Modifier.padding(end = 18.dp), color = CodeForeground,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 20.sp, softWrap = false)
            }
        }
    }
}
