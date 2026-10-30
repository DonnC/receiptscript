// Ports of rct-core's DefaultRctParserTest, so both engines answer the same cases the same way.
import { test } from "node:test";
import assert from "node:assert/strict";

import { parse, parseFragment, RctParseError, parseTemplateString } from "../src/index.js";

const source = (...lines) => lines.join("\n");

test("parses the declaration, meta and body", () => {
  const document = parse(source(
    'receipt "sale_default"',
    "meta:",
    "  width: 42",
    '  paper: "58mm"',
    "  templateVersion: 1",
    'center bold underline "{{merchant.name}}"',
    'kv underline "INVOICE" "{{txn.invoiceId}}"',
    "divider",
    "spacer 1",
  ));

  assert.equal(document.name, "sale_default");
  assert.equal(document.meta.width, 42);
  assert.equal(document.meta.paper, "58mm");
  assert.equal(document.meta.templateVersion, 1);
  assert.equal(document.body.length, 4);
  assert.deepEqual(
    [document.body[0].align, document.body[0].bold, document.body[0].underline],
    ["center", true, true],
  );
  assert.equal(document.body[1].key, "INVOICE");
  assert.equal(document.body[1].underline, true);
  assert.equal(document.body[2].type, "divider");
  assert.equal(document.body[3].lines, 1);
});

test("parses if and for blocks", () => {
  const document = parse(source(
    'receipt "wallet"',
    "if txn.wallet:",
    '  kv "WALLET" "{{txn.wallet}}"',
    "for item in items:",
    '  text "{{item.label}}"',
  ));

  assert.deepEqual([document.body[0].path, document.body[0].children.length], ["txn.wallet", 1]);
  assert.deepEqual([document.body[1].item, document.body[1].collection, document.body[1].children.length], ["item", "items", 1]);
});

test("parses binding filters", () => {
  const document = parse(source('receipt "filters"', 'kv "AMOUNT" "{{txn.amount | money:txn.currency | upper}}"'));
  const [binding] = parseTemplateString(document.body[0].value);

  assert.equal(binding.path, "txn.amount");
  assert.deepEqual(binding.filters, [{ name: "money", args: ["txn.currency"] }, { name: "upper", args: [] }]);
});

test("rejects tabs", () => {
  assert.throws(() => parse('receipt "bad"\n\ttext "hello"'), RctParseError);
});

test("keeps mixed literals and bindings", () => {
  const document = parse(source('receipt "mixed"', 'text "Hello {{merchant.name}}!"'));
  const parts = parseTemplateString(document.body[0].text);

  assert.deepEqual(parts.map((part) => part.kind), ["literal", "binding", "literal"]);
  assert.equal(document.body[0].bold, false);
});

test("ignores comment lines", () => {
  const document = parse(source('receipt "commented"', "# header note", 'center "Hello"', "# batch note", 'kv "TOTAL" "USD 10.00"'));

  assert.deepEqual(document.body.map((node) => node.type), ["text", "kv"]);
});

test("parses custom dividers and rich blocks", () => {
  const document = parse(source(
    'receipt "showcase"',
    'center small "TITLE" 12.5',
    'kv "AMOUNT" "{{txn.amount}}" 11.0',
    'divider "=" 10',
    "table:",
    '  headers: "TYPE" "COUNT" "TOTAL"',
    "  weights: 0.4 0.2 0.4",
    "  alignments: start center end",
    '  row "ITEM" "QTY" "TOTAL"',
    "  for item in items:",
    '    row "{{item.name}}" "{{item.qty}}" "{{item.total}}"',
    'qr "{{txn.qr}}" 240',
    'barcode "{{txn.reference}}" 260 72',
    'image "assets/logo.png" 88 44',
  ));
  const [title, amount, divider, table, qr, barcode, image] = document.body;

  assert.deepEqual([title.size, title.fontSize], ["small", 12.5]);
  assert.equal(amount.fontSize, 11);
  assert.deepEqual([divider.pattern, divider.length], ["=", 10]);
  assert.equal(table.headers.length, 3);
  assert.deepEqual(table.weights, [0.4, 0.2, 0.4]);
  assert.deepEqual(table.alignments, ["start", "center", "end"]);
  assert.equal(table.children.length, 2);
  assert.equal(table.children[0].columns.length, 3);
  assert.equal(qr.size, 240);
  assert.deepEqual([barcode.width, barcode.height], [260, 72]);
  assert.deepEqual([image.width, image.height], [88, 44]);
});

test("parses the base font and trailing inline sizes", () => {
  const document = parse(source(
    'receipt "font_control"',
    "meta:",
    "  font: 21",
    '  fontScaleMode: "fixed"',
    'center bold "SALE" 24',
    'kv small "TOTAL" "{{txn.total}}" 19',
    'right big "APPROVED" 26',
  ));

  assert.deepEqual([document.meta.font, document.meta.fontScaleMode], [21, "fixed"]);
  assert.deepEqual([document.body[0].size, document.body[0].fontSize, document.body[0].bold], ["middle", 24, true]);
  assert.deepEqual([document.body[1].size, document.body[1].fontSize], ["small", 19]);
  assert.deepEqual([document.body[2].size, document.body[2].fontSize], ["big", 26]);
});

test("rejects an invalid base font", () => {
  assert.throws(() => parse(source('receipt "font_control"', "meta:", "  font: giant", 'center "SALE"')), RctParseError);
});

test("parses header, body and footer sections", () => {
  const document = parse(source(
    'receipt "sale_default"',
    "header:",
    '  center bold "{{merchant.name}}"',
    "body:",
    '  kv "INVOICE" "{{txn.invoiceId}}"',
    "footer:",
    '  center "Thank you!"',
  ));

  assert.deepEqual([document.header.length, document.body.length, document.footer.length], [1, 1, 1]);
  assert.equal(document.footer[0].text, "Thank you!");
});

test("treats a template without sections as body only", () => {
  const document = parse(source('receipt "legacy"', 'center "{{merchant.name}}"', 'kv "INVOICE" "{{txn.invoiceId}}"'));

  assert.deepEqual([document.header.length, document.footer.length, document.body.length], [0, 0, 2]);
});

test("parses shared header and footer references", () => {
  const document = parse(source(
    'receipt "sale_default"',
    'use header "merchant-default"',
    'use footer "customer-default"',
    "body:",
    '  kv "INVOICE" "{{txn.invoiceId}}"',
  ));

  assert.deepEqual(document.headerRefs, ["merchant-default"]);
  assert.deepEqual(document.footerRefs, ["customer-default"]);
});

test("parses a standalone fragment", () => {
  const nodes = parseFragment(source('center bold "{{merchant.name}}"', 'divider "_"'));

  assert.deepEqual(nodes.map((node) => node.type), ["text", "divider"]);
});

test("reports the line of an unsupported statement", () => {
  assert.throws(() => parse(source('receipt "x"', "", 'shout "hi"')), (error) => error.line === 3);
});

test("rejects a fragment that is indented like the old demo header", () => {
  assert.throws(() => parseFragment('center bold "{{merchant.merchantName}}"\n  divider "_"'), /Unexpected indentation at line 2/);
});
