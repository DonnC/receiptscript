import { test } from "node:test";
import assert from "node:assert/strict";
import { ReceiptScript, parse, write } from "../src/index.js";
import { HtmlRenderer, symbolSvg } from "../src/html.js";
import { TextRenderer } from "../src/text.js";

test("barcode format is backward compatible and survives canonical writing", () => {
  const legacy = parse('receipt "s"\nbarcode "ABC" 300 60');
  assert.equal(legacy.body[0].format, "code128");
  const typed = parse('receipt "s"\nbarcode "5901234123457" 300 60 "ean13"');
  assert.deepEqual(parse(write(typed)), typed);
  assert.throws(() => parse('receipt "s"\nbarcode "X" 300 60 "fictional"'), /barcode format/);
});

test("barcode and QR SVG output is offline, data-dependent and validates payloads", () => {
  assert.notEqual(symbolSvg({type: "barcode", content: "INV-1", width: 300, height: 60, format: "code128"}), symbolSvg({type: "barcode", content: "INV-2", width: 300, height: 60, format: "code128"}));
  assert.match(symbolSvg({type: "qr", content: "test", size: 150}), /<svg/);
  assert.throws(() => symbolSvg({type: "barcode", content: "invalid", width: 300, height: 60, format: "ean13"}), /barcode/i);
});

test("HTML escapes bindings, renders real codes, and resolves images only via the host", () => {
  const receipt = new ReceiptScript().compile('receipt "s"\nleft "{{name}}"\nimage "logo" 40 40\nqr "local" 120\nbarcode "ABC" 280 60').render({name: '<script>alert("x")</script>'});
  const html = new HtmlRenderer({resolveImage: () => "data:image/png;base64,AAAA"}).render(receipt);
  assert.match(html, /&lt;script&gt;/);
  assert.equal((html.match(/<svg/g) ?? []).length, 2);
  assert.doesNotMatch(html, /api\.qrserver|<script|repeating-linear-gradient/);
  assert.throws(() => new HtmlRenderer({resolveImage: () => 'javascript:alert(1)'}).render(receipt), /image source/);
});

test("text tables wrap long cells, honor weights and stay within adapter columns", () => {
  const receipt = new ReceiptScript().compile('receipt "s"\ntable:\n  headers: "ITEM" "QTY" "TOTAL"\n  weights: 3 1 2\n  alignments: start center end\n  row "Very long item description" "12" "USD 123.45"').render({});
  const output = new TextRenderer({columns: 32}).render(receipt);
  assert.ok(output.split("\n").every(line => Array.from(line).length <= 32));
  assert.match(output, /description/);
  assert.match(output, /USD 123\.45/);
});

test("text output fails explicitly for media unless an adapter handles it", () => {
  const receipt = new ReceiptScript().compile('receipt "s"\nqr "ABC"').render({});
  assert.throws(() => new TextRenderer().render(receipt), /Unsupported block: qr/);
  assert.match(new TextRenderer({media: b => `[${b.type}]`}).render(receipt), /\[qr\]/);
});
