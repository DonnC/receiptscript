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

test("README includes a complete Android entry point and each printer path", () => {
  const kotlin = fences.filter(match => match[1] === "kotlin").map(match => match[2]).join("\n");
  for (const name of ["loadSale", "SaleReceiptScreen", "printOnTerminal", "printToBluetooth", "printToUsbSerial"]) {
    assert.match(kotlin, new RegExp(`fun ${name}\\(`));
  }
  assert.match(readme, /RCT: Open Preview/);
  assert.match(readme, /sale\.preview\.json/);
});
