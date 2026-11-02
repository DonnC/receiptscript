package io.github.donnclab.rct

internal class DefaultRctParser : RctParser {

    override fun parse(source: String): RctDocument {
        val lines = source.toSourceLines()

        val state = ParserState(lines)
        val declaration = state.nextMeaningfulLine()
            ?: throw RctParseException("Receipt declaration is required.")

        val declarationMatch = RECEIPT_DECLARATION.matchEntire(declaration.content.trim())
            ?: throw RctParseException("Invalid receipt declaration at line ${declaration.number}")

        val meta = parseMeta(state)
        val content = parseDocumentContent(state)

        return RctDocument(
            name = declarationMatch.groupValues[1],
            meta = meta,
            body = content.body,
            header = content.header,
            footer = content.footer,
            headerReferences = content.headerReferences,
            footerReferences = content.footerReferences
        )
    }

    override fun parseFragment(source: String): List<RctNode> {
        return parseBlock(ParserState(source.toSourceLines()), indentLevel = 0)
    }

    private fun String.toSourceLines(): List<SourceLine> {
        return replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .mapIndexed { index, raw ->
                if (raw.contains('\t')) {
                    throw RctParseException("Tabs are not allowed in .rct files. Line ${index + 1}")
                }
                SourceLine(index + 1, raw)
            }
    }

    private fun parseMeta(state: ParserState): ReceiptMeta? {
        val line = state.peekMeaningfulLine() ?: return null
        if (line.content.trim() != "meta:") return null

        state.consumeMeaningfulLine()

        val entries = linkedMapOf<String, String>()
        while (true) {
            val next = state.peekMeaningfulLine() ?: break
            val indent = next.indentLevel()
            if (indent == 0) break
            if (indent != 1) {
                throw RctParseException("Meta entries must be indented by 2 spaces. Line ${next.number}")
            }

            val entry = state.consumeMeaningfulLine()
                ?: throw RctParseException("Unexpected end of meta block.")
            val match = META_ENTRY.matchEntire(entry.content.trim())
                ?: throw RctParseException("Invalid meta entry at line ${entry.number}")
            entries[match.groupValues[1]] = unquote(match.groupValues[2].trim())
        }

        return ReceiptMeta(
            width = entries["width"]?.toIntOrNull(),
            paper = entries["paper"],
            copy = entries["copy"],
            templateVersion = entries["templateVersion"]?.toIntOrNull(),
            font = entries["font"]?.let(::parseFont),
            fontScaleMode = entries["fontScaleMode"]?.let(::parseFontScaleMode) ?: ReceiptFontScaleMode.RELATIVE,
            extra = entries.filterKeys {
                it !in setOf("width", "paper", "copy", "templateVersion", "font", "fontScaleMode")
            }
        )
    }

    private fun parseDocumentContent(state: ParserState): DocumentContent {
        val header = mutableListOf<RctNode>()
        val body = mutableListOf<RctNode>()
        val footer = mutableListOf<RctNode>()
        val headerReferences = mutableListOf<RctFragmentReference>()
        val footerReferences = mutableListOf<RctFragmentReference>()
        val seenSections = mutableSetOf<String>()

        while (true) {
            val next = state.peekMeaningfulLine() ?: break
            val currentIndent = next.indentLevel()
            if (currentIndent != 0) {
                throw RctParseException("Unexpected indentation at line ${next.number}")
            }

            val trimmed = next.content.trim()
            val useFragmentMatch = USE_FRAGMENT_STATEMENT.matchEntire(trimmed)
            if (useFragmentMatch != null) {
                state.consumeMeaningfulLine()
                val reference = RctFragmentReference(useFragmentMatch.groupValues[2])
                when (useFragmentMatch.groupValues[1]) {
                    "header" -> headerReferences += reference
                    "footer" -> footerReferences += reference
                }
                continue
            }

            when (trimmed) {
                "header:" -> {
                    parseSection(state, next, "header", seenSections).also { header += it }
                    continue
                }

                "body:" -> {
                    parseSection(state, next, "body", seenSections).also { body += it }
                    continue
                }

                "footer:" -> {
                    parseSection(state, next, "footer", seenSections).also { footer += it }
                    continue
                }
            }

            val line = state.consumeMeaningfulLine()
                ?: throw RctParseException("Unexpected end of input.")
            body += parseNode(line, state, indentLevel = 0)
        }

        return DocumentContent(
            header = header,
            body = body,
            footer = footer,
            headerReferences = headerReferences,
            footerReferences = footerReferences
        )
    }

