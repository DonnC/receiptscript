package io.github.donnclab.rct.tooling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RctLinterTest {

    @Test
    fun `linter should report unsupported statement with line number`() {
        val issues = RctLinter.lint(
            """
                receipt "bad"
                wobble "oops"
            """.trimIndent()
        )

        assertTrue(issues.any { it.line == 2 && it.message.contains("Unsupported statement") })
    }

    @Test
    fun `linter should report malformed interpolation`() {
        val issues = RctLinter.lint(
            """
                receipt "bad"
                center "Amount {{txn.amount"
            """.trimIndent()
        )

        assertTrue(issues.any { it.message.contains("Unterminated string or malformed interpolation") })
    }
}
