package zw.co.donnclab.rct

interface RctTemplateEngine {
    fun render(template: RctTemplateString, context: RctRenderContext): String
    fun resolveValue(path: String, context: RctRenderContext): Any?
    fun isTruthy(path: String, context: RctRenderContext): Boolean
}

data class RctRenderContext(
    val root: Any?,
    val locals: Map<String, Any?> = emptyMap()
)