    private fun parseSection(
        state: ParserState,
        line: SourceLine,
        section: String,
        seenSections: MutableSet<String>
    ): List<RctNode> {
        if (!seenSections.add(section)) {
            throw RctParseException("Duplicate $section section at line ${line.number}")
        }
        state.consumeMeaningfulLine()
        val next = state.peekMeaningfulLine()
            ?: throw RctParseException("Expected indented block after line ${line.number}")
        if (next.indentLevel() != 1) {
            throw RctParseException("Expected indented block after line ${line.number}")
        }
        return parseBlock(state, indentLevel = 1)
    }

    private fun parseBlock(state: ParserState, indentLevel: Int): List<RctNode> {
        val nodes = mutableListOf<RctNode>()

        while (true) {
            val next = state.peekMeaningfulLine() ?: break
            val currentIndent = next.indentLevel()
            if (currentIndent < indentLevel) break
            if (currentIndent > indentLevel) {
                throw RctParseException("Unexpected indentation at line ${next.number}")
            }

            val line = state.consumeMeaningfulLine()
                ?: throw RctParseException("Unexpected end of input.")
            nodes += parseNode(line, state, indentLevel)
        }

        return nodes
    }

    private fun parseNode(line: SourceLine, state: ParserState, indentLevel: Int): RctNode {
        val trimmed = line.content.trim()

        DIVIDER_STATEMENT.matchEntire(trimmed)?.let { match ->
            val pattern = match.groupValues[1].takeIf { it.isNotBlank() } ?: "-"
            val length = match.groupValues[2].takeIf { it.isNotBlank() }?.toInt()
            return RctDividerNode(pattern = pattern, length = length)
        }

        SPACER_STATEMENT.matchEntire(trimmed)?.let { match ->
            return RctSpacerNode(lines = match.groupValues[1].toInt())
        }

        IF_STATEMENT.matchEntire(trimmed)?.let { match ->
            val children = parseNestedBlock(state, line, indentLevel)
            return RctIfNode(
                path = match.groupValues[1].trim(),
                children = children
            )
        }

        FOR_STATEMENT.matchEntire(trimmed)?.let { match ->
            val children = parseNestedBlock(state, line, indentLevel)
            return RctForEachNode(
                itemName = match.groupValues[1].trim(),
                collectionPath = match.groupValues[2].trim(),
                children = children
            )
        }

        KEY_VALUE_STATEMENT.matchEntire(trimmed)?.let { match ->
            val modifiers = parseModifiers(
                raw = match.groupValues[1],
                trailingFontSize = match.groupValues[4].takeIf { it.isNotBlank() }?.toFloat(),
                lineNumber = line.number
            )
            return RctKeyValueNode(
                key = parseTemplateString(match.groupValues[2]),
                value = parseTemplateString(match.groupValues[3]),
                fontSize = modifiers.fontSize,
                bold = modifiers.bold,
                underline = modifiers.underline,
                customFontSize = modifiers.customFontSize
            )
        }

        if (trimmed == "table:") {
            return parseTableBlock(state, line, indentLevel)
        }

        if (trimmed.startsWith("row ")) {
            val columns = extractQuotedArguments(trimmed.removePrefix("row").trim(), "row", line.number)
            if (columns.size < 2) {
                throw RctParseException("Row statements need at least 2 columns. Line ${line.number}")
            }
            return RctRowNode(columns = columns.map(::parseTemplateString))
        }

        QR_STATEMENT.matchEntire(trimmed)?.let { match ->
            return RctQrNode(
                content = parseTemplateString(match.groupValues[1]),
                size = match.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 300
            )
        }

        BARCODE_STATEMENT.matchEntire(trimmed)?.let { match ->
            return RctBarcodeNode(
                content = parseTemplateString(match.groupValues[1]),
                width = match.groupValues[2].takeIf { it.isNotBlank() }?.toInt() ?: 300,
                height = match.groupValues[3].takeIf { it.isNotBlank() }?.toInt() ?: 60,
                format = match.groupValues[4].takeIf { it.isNotBlank() }?.let { id ->
                    ReceiptBarcodeFormat.entries.firstOrNull { it.id == id }
                        ?: throw RctParseException("Unsupported barcode format: $id")
                } ?: ReceiptBarcodeFormat.CODE128
            )
        }

        IMAGE_STATEMENT.matchEntire(trimmed)?.let { match ->
            return RctImageNode(
                source = parseTemplateString(match.groupValues[1]),
                width = match.groupValues[2].takeIf { it.isNotBlank() }?.toInt(),
                height = match.groupValues[3].takeIf { it.isNotBlank() }?.toInt()
            )
        }

        TEXT_STATEMENT.matchEntire(trimmed)?.let { match ->
            val keyword = match.groupValues[1]
            val modifiers = parseModifiers(
                raw = match.groupValues[2],
                trailingFontSize = match.groupValues[4].takeIf { it.isNotBlank() }?.toFloat(),
                lineNumber = line.number
            )
            val text = match.groupValues[3]

            return RctTextNode(
                text = parseTemplateString(text),
                align = when (keyword) {
                    "text", "left" -> ReceiptAlign.LEFT
                    "center" -> ReceiptAlign.CENTER
                    "right" -> ReceiptAlign.RIGHT
                    else -> throw RctParseException("Unsupported text statement '$keyword' at line ${line.number}")
                },
                bold = modifiers.bold,
                underline = modifiers.underline,
                fontSize = modifiers.fontSize,
                customFontSize = modifiers.customFontSize
            )
        }

        throw RctParseException("Unsupported statement at line ${line.number}: $trimmed")
    }

