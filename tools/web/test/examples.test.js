import {test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync, readdirSync} from "node:fs";
import {ReceiptScript} from "../src/index.js";
import {HtmlRenderer} from "../src/html.js";

function visit(directory) {
  for (const entry of readdirSync(directory, {withFileTypes: true})) {
    if (["build", ".gradle", ".kotlin", ".idea"].includes(entry.name)) continue;
    const url = new URL(entry.name + (entry.isDirectory() ? "/" : ""), directory);
    if (entry.isDirectory()) visit(url);
    else if (entry.name.endsWith(".preview.json")) {
      test(`public example ${entry.name}: renders through the public API with real media`, () => {
        const template = new URL(entry.name.replace(".preview.json", ".rct"), directory);
        const engine = new ReceiptScript({loadTemplate: id => readFileSync(new URL(`${id}.rct`, directory), "utf8")});
        const receipt = engine.compile(readFileSync(template, "utf8")).render(JSON.parse(readFileSync(url, "utf8")));
        const html = receipt.render(new HtmlRenderer({resolveImage: ref => `data:image/png;base64,${readFileSync(new URL(ref, directory)).toString("base64")}`}));
        assert.match(html, /<main class="receipt">/);
      });
    }
  }
}
visit(new URL("../../../example/src/main/assets/", import.meta.url));
