package zw.co.donnclab.rct

/**
 * Supplies UTF-8 template source by logical ID, including reusable header/footer fragments.
 * [args] carries host-specific lookup options (for example a merchant or locale), not receipt data.
 * Implementations own I/O dispatch, authentication and storage caching. Missing templates should
 * throw; failures and coroutine cancellation propagate to the caller unchanged.
 */
interface ReceiptTemplateRepository {
    suspend fun getTemplate(templateId: String, args: Map<String, Any?> = emptyMap()): String
}
