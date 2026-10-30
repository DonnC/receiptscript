package zw.co.donnclab.rct

interface RctParser {
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

class RctParseException(message: String) : IllegalArgumentException(message)
