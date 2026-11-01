# Third-party notices

ReceiptScript source is MIT licensed. Dependencies and tooling retain their own
licenses; do not remove required notices from redistributed artifacts.

| Component | Purpose | License / source |
| --- | --- | --- |
| BWIP-JS | Optional HTML QR/barcode encoding and VS Code preview | MIT; includes BWIPP and font notices, see installed package license files and https://github.com/metafloor/bwip-js |
| Inconsolata | Android raster renderer font | SIL Open Font License 1.1; font and license from BWIP-JS, notice included in the Android AAR assets |
| Kotlin standard library | Kotlin runtime | Apache-2.0, https://github.com/JetBrains/kotlin |
| ZXing core | Android and ESC/POS QR/barcode encoding | Apache-2.0, https://github.com/zxing/zxing |
| AndroidX Compose | In-app receipt preview | Apache-2.0, https://android.googlesource.com/platform/frameworks/support/ |
| Kotlin coroutines | Background rendering and suspending printer integration | Apache-2.0, https://github.com/Kotlin/kotlinx.coroutines |
| Gradle wrapper | Reproducible builds | Apache-2.0, https://github.com/gradle/gradle |
| JUnit | JVM tests | EPL-1.0, https://junit.org/junit4/ |
| esbuild | Extension bundling, development only | MIT, https://github.com/evanw/esbuild |
| Playwright core | Reproducible README screenshots, development only | Apache-2.0, https://github.com/microsoft/playwright |
| yazl | Binary release ZIP/JAR assembly, development only | MIT, https://github.com/thejoshwolfe/yazl |

The lockfile records transitive JavaScript dependencies. Distribution builds copy
the barcode encoder's license files alongside its bundle. Inspect notices whenever
upgrading or changing the bundled renderer.