    private fun parseNestedBlock(
        state: ParserState,
        line: SourceLine,
        parentIndentLevel: Int
    ): List<RctNode> {
        val next = state.peekMeaningfulLine()
            ?: throw RctParseException("Expected indented block after line ${line.number}")
        if (next.indentLevel() != parentIndentLevel + 1) {
            throw RctParseException("Expected indented block after line ${line.number}")
        }
        return parseBlock(state, parentIndentLevel + 1)
    }

    private fun parseTableBlock(
        state: ParserState,
        line: SourceLine,
        parentIndentLevel: Int
    ): RctTableNode {
        val next = state.peekMeaningfulLine()
            ?: throw RctParseException("Expected indented block after line ${line.number}")
        val tableIndent = parentIndentLevel + 1
        if (next.indentLevel() != tableIndent) {
            throw RctParseException("Expected indented block after line ${line.number}")
        }

        val headers = mutableListOf<RctTemplateString>()
        var weights: List<Float>? = null
        var alignments: List<ReceiptColumnAlign>? = null
        val children = mutableListOf<RctNode>()

        while (true) {
            val peek = state.peekMeaningfulLine() ?: break
            val indent = peek.indentLevel()
            if (indent < tableIndent) break
            if (indent > tableIndent) {
                throw RctParseException("Unexpected indentation at line ${peek.number}")
            }

            val current = state.consumeMeaningfulLine()
                ?: throw RctParseException("Unexpected end of table block.")
            val trimmed = current.content.trim()

            when {
                trimmed.startsWith("headers:") -> {
                    val values = extractQuotedArguments(trimmed.removePrefix("headers:").trim(), "headers", current.number)
                    headers.clear()
                    headers += values.map(::parseTemplateString)
                }

                trimmed.startsWith("weights:") -> {
                    val values = trimmed.removePrefix("weights:").trim()
                        .split(Regex("""\s+"""))
                        .filter { it.isNotBlank() }
                    if (values.isEmpty()) {
                        throw RctParseException("Invalid weights statement at line ${current.number}")
                    }
                    weights = values.map {
                        it.toFloatOrNull()
                            ?: throw RctParseException("Invalid weight '$it' at line ${current.number}")
                    }
                }

                trimmed.startsWith("alignments:") -> {
                    val values = trimmed.removePrefix("alignments:").trim()
                        .split(Regex("""\s+"""))
                        .filter { it.isNotBlank() }
                    if (values.isEmpty()) {
                        throw RctParseException("Invalid alignments statement at line ${current.number}")
                    }
                    alignments = values.map {
                        when (it.lowercase()) {
                            "start", "left" -> ReceiptColumnAlign.START
                            "center", "middle" -> ReceiptColumnAlign.CENTER
                            "end", "right" -> ReceiptColumnAlign.END
                            else -> throw RctParseException("Invalid alignment '$it' at line ${current.number}")
                        }
                    }
                }

                else -> children += parseNode(current, state, tableIndent)
            }
        }

        return RctTableNode(
            headers = headers,
            columnWeights = weights,
            columnAlignments = alignments,
            children = children
        )
    }

