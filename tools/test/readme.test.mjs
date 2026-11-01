import test from "node:test";
import assert from "node:assert/strict";
import {existsSync, readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {ReceiptScript} from "../web/src/index.js";

const root = fileURLToPath(new URL("../../", import.meta.url));
const readme = readFileSync(root + "README.md", "utf8");
const fences = [...readme.matchAll(/```(\w+)\r?\n([\s\S]*?)```/g)];

test("README receipt and preview data match the illustrated example", () => {
  const template = fences.find(match => match[1] === "rct")[2].trim();
  const data = JSON.parse(fences.find(match => match[1] === "json")[2]);
  const examples = root + "example/src/main/assets/templates/readme/";
  const statements = source => source.split(/\r?\n/).filter(line => line.trim()).join("\n");
  assert.equal(statements(template), statements(readFileSync(examples + "sale.rct", "utf8")));
  assert.deepEqual(data, JSON.parse(readFileSync(examples + "sale.preview.json", "utf8")));
  const receipt = new ReceiptScript().compile(template).render(data);
  assert.ok(receipt.blocks.some(block => block.type === "table"));
});

test("README repository-relative links resolve", () => {
  for (const match of readme.matchAll(/\]\((?!https?:|#)([^)]+)\)/g)) {
    assert.ok(existsSync(root + match[1]), match[1]);
  }
});

test("README shows the direct API and each printer path without application wrappers", () => {
  const kotlin = fences.filter(match => match[1] === "kotlin").map(match => match[2]).join("\n");
  assert.match(kotlin, /val receipts = ReceiptScript\(/);
  assert.match(kotlin, /receipts\.render\("sale", data\)/);
  assert.match(kotlin, /receipts\.print\(receipt\)/);
  assert.match(kotlin, /class TerminalDriver/);
  assert.match(kotlin, /val bluetoothDriver = EscPosReceiptPrinter/);
  assert.match(kotlin, /val usbDriver = EscPosReceiptPrinter/);
  assert.doesNotMatch(kotlin, /fun (createReceipts|loadSale|SaleReceiptScreen|printSale)\(/);
  assert.match(readme, /RCT: Open Preview/);
  assert.match(readme, /sale\.preview\.json/);
  assert.match(readme, /ReceiptUiPreview\(receipt/);
  assert.doesNotMatch(readme, /RasterReceipt/);
});
