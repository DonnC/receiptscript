// Every real template (the app's bundled receipts and the ReceiptScript examples) must parse, lint
// clean, lay out against its sample data, and survive a write/parse round trip unchanged.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { composeFragments, createContext, layout, lint, parse, parseFragment, write, writeFragment } from "../src/index.js";

const FIXTURES = join(dirname(fileURLToPath(import.meta.url)), "fixtures");
const read = (name) => readFileSync(join(FIXTURES, name), "utf8");
const isFragment = (text) => !/^\s*receipt\s+"/m.test(text.split("\n").find((line) => line.trim() && !line.trim().startsWith("#")) ?? "");

const templates = readdirSync(FIXTURES).filter((name) => name.endsWith(".rct"));

for (const name of templates) {
  const text = read(name);
  const fragment = isFragment(text);

  test(`${name}: lints clean`, () => {
    assert.deepEqual(lint(text, { fragment }).filter((issue) => issue.severity === "error"), []);
  });

  test(`${name}: round-trips through the writer`, () => {
    if (fragment) {
      const nodes = parseFragment(text);
      assert.deepEqual(parseFragment(writeFragment(nodes)), nodes);
    } else {
      const document = parse(text);
      assert.deepEqual(parse(write(document)), document);
    }
  });

  const sample = name.replace(/\.rct$/, ".preview.json");
  if (!fragment && existsSync(join(FIXTURES, sample))) {
    test(`${name}: lays out against its sample data`, () => {
      const { document, missing } = composeFragments(parse(text), (id) =>
        existsSync(join(FIXTURES, `${id}.rct`)) ? read(`${id}.rct`) : null);
      const blocks = layout(document, createContext(JSON.parse(read(sample))));

      assert.deepEqual(missing, []);
      assert.ok(blocks.length > 0);
      assert.ok(blocks.every((block) => block.type));
    });
  }
}
