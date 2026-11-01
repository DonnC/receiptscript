package zw.co.donnclab.rct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRctParserTest {

    private val parser = DefaultRctParser()

    @Test
    fun `parser should parse receipt declaration meta and body`() {
        val document = parser.parse(
            """
            receipt "sale_default"

            meta:
              width: 42
              paper: "58mm"
              templateVersion: 1

            center bold underline "{{merchant.name}}"
            kv underline "INVOICE" "{{txn.invoiceId}}"
            divider
            spacer 1
            """.trimIndent()
        )

        assertEquals("sale_default", document.name)
        assertEquals(42, document.meta?.width)
        assertEquals("58mm", document.meta?.paper)
        assertEquals(1, document.meta?.templateVersion)
        assertEquals(4, document.body.size)

        val header = document.body[0] as RctTextNode
        assertEquals(RctAlign.CENTER, header.align)
        assertTrue(header.bold)
        assertTrue(header.underline)

        val invoice = document.body[1] as RctKeyValueNode
        assertEquals("INVOICE", (invoice.key.parts.single() as RctLiteralPart).text)
        assertTrue(invoice.underline)

        assertTrue(document.body[2] is RctDividerNode)
        assertEquals(1, (document.body[3] as RctSpacerNode).lines)
    }

    @Test
    fun `parser should parse if and for blocks`() {
        val document = parser.parse(
            """
            receipt "wallet"

            if txn.wallet:
              kv "WALLET" "{{txn.wallet}}"

            for item in items:
              text "{{item.label}}"
            """.trimIndent()
        )

        val ifNode = document.body[0] as RctIfNode
        assertEquals("txn.wallet", ifNode.path)
        assertEquals(1, ifNode.children.size)

        val forNode = document.body[1] as RctForEachNode
        assertEquals("item", forNode.itemName)
        assertEquals("items", forNode.collectionPath)
        assertEquals(1, forNode.children.size)
    }

    @Test
    fun `parser should parse binding filters`() {
        val document = parser.parse(
            """
            receipt "filters"

            kv "AMOUNT" "{{txn.amount | money:txn.currency | upper}}"
            """.trimIndent()
        )

        val node = document.body.single() as RctKeyValueNode
        val binding = node.value.parts.single() as RctBindingPart

        assertEquals("txn.amount", binding.path)
        assertEquals(2, binding.filters.size)
        assertEquals("money", binding.filters[0].name)
        assertEquals(listOf("txn.currency"), binding.filters[0].args)
        assertEquals("upper", binding.filters[1].name)
    }

    @Test(expected = RctParseException::class)
    fun `parser should reject tabs`() {
        parser.parse("receipt \"bad\"\n\ttext \"hello\"")
    }

    @Test
    fun `parser should preserve mixed literal and bindings`() {
        val document = parser.parse(
            """
            receipt "mixed"

            text "Hello {{merchant.name}}!"
            """.trimIndent()
        )

        val node = document.body.single() as RctTextNode
        assertEquals(3, node.text.parts.size)
        assertTrue(node.text.parts[0] is RctLiteralPart)
        assertTrue(node.text.parts[1] is RctBindingPart)
        assertTrue(node.text.parts[2] is RctLiteralPart)
        assertFalse(node.bold)
    }

    @Test
    fun `parser should ignore comment lines`() {
        val document = parser.parse(
            """
            receipt "commented"

            # header note
            center "Hello"
            # batch note
            kv "TOTAL" "USD 10.00"
            """.trimIndent()
        )

        assertEquals(2, document.body.size)
        assertTrue(document.body[0] is RctTextNode)
        assertTrue(document.body[1] is RctKeyValueNode)
    }

    @Test
    fun `parser should parse custom divider and rich receipt blocks`() {
        val document = parser.parse(
            """
            receipt "showcase"

            center small "TITLE" 12.5
            kv "AMOUNT" "{{txn.amount}}" 11.0
            divider "=" 10
            table:
              headers: "TYPE" "COUNT" "TOTAL"
              weights: 0.4 0.2 0.4
              alignments: start center end
              row "ITEM" "QTY" "TOTAL"
              for item in items:
                row "{{item.name}}" "{{item.qty}}" "{{item.total}}"
            qr "{{txn.qr}}" 240
            barcode "{{txn.reference}}" 260 72
            image "assets/logo.png" 88 44
            """.trimIndent()
        )

        val title = document.body[0] as RctTextNode
        assertEquals(RctFontSize.SMALL, title.fontSize)
        assertEquals(12.5f, title.customFontSize)

        val amount = document.body[1] as RctKeyValueNode
        assertEquals(11.0f, amount.customFontSize)

        val divider = document.body[2] as RctDividerNode
        assertEquals("=", divider.pattern)
        assertEquals(10, divider.length)

        val table = document.body[3] as RctTableNode
        assertEquals(3, table.headers.size)
        assertEquals(listOf(0.4f, 0.2f, 0.4f), table.columnWeights)
        assertEquals(listOf(RctColumnAlignment.START, RctColumnAlignment.CENTER, RctColumnAlignment.END), table.columnAlignments)
        assertEquals(2, table.children.size)
        val headerRow = table.children[0] as RctRowNode
        assertEquals(3, headerRow.columns.size)

        val qr = document.body[4] as RctQrNode
        assertEquals(240, qr.size)

        val barcode = document.body[5] as RctBarcodeNode
        assertEquals(260, barcode.width)
        assertEquals(72, barcode.height)

        val image = document.body[6] as RctImageNode
        assertEquals(88, image.width)
        assertEquals(44, image.height)
    }

    @Test
    fun `parser should parse base font metadata and trailing inline font overrides`() {
        val document = parser.parse(
            """
            receipt "font_control"

            meta:
              font: 21
              fontScaleMode: "fixed"

            center bold "SALE" 24
            kv small "TOTAL" "{{txn.total}}" 19
            right big "APPROVED" 26
            """.trimIndent()
        )

        assertEquals(21f, document.meta?.font)
        assertEquals(RctFontScaleMode.FIXED, document.meta?.fontScaleMode)

        val title = document.body[0] as RctTextNode
        assertEquals(RctFontSize.MIDDLE, title.fontSize)
        assertEquals(24f, title.customFontSize)
        assertTrue(title.bold)

        val total = document.body[1] as RctKeyValueNode
        assertEquals(RctFontSize.SMALL, total.fontSize)
        assertEquals(19f, total.customFontSize)

        val approved = document.body[2] as RctTextNode
        assertEquals(RctFontSize.BIG, approved.fontSize)
        assertEquals(26f, approved.customFontSize)
    }

    @Test(expected = RctParseException::class)
    fun `parser should reject invalid base font metadata`() {
        parser.parse(
            """
            receipt "font_control"

            meta:
              font: giant

            center "SALE"
            """.trimIndent()
        )
    }

    @Test
    fun `parser should parse first class header body and footer sections`() {
        val document = parser.parse(
            """
            receipt "sale_default"

            header:
              center bold "{{merchant.name}}"

            body:
              kv "INVOICE" "{{txn.invoiceId}}"

            footer:
              center "Thank you!"
            """.trimIndent()
        )

        assertEquals("sale_default", document.name)
        assertEquals(1, document.header.size)
        assertEquals(1, document.body.size)
        assertEquals(1, document.footer.size)
        assertEquals(RctAlign.CENTER, (document.header.single() as RctTextNode).align)
        assertTrue((document.header.single() as RctTextNode).bold)
        assertTrue(document.body.single() is RctKeyValueNode)
        assertEquals("Thank you!", ((document.footer.single() as RctTextNode).text.parts.single() as RctLiteralPart).text)
    }

    @Test
    fun `parser should keep old templates as body only templates`() {
        val document = parser.parse(
            """
            receipt "legacy"

            center "{{merchant.name}}"
            kv "INVOICE" "{{txn.invoiceId}}"
            """.trimIndent()
        )

        assertTrue(document.header.isEmpty())
        assertTrue(document.footer.isEmpty())
        assertEquals(2, document.body.size)
    }

    @Test
    fun `parser should parse reusable header and footer references`() {
        val document = parser.parse(
            """
            receipt "sale_default"

            use header "merchant-default"
            use footer "customer-default"

            body:
              kv "INVOICE" "{{txn.invoiceId}}"
            """.trimIndent()
        )

        assertEquals(listOf(RctFragmentReference("merchant-default")), document.headerReferences)
        assertEquals(listOf(RctFragmentReference("customer-default")), document.footerReferences)
        assertEquals(1, document.body.size)
    }

    @Test
    fun `parser should parse standalone reusable fragments`() {
        val nodes = parser.parseFragment(
            """
            center bold "{{merchant.name}}"
            divider "_"
            """.trimIndent()
        )

        assertEquals(2, nodes.size)
        assertTrue(nodes[0] is RctTextNode)
        assertTrue(nodes[1] is RctDividerNode)
    }
}