    private fun parseTemplateString(raw: String): RctTemplateString {
        val parts = mutableListOf<RctTemplatePart>()
        var cursor = 0

        while (cursor < raw.length) {
            val start = raw.indexOf("{{", cursor)
            if (start == -1) {
                val literal = raw.substring(cursor)
                if (literal.isNotEmpty()) {
                    parts += RctLiteralPart(literal)
                }
                break
            }

            if (start > cursor) {
                parts += RctLiteralPart(raw.substring(cursor, start))
            }

            val end = raw.indexOf("}}", start + 2)
            if (end == -1) {
                throw RctParseException("Unmatched template binding in string: $raw")
            }

            val binding = raw.substring(start + 2, end).trim()
            if (binding.isEmpty()) {
                throw RctParseException("Empty template binding in string: $raw")
            }
            parts += parseBindingPart(binding)
            cursor = end + 2
        }

        if (parts.isEmpty()) {
            parts += RctLiteralPart("")
        }

        return RctTemplateString(parts)
    }

    private fun parseBindingPart(binding: String): RctBindingPart {
        val segments = binding.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            throw RctParseException("Invalid binding: $binding")
        }

        val path = segments.first()
        val filters = segments.drop(1).map { filterSegment ->
            val filterParts = filterSegment.split(":", limit = 2)
            val name = filterParts[0].trim()
            if (name.isEmpty()) {
                throw RctParseException("Invalid filter in binding: $binding")
            }
            val args = if (filterParts.size == 2) {
                filterParts[1]
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
            RctFilterCall(name = name, args = args)
        }

        return RctBindingPart(path = path, filters = filters)
    }

