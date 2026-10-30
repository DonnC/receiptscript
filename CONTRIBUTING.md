# Contributing

Keep the language readable, the resolved model vendor-neutral, and integrations
small enough to adopt without a framework. A change to parser semantics should
include a template fixture, expected output, and both runtime implementations
when applicable. Start with a failing behavior test for engine changes.

```sh
npm ci
npm test
npm run check:types
./gradlew :kotlin:test :drivers:escpos:test
```

Use two spaces in JavaScript/JSON and four in Kotlin. Comments should explain
invariants, assumptions or decisions. Prefer concise public documentation over
comments that restate the code. Avoid printer SDK dependencies in either engine.

For new output targets, implement `ReceiptAdapter<T>` and demonstrate a real
integration. Describe supported fonts, media, code formats and failure behavior.
Include hardware evidence when claiming compatibility with a specific printer.

Before submitting, keep generated artifacts out of Git, update the changelog and
API reference, and ensure sample data is fictional and contains no secrets.
