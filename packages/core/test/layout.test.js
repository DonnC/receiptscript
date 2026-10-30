// The neutral model rct-core's RctReceiptRenderer produces, checked block by block.
import { test } from "node:test";
import assert from "node:assert/strict";

import { composeFragments, createContext, geometry, layout, lint, parse, rowText } from "../src/index.js";

const strip = (blocks) => blocks.map(({ origin, ...block }) => block);

test("orders shared header, inline header, body, a two-line gap, inline footer, shared footer", () => {
  const document = parse([
    'receipt "sale"',
    'use header "header"',
    'use footer "footer"',
    "header:",
    '  center "INLINE HEADER"',
    "body:",
    '  left "BODY"',
    "footer:",
    '  center "INLINE FOOTER"',
  ].join("\n"));
  const fragments = { header: 'center "SHARED HEADER"', footer: 'center "SHARED FOOTER"' };

  const { document: composed } = composeFragments(document, (id) => fragments[id]);
  const texts = layout(composed, createContext({})).map((block) => block.text ?? block.type);

  assert.deepEqual(texts, ["SHARED HEADER", "INLINE HEADER", "BODY", "spacer", "spacer", "INLINE FOOTER", "SHARED FOOTER"]);
});

test("reports a shared fragment that doesn't exist instead of printing without it", () => {
  const { missing } = composeFragments(parse('receipt "sale"\nuse header "header"\nleft "x"'), () => null);

  assert.deepEqual(missing, [{ section: "header", id: "header" }]);
});

test("resolves font sizes through the base font policy", () => {
  const relative = parse('receipt "f"\nmeta:\n  font: 20\nleft small "s"\nleft "m"\nleft big "b"\nleft "custom" 30');
  const fixed = parse('receipt "f"\nmeta:\n  font: 20\n  fontScaleMode: "fixed"\nleft small "s"\nleft big "b"');

  assert.deepEqual(layout(relative, createContext({})).map((block) => block.fontSize), [17, 20, 23, 30]);
  assert.deepEqual(layout(fixed, createContext({})).map((block) => block.fontSize), [20, 20]);
  assert.deepEqual(layout(parse('receipt "f"\nleft big "b"'), createContext({})).map((block) => block.fontSize), [null]);
});

test("draws dividers and rows in characters of the receipt width", () => {
  const document = parse('receipt "w"\nmeta:\n  width: 10\ndivider "=-"\ndivider "*" 4\nrow "ab" "cdefgh" "i"');

  assert.deepEqual(layout(document, createContext({})).map((block) => block.text), ["=-=-=-=-=-", "****", "ab  cdei  "]);
  assert.equal(rowText(["x", "y"], 5), "x  y ");
});

test("builds table rows through if and for, and drops anything else", () => {
  const document = parse([
    'receipt "t"',
    "table:",
    '  headers: "ITEM" "QTY"',
    "  for item in items:",
    '    row "{{item.name}}" "{{item.qty}}"',
    "  if extra:",
    '    row "EXTRA" "1"',
    '  left "ignored"',
  ].join("\n"));

  const [table] = strip(layout(document, createContext({ items: [{ name: "Tea", qty: 2 }], extra: true })));

  assert.deepEqual(table.rows, [["Tea", "2"], ["EXTRA", "1"]]);
  assert.deepEqual([table.headerSize, table.rowSize, table.headerBold, table.lineWidth], ["small", "small", true, 34]);
  assert.equal(lint('receipt "t"\ntable:\n  row "a" "b"\n  left "x"').find((issue) => issue.code === "ignored-in-table").severity, "warning");
});

test("sets loop variables like rct-core", () => {
  const document = parse('receipt "l"\nfor n in list:\n  left "{{@index}}/{{@count}} {{n}} {{@first}} {{@last}}"');

  assert.deepEqual(layout(document, createContext({ list: ["a", "b"] })).map((block) => block.text), ["0/2 a true false", "1/2 b false true"]);
});

test("records which template node each block came from", () => {
  const document = parse('receipt "o"\nif show:\n  left "x"\nfor n in list:\n  left "{{n}}"');

  const origins = layout(document, createContext({ show: true, list: [1, 2] })).map((block) => block.origin.path);

  assert.deepEqual(origins, [["body", 0, 0], ["body", 1, 0], ["body", 1, 0]]);
});

test("fits the default width to the line at the middle font", () => {
  const info = geometry(null);

  assert.equal(info.width, 34);
  assert.equal(info.lineWidthPx, 34 * 0.6 * 13);
  assert.equal(Math.round(info.dotScale * 384), Math.round(info.lineWidthPx));
});
