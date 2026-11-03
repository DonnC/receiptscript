package io.github.donnclab.rct.studio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import io.github.donnclab.rct.tooling.*

/** A deliberately small code editor: native selection, IME, scrolling and syntax colours. */
@Composable
internal fun StudioCodeEditor(value: String, onChange: (String) -> Unit, json: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val transform = remember(json, colors) {
        VisualTransformation { input ->
            val text = input.text
            val result = buildAnnotatedString {
                append(text)
                if (json) {
                    // Highlighting is tolerant while typing; acceptance belongs to the strict JSON parser.
                    Regex("\"(?:\\\\.|[^\"\\\\])*\"|\\b(?:true|false|null)\\b|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?|[{}\\[\\]:,]")
                        .findAll(text).forEach { token ->
                            var following = token.range.last + 1
                            while (following < text.length && text[following].isWhitespace()) following++
                            val color = when {
                                token.value.startsWith('"') && text.getOrNull(following) == ':' -> colors.primary
                                token.value.startsWith('"') -> colors.tertiary
                                token.value.first().isDigit() || token.value.first() == '-' -> colors.secondary
                                else -> colors.onSurfaceVariant
                            }
                            addStyle(SpanStyle(color = color), token.range.first, token.range.last + 1)
                        }
                } else RctLexerScanner.scan(text).forEach { token ->
                    val color = when (token.type) {
                        RctHighlightTokenType.KEYWORD -> colors.primary
                        RctHighlightTokenType.STRING -> colors.tertiary
                        RctHighlightTokenType.INTERPOLATION, RctHighlightTokenType.NUMBER -> colors.secondary
                        RctHighlightTokenType.BAD_CHARACTER -> colors.error
                        RctHighlightTokenType.COMMENT -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    }
                    addStyle(SpanStyle(color = color), token.start, token.end)
                }
            }
            TransformedText(result, OffsetMapping.Identity)
        }
    }
    val style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = colors.onSurface)
    Row(modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
        Text((1..(value.count { it == '\n' } + 1)).joinToString("\n"),
            Modifier.padding(horizontal = 12.dp), style = style.copy(color = colors.onSurfaceVariant))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = style,
            visualTransformation = transform,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.primary),
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 16.dp).defaultMinSize(minHeight = 160.dp)
        )
    }
}
