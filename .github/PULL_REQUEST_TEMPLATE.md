## What and why

<!-- What does this change, and what problem does it solve? Link the issue, e.g. "Fixes #12". -->

## How it was tested

<!-- Tests added or updated. For printing changes: printer model, paper width and connection. -->

## Checklist

- [ ] Tests cover the change, and `./gradlew :testDebugUnitTest` and `npm test --prefix tools` pass
- [ ] New public API is intentional, has KDoc, and anything else is `internal`
- [ ] Template language changes are made in both engines, the language spec JSON and the language reference
- [ ] Docs updated if behaviour changed (guide examples come from `src/test/*/examples`)
- [ ] `CHANGELOG.md` has an entry under `[Unreleased]`
- [ ] No real customer, card or transaction data in code, tests or screenshots
