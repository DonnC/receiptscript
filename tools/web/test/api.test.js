import { test } from "node:test";
import assert from "node:assert/strict";
import { ReceiptScript } from "../src/index.js";

test("compiles once, binds fresh data on each render, and resolves shared sections", () => {
  const engine = new ReceiptScript({ templates: { sale: 'receipt "sale"\nuse footer "thanks"\nleft "{{name}}"', thanks: 'center "Thanks"' } });
  const template = engine.template("sale");
  assert.equal(template.render({name: "First"}).blocks[0].text, "First");
  assert.equal(template.render({name: "Second"}).blocks[0].text, "Second");
  assert.deepEqual(template.render({name: "A"}).blocks.map(b => b.text ?? b.type), ["A", "spacer", "spacer", "Thanks"]);
});

test("missing templates and fragments fail before an adapter is invoked", () => {
  const engine = new ReceiptScript();
  assert.throws(() => engine.template("missing"), /Unknown template/);
  assert.throws(() => engine.compile('receipt "s"\nuse header "absent"').render({}), /Missing header/);
});

test("strict bindings reject missing data but optional if and default remain usable", () => {
  const engine = new ReceiptScript();
  assert.throws(() => engine.compile('receipt "s"\nleft "{{missing}}"').render({}), /Missing binding: missing/);
  assert.equal(engine.compile('receipt "s"\nif optional:\n  left "{{optional}}"\nleft "{{missing | default:Unknown}}"').render({}).blocks[0].text, "Unknown");
  assert.equal(new ReceiptScript({missingBindings: "empty"}).compile('receipt "s"\nleft "{{missing}}"').render({}).blocks[0].text, "");
});

test("adapter sees the same resolved receipt used for preview, including barcode type", async () => {
  const receipt = new ReceiptScript().compile('receipt "s"\nbarcode "{{ref}}" 300 60 "code128"').render({ref: "INV-123"});
  assert.equal(receipt.blocks[0].format, "code128");
  const result = await receipt.render({render: async r => {assert.equal(r, receipt); return new Uint8Array([1, 2]);}});
  assert.deepEqual(result, new Uint8Array([1, 2]));
});

test("compile rejects invalid filters and table shapes before output", () => {
  assert.throws(() => new ReceiptScript().compile('receipt "s"\nleft "{{x | unknown}}"'), /Unknown filter/);
  assert.throws(() => new ReceiptScript().compile('receipt "s"\ntable:\n  headers: "A" "B"\n  row "1" "2" "3"').render({}), /columns/);
});

test("fragments are validated and invalid collection indexes are missing", () => {
  const engine = new ReceiptScript({templates: {bad: 'left "{{name | unknown}}"'}});
  assert.throws(() => engine.compile('receipt "s"\nuse header "bad"').render({name: "A"}), /Unknown filter/);
  assert.throws(() => engine.compile('receipt "s"\nleft "{{items[9]}}"').render({items: [1]}), /Missing binding/);
});
