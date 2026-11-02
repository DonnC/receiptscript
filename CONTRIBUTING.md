# Contributing to ReceiptScript

Thanks for helping. Bug reports, printer compatibility notes, documentation fixes and code are all
welcome.

## Before you start

- **Bugs:** open an issue with the bug report form. Include a minimal template and fictional data.
  Never post real card numbers, customer details or production receipts.
- **Features and API changes:** open an issue first so we can agree on the design. The public API is
  intentionally small (see [Architecture](docs/architecture.md)), so new public types need a strong
  reason.
- **Security issues:** do not open a public issue. Follow [SECURITY.md](SECURITY.md).

## Set up

You need:

- JDK 17 or newer
- Android SDK with platform 36 (Android Studio installs it)
- Node.js 22 or newer, for the VS Code extension, the JavaScript engine and the JVM release build

```shell
git clone https://github.com/DonnC/receiptscript.git
cd receiptscript
npm ci --prefix tools
```

Open the repository root in Android Studio and run **ReceiptScript Starter** to try the example app.

## Run the checks

These are the same checks CI runs:

```shell
# Android library and example app: unit tests, release AAR, instrumented test APK
./gradlew :testDebugUnitTest :example:testDebugUnitTest :assembleRelease :assembleDebugAndroidTest :example:assembleDebug

# JavaScript engine, VS Code extension, README and project-structure tests
npm test --prefix tools

# JVM JAR: compiles the platform-independent sources alone, runs their tests, verifies a consumer
npm run release:kotlin --prefix tools
```

Instrumented tests (`src/androidTest`) need a device or emulator: `./gradlew :connectedDebugAndroidTest`.

## Where things live

| Path | Contents |
| --- | --- |
| `src/main/kotlin` | platform-independent code: compiler, blocks, ESC/POS encoder. **No Android imports.** |
| `src/main/java` | Android code: `ReceiptScript`, rendering, exports, `EscPosDriver`, Compose preview |
| `src/test/kotlin` | JVM unit tests, and the JVM example used by the docs |
| `src/test/java` | Robolectric tests, and the Android examples used by the docs |
| `example/` | the starter app and its sample templates |
| `docs/examples/` | the README template and its preview data, also used by tests and the README image |
| `tools/web` | the JavaScript engine used by the VS Code preview |
| `tools/vscode-extension` | the VS Code extension |
| `docs/` | documentation |

[Architecture](docs/architecture.md) explains how the pieces fit.

## Making changes

- **Public API:** explicit API mode is on. Mark new declarations `internal` unless users need them.
  Add KDoc to everything public, and say what callers must do and what can go wrong.
- **Template language:** grammar changes must be made in both engines, Kotlin and JavaScript, and in
  `rct-language-spec.json` and the [language reference](docs/language-reference.md). See
  "Two implementations of the language" in [Architecture](docs/architecture.md).
- **Docs:** the main guide examples are copied from compiled code in `src/test/*/examples`. Change
  the example first, then copy it into the guide. `npm test --prefix tools` checks every link in the docs.
- **Tests:** add a test that fails without your change.
- **Changelog:** add a line under `## [Unreleased]` in [CHANGELOG.md](CHANGELOG.md), describing the
  change from a user's point of view.
- **Style:** follow `.editorconfig`: 4-space Kotlin, 2-space everything else, LF line endings.

## Pull requests

- Keep each pull request to one change. Small pull requests are reviewed faster.
- Fill in the pull request template, including how you tested. For printer changes, say which
  printer and connection you used.
- CI must pass.

## Releasing (maintainers)

1. Move the `[Unreleased]` changelog entries under a new version heading with today's date.
2. Write the version to `VERSION`, then run `npm run version:sync --prefix tools`.
3. Commit, tag `vX.Y.Z`, and push the tag. The **Binary distribution** workflow builds the AAR, the
   JVM JAR, the example APK and the VS Code extension, and attaches them to a GitHub release.

## Code of conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
