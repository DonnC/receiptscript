const {test} = require("node:test");
const assert = require("node:assert/strict");
const {lint} = require("../dist/core.cjs");
test("shared diagnostics reject unknown filters and accept typed barcodes", () => {
  assert.ok(lint('receipt "s"\nleft "{{a | nope}}"').some(issue => issue.code === "unknown-filter"));
  assert.deepEqual(lint('receipt "s"\nbarcode "5901234123457" 300 60 "ean13"'), []);
});