    private fun unquote(value: String): String {
        return if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            value.substring(1, value.length - 1)
        } else {
            value
        }
    }

    private fun extractQuotedArguments(raw: String, statement: String, lineNumber: Int): List<String> {
        val matches = QUOTED_ARGUMENT.findAll(raw).toList()
        if (matches.isEmpty()) {
            throw RctParseException("Invalid $statement statement at line $lineNumber")
        }
        val remainder = QUOTED_ARGUMENT.replace(raw, "").trim()
        if (remainder.isNotEmpty()) {
            throw RctParseException("Invalid $statement statement at line $lineNumber")
        }
        return matches.map { it.groupValues[1] }
    }

    private data class SourceLine(
        val number: Int,
        val content: String
    ) {
        fun isMeaningful(): Boolean {
            val trimmed = content.trim()
            return trimmed.isNotEmpty() && !trimmed.startsWith("#")
        }

        fun indentLevel(): Int {
            val spaces = content.takeWhile { it == ' ' }.length
            if (spaces % INDENT_SIZE != 0) {
                throw RctParseException("Indentation must use multiples of 2 spaces. Line $number")
            }
            return spaces / INDENT_SIZE
        }
    }

    private class ParserState(
        private val lines: List<SourceLine>
    ) {
        private var index: Int = 0

        fun peekMeaningfulLine(): SourceLine? {
            var cursor = index
            while (cursor < lines.size) {
                val line = lines[cursor]
                if (line.isMeaningful()) return line
                cursor++
            }
            return null
        }

        fun nextMeaningfulLine(): SourceLine? {
            while (index < lines.size) {
                val line = lines[index++]
                if (line.isMeaningful()) return line
            }
            return null
        }

        fun consumeMeaningfulLine(): SourceLine? = nextMeaningfulLine()
    }

    private fun parseFontScaleMode(raw: String): ReceiptFontScaleMode {
        return when (raw.lowercase()) {
            "relative" -> ReceiptFontScaleMode.RELATIVE
            "fixed" -> ReceiptFontScaleMode.FIXED
            else -> throw RctParseException("Invalid fontScaleMode '$raw'. Expected relative or fixed.")
        }
    }

    private fun parseFont(raw: String): Float {
        val parsed = raw.toFloatOrNull()
            ?: throw RctParseException("Invalid font '$raw'. Expected a positive number.")
        if (parsed <= 0f) {
            throw RctParseException("Invalid font '$raw'. Expected a positive number.")
        }
        return parsed
    }

    private fun parseModifiers(
        raw: String,
        trailingFontSize: Float?,
        lineNumber: Int
    ): ParsedModifiers {
        val tokens = raw.trim()
            .split(Regex("""\s+"""))
            .filter { it.isNotBlank() }
        var index = 0
        var bold = false
        var underline = false
        var fontSize = ReceiptFontSize.MIDDLE

        while (index < tokens.size) {
            when (val token = tokens[index]) {
                "bold" -> bold = true
                "underline" -> underline = true
                "small" -> fontSize = ReceiptFontSize.SMALL
                "big" -> fontSize = ReceiptFontSize.BIG
                else -> throw RctParseException("Unsupported modifier '$token' at line $lineNumber")
            }
            index++
        }

        return ParsedModifiers(
            bold = bold,
            underline = underline,
            fontSize = fontSize,
            customFontSize = trailingFontSize
        )
    }

    private data class DocumentContent(
        val header: List<RctNode>,
        val body: List<RctNode>,
        val footer: List<RctNode>,
        val headerReferences: List<RctFragmentReference>,
        val footerReferences: List<RctFragmentReference>
    )

    private data class ParsedModifiers(
        val bold: Boolean,
        val underline: Boolean,
        val fontSize: ReceiptFontSize,
        val customFontSize: Float?
    )

    private companion object {
        private const val INDENT_SIZE = 2

        private val RECEIPT_DECLARATION = Regex("""receipt\s+"([^"]+)"""")
        private val USE_FRAGMENT_STATEMENT = Regex("""use\s+(header|footer)\s+"([^"]+)"""")
        private val META_ENTRY = Regex("""([A-Za-z][A-Za-z0-9_]*)\s*:\s*(.+)""")
        private val DIVIDER_STATEMENT = Regex("""divider(?:\s+"((?:\\.|[^"])*)")?(?:\s+(\d+))?""")
        private val SPACER_STATEMENT = Regex("""spacer\s+(\d+)""")
        private val IF_STATEMENT = Regex("""if\s+([A-Za-z0-9_.]+)\s*:""")
        private val FOR_STATEMENT =
            Regex("""for\s+([A-Za-z_][A-Za-z0-9_]*)\s+in\s+([A-Za-z0-9_.]+)\s*:""")
        private val KEY_VALUE_STATEMENT =
            Regex("""kv\s+((?:(?:bold|small|big|underline)\s+)*)"((?:\\.|[^"])*)"\s+"((?:\\.|[^"])*)"(?:\s+(\d+(?:\.\d+)?))?""")
        private val QR_STATEMENT =
            Regex("""qr\s+"((?:\\.|[^"])*)"(?:\s+(\d+))?""")
        private val BARCODE_STATEMENT =
            Regex("""barcode\s+"((?:\\.|[^"])*)"(?:\s+(\d+))?(?:\s+(\d+))?(?:\s+"([a-z0-9]+)")?""")
        private val IMAGE_STATEMENT =
            Regex("""image\s+"((?:\\.|[^"])*)"(?:\s+(\d+))?(?:\s+(\d+))?""")
        private val TEXT_STATEMENT =
            Regex("""(text|left|center|right)\s+((?:(?:bold|small|big|underline)\s+)*)"((?:\\.|[^"])*)"(?:\s+(\d+(?:\.\d+)?))?""")
        private val QUOTED_ARGUMENT =
            Regex(""""((?:\\.|[^"])*)"""")
    }
}
