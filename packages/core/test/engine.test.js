// Ports of rct-core's RctTemplateEngineTest.
import { test } from "node:test";
import assert from "node:assert/strict";

import { createContext, isTruthy, render, resolveValue } from "../src/index.js";

const context = createContext(
  {
    merchant: { name: "Test Merchant" },
    txn: { amount: 20.0, currency: "USD", date: "2026-03-31T10:15:30", invoiceId: "INV-001" },
    items: [{ label: "Cash", count: 2 }, { label: "Card", count: 5 }],
    card: { pan: "4506911234567890" },
  },
  { item: { label: "Wallet", count: 1 }, "@index": 0 },
);

test("reads nested root values", () => {
  assert.equal(resolveValue("merchant.name", context), "Test Merchant");
  assert.equal(resolveValue("txn.invoiceId", context), "INV-001");
});

test("prefers locals and supports list indexes", () => {
  assert.equal(resolveValue("item.label", context), "Wallet");
  assert.equal(resolveValue("items[1].label", context), "Card");
  assert.equal(resolveValue("@index", context), 0);
  assert.equal(resolveValue("@root.txn.currency", context), "USD");
});

test("combines literals and bindings", () => {
  assert.equal(render("Hello {{merchant.name}}!", context), "Hello Test Merchant!");
});

test("applies core filters", () => {
  assert.equal(render("{{txn.amount | money:txn.currency | upper}}", context), "USD 20.00");
});

test("supports default and maskPan", () => {
  assert.equal(render('{{customer.name | default:"Walk In"}}', context), "Walk In");
  assert.equal(render("{{card.pan | maskPan}}", context), "450691******7890");
});

test("supports configurable masking", () => {
  const maskContext = createContext({
    security: { pin: "1234", password: "secret" },
    customer: { mobile: "0778006236", nationalId: "63-123456A75" },
  });

  assert.equal(render("{{security.pin | mask}}", maskContext), "****");
  assert.equal(render("{{customer.mobile | mask:0,2}}", maskContext), "********36");
  assert.equal(render("{{customer.nationalId | mask:2,3}}", maskContext), "63*******A75");
  assert.equal(render('{{security.password | mask:1,1,"#"}}', maskContext), "s####t");
});

test("never reveals a value shorter than its visible edges", () => {
  assert.equal(render("{{secret | mask:4,4}}", createContext({ secret: "1234" })), "****");
});

test("supports date, datetime and padding", () => {
  const custom = createContext({ ...context.root, txn: { ...context.root.txn, compactDate: "20260331101530" } });

  assert.equal(render("{{txn.date | date}}", context), "31/03/2026");
  assert.equal(render("{{txn.date | datetime}}", context), "31/03/2026 10:15:30");
  assert.equal(render('{{txn.compactDate | datetime:\\"dd MMM yyyy HH:mm\\",\\"yyyyMMddHHmmss\\"}}', custom), "31 Mar 2026 10:15");
  assert.equal(render('{{txn.invoiceId | padRight:10,"."}}', context), "INV-001...");
});

test("follows receipt-friendly truthiness", () => {
  assert.equal(isTruthy("merchant.name", context), true);
  assert.equal(isTruthy("items", context), true);
  assert.equal(isTruthy("customer.name", context), false);
  assert.equal(isTruthy("empty", createContext({ empty: "" })), false);
});

test("returns null for missing paths", () => {
  assert.equal(resolveValue("merchant.address", context), null);
  assert.equal(resolveValue("items[5].label", context), null);
});

test("rounds money half up on the decimal digits, like BigDecimal", () => {
  const amounts = createContext({ a: 1.005, b: "2.345", c: -1.005, d: 1e21, e: "abc" });

  assert.equal(render("{{a | money}}", amounts), "1.01");
  assert.equal(render("{{b | money:USD}}", amounts), "USD 2.35");
  assert.equal(render("{{c | money}}", amounts), "-1.01");
  assert.equal(render("{{d | money}}", amounts), "1000000000000000000000.00");
  assert.equal(render("{{e | money}}", amounts), "abc");
});

test("fails like the terminal on an unknown filter or a time field in date", () => {
  assert.throws(() => render("{{merchant.name | shout}}", context), /Unsupported filter: shout/);
  assert.throws(() => render('{{txn.date | date:"HH:mm"}}', context), /Unsupported field/);
});
