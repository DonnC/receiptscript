import test from "node:test";
import assert from "node:assert/strict";
import {existsSync, readFileSync, readdirSync} from "node:fs";
import {dirname, resolve as resolvePath} from "node:path";
import {fileURLToPath} from "node:url";
import {ReceiptScript} from "../web/src/index.js";

const root = fileURLToPath(new URL("../../", import.meta.url));
const readme = readFileSync(root + "README.md", "utf8");
const fences = [...readme.matchAll(/```(\w+)\r?\n([\s\S]*?)```/g)];

test("README receipt and preview data match the illustrated example", () => {
  const template = fences.find(match => match[1] === "rct")[2].trim();
  const data = JSON.parse(fences.find(match => match[1] === "json")[2]);
  const examples = root + "docs/examples/";
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

test("README shows the engine, the three interfaces and the ESC/POS driver", () => {
  const kotlin = fences.filter(match => match[1] === "kotlin").map(match => match[2]).join("\n");
  assert.match(kotlin, /val receipts = ReceiptScript\(/);
  assert.match(kotlin, /receipts\.render\("sale", data\)/);
  assert.match(kotlin, /receipts\.print\(receipt\)/);
  assert.match(kotlin, /ReceiptPreview\(receipt\)/);
  for (const name of ["ReceiptDriver", "ReceiptTemplateRepository", "ReceiptImageResolver", "EscPosDriver"]) {
    assert.match(kotlin, new RegExp(`${name} ?[({]`), name);
  }
  assert.match(readme, /RCT: Open Preview/);
  assert.match(readme, /sale\.preview\.json/);
  for (const removed of ["RasterReceipt", "ReceiptUiPreview", "EscPosReceiptPrinter", "toImageBytes", "zw.co.donnclab"]) {
    assert.doesNotMatch(readme, new RegExp(removed), removed);
  }
});

test("documentation links resolve", () => {
  const pages = ["README.md", "CONTRIBUTING.md", "CHANGELOG.md", "SECURITY.md"];
  const walk = directory => readdirSync(root + directory, {withFileTypes: true}).flatMap(entry =>
    entry.isDirectory() ? walk(`${directory}/${entry.name}`) : entry.name.endsWith(".md") ? [`${directory}/${entry.name}`] : []);
  for (const page of [...pages, ...walk("docs")]) {
    const text = readFileSync(root + page, "utf8");
    for (const match of text.matchAll(/\]\((?!https?:|#|mailto:)([^)#\s]+)(#[^)]*)?\)/g)) {
      const target = resolvePath(dirname(root + page), match[1]);
      assert.ok(existsSync(target), `${page} -> ${match[1]}`);
    }
  }
});

test("documentation never refers to removed 0.6 names outside the migration guide and changelog", () => {
  const walk = directory => readdirSync(root + directory, {withFileTypes: true}).flatMap(entry =>
    entry.isDirectory() ? walk(`${directory}/${entry.name}`) : entry.name.endsWith(".md") ? [`${directory}/${entry.name}`] : []);
  for (const page of walk("docs").filter(page => !page.includes("/migration/"))) {
    const text = readFileSync(root + page, "utf8");
    for (const removed of ["ReceiptMediaResolver", "AssetReceipt", "EscPosReceiptPrinter", "ReceiptUiPreview", "toImageBytes", "toPdfBytes", "zw.co.donnclab"]) {
      assert.doesNotMatch(text, new RegExp(removed), `${page} mentions ${removed}`);
    }
  }
});
