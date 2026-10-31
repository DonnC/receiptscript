import { test } from "node:test";
import assert from "node:assert/strict";

import { lint, write, parse } from "../src/index.js";

const codes = (issues) => issues.map((issue) => `${issue.code}@${issue.line}`);

test("reports an unsupported statement at its line, like RctLinter", () => {
  assert.deepEqual(codes(lint('receipt "x"\n\nshout "hi"')), ["parse@3"]);
});

test("reports a malformed interpolation", () => {
  assert.ok(codes(lint('receipt "x"\ncenter "{{merchant.name"')).includes("unterminated@2"));
});

test("does not flag dotted paths outside quotes", () => {
  assert.deepEqual(lint('receipt "x"\nif result.bankHostData.stan:\n  left "{{result.bankHostData.stan}}"'), []);
});

test("catches what would only fail while printing", () => {
  const issues = lint([
    'receipt "x"',
    'left "{{a | shout}}"',
    'left "{{b | date:\\"dd/MM HH:mm\\"}}"',
    'left "{{1bad.path}}"',
  ].join("\n"));

  assert.deepEqual(codes(issues), ["unknown-filter@2", "date-time-field@3", "invalid-path@4"]);
});

test("allows a time in datetime and in quoted date literals", () => {
  assert.deepEqual(lint('receipt "x"\nleft "{{a | datetime:\\"HH:mm\\"}}"\nleft "{{b | date:\\"dd \'at\' MM\\"}}"'), []);
});

test("flags a block left empty in the designer, which the grammar can't express", () => {
  const document = parse('receipt "x"\nif a:\n  left "y"');
  document.body[0].children = [];

  assert.throws(() => parse(write(document)), /Expected indented block/);
  assert.equal(lint(write(document))[0].code, "parse");
});

test("treats a fragment's own rules separately from a receipt's", () => {
  assert.deepEqual(lint('center "{{merchant.merchantName}}"', { fragment: true }), []);
  assert.equal(lint('center "x"')[0].code, "parse");
  assert.equal(lint('receipt "x"\nleft "a"', { fragment: true })[0].code, "parse");
});
