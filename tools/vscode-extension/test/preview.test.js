const {test} = require("node:test");
const assert = require("node:assert/strict");
const {mkdtempSync, writeFileSync, rmSync} = require("node:fs");
const {tmpdir} = require("node:os");
const {join} = require("node:path");
const {preview} = require("../extension.js");
test("extension uses core tables, offline QR/barcode and escaped text", () => {
  const html = preview('receipt "s"\nleft "{{name}}"\ntable:\n  headers: "A" "B"\n  row "1" "2"\nqr "local"\nbarcode "ABC"', {name: "<test>"}, join(__dirname, "sample.rct"));
  assert.match(html, /&lt;test&gt;/);
  assert.match(html, /<table>/);
  assert.equal((html.match(/<svg/g) ?? []).length, 2);
  assert.doesNotMatch(html, /qrserver|<script/);
  assert.match(html, /Content-Security-Policy/);
});
test("local footer follows body with the default gap; escaping references fail", () => {
  const directory = mkdtempSync(join(tmpdir(), "rct-preview-"));
  try {
    writeFileSync(join(directory, "thanks.rct"), 'center "THANKS"');
    const html = preview('receipt "s"\nuse footer "thanks"\nleft "BODY"', {}, join(directory, "sample.rct"));
    assert.ok(html.indexOf("BODY") < html.indexOf("THANKS"));
    assert.equal((html.match(/class="spacer"/g) ?? []).length, 2);
    assert.throws(() => preview('receipt "s"\nuse header "../outside"', {}, join(directory, "sample.rct")), /escapes/);
  } finally {rmSync(directory, {recursive: true, force: true});}
});
