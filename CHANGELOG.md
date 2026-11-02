# Changelog

All notable changes to ReceiptScript are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/). Until 1.0, a minor version (0.x) may contain breaking
changes; each one is listed with a migration note.

## [Unreleased]

## [0.7.0] - Unreleased

A smaller, clearer API under a new package. See the [migration guide](docs/migration/0.7.md).

### Changed (breaking)

- Package renamed from `zw.co.donnclab.rct` to `io.github.donnclab.rct`, and Maven group from
  `io.github.donnclab.receiptscript` to `io.github.donnclab`. The Android classes moved from
  `…rct.android` into the main package.
- One `ReceiptDriver` with a single `suspend fun print(receipt)` replaces both the bitmap driver and
  the nine-method block driver. Drivers read `receipt.bands` or `receipt.blocks`.
- `ReceiptMediaResolver<T>` replaced by `ReceiptImageResolver`, which suspends, so images can come
  from the network or a database.
- `ReceiptTemplateRepository`, `ReceiptImageResolver` and `ReceiptDriver` are `fun interface`s and
  can be written as lambdas. `args` stays optional for callers through a `getTemplate(id)` extension.
- Renamed: `AssetReceiptTemplateRepository` → `AssetTemplateRepository`,
  `AssetReceiptMediaResolver` → `AssetImageResolver`, `EscPosReceiptPrinter` → `escpos.EscPosDriver`,
  `ReceiptUiPreview` → `ReceiptPreview`, `toImageBytes`/`toPdfBytes` → `toImage`/`toPdf`.
- The platform-independent engine is now `RctCompiler` producing `ReceiptDocument`; the byte encoders
  are `EscPosEncoder` and `PlainTextEncoder`.
- Template value types renamed to the `Receipt*` prefix: `ReceiptMeta`, `ReceiptAlign`,
  `ReceiptFontSize`, `ReceiptFontScaleMode`, `ReceiptColumnAlign`, `ReceiptBarcodeFormat`.
- `RctLinter` and `RctLexerScanner` moved to `io.github.donnclab.rct.tooling`.

### Added

- `BluetoothPrinterConnection` (Bluetooth Classic SPP) and `UsbPrinterConnection` (USB bulk).
- `EscPosMode.TEXT` for `EscPosDriver`, which prints with the printer's built-in fonts.
- `ReceiptScript.print(templateId, data)` to render and print in one call.
- Documentation site under `docs/`: getting started, a full template language reference, guides
  and architecture. The guides' main examples are compiled with the tests.
- `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, issue forms and monthly Dependabot updates.

### Removed

- Parser, syntax tree, template engine, renderer and exporter classes are now internal.
- `ReceiptAdapter`, `escpos.ReceiptPrinter`, and the deprecated `RasterReceipt`, `RasterOptions`
  and `ReceiptPrinter` aliases.

### Fixed

- Example app receipts that use a logo failed with "Unresolved image": the sample data now uses
  `asset:` references.
- The library declared `kotlin-stdlib` 2.2.20 while compiling with Kotlin 2.3.20.
- The example app used a different Compose BOM from the library.

### Internal

- Kotlin explicit API mode is enabled, for both the Gradle build and the JVM release build.
- ESC/POS command bytes are named constants (`EscPosCommands`) instead of bare numbers.
- The example app is down to four source files, and repository fixtures no longer ship inside its
  APK: the README template moved to `docs/examples/`, the Node demo template to `tools/examples/node/`,
  and the remaining samples into the JS engine's test fixtures.
- Dependabot opens one grouped pull request per ecosystem each month.
- Uncompiled integration snippets in `src/test/fixtures` replaced by compiled examples.

## [0.6.0] - 2026-11-01

- One Android AAR containing the engine, ESC/POS support, Android rendering, exports and the Compose
  preview, replacing separate core, ESC/POS, Android and Compose artifacts.
- Unified Android client: `ReceiptScript(context, driver, repository, images)` with `render`,
  `renderSource` and `print`.
- Image and PDF export from a rendered receipt.

## [0.5.0] - 2026-10-31

- Android starter app with Compose previews for every example.
- Raster previews shared by the app, exports and printing.

## [0.4.0] - 2026-10-30

- First public build: the `.rct` template language, the Kotlin and JavaScript engines, the ESC/POS
  driver, and the VS Code extension.

[Unreleased]: https://github.com/DonnC/receiptscript/compare/v0.7.0...HEAD
[0.7.0]: https://github.com/DonnC/receiptscript/compare/v0.6.0...v0.7.0
[0.6.0]: https://github.com/DonnC/receiptscript/releases/tag/v0.6.0
[0.5.0]: https://github.com/DonnC/receiptscript/releases/tag/v0.5.0
[0.4.0]: https://github.com/DonnC/receiptscript/releases/tag/v0.4.0
