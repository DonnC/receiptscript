# Verification notes

## Local results

| Check | Result |
| --- | --- |
| JavaScript core | 93 tests passed |
| VS Code preview integration | 3 tests passed |
| TypeScript consumer API | Passed |
| Browser bundling of engine and HTML renderer | Passed |
| Kotlin source, test and adapter-example compilation | Passed, Kotlin 2.3.20 / JVM 11 target |
| Kotlin core and optional drivers | 49 tests passed on JDK 17 |
| Android Bitmap, asset/stream, paired-SPP and USB examples | Compiled against Android API 35, not run on device |
| VSIX | Built, including offline encoder and third-party notices |
| npm archive | Built with source, TypeScript declarations, README and license |
| Kotlin binary distribution | Built with pinned, SHA-256-verified compiler/dependency inputs |
| Independent binary consumer | Compiled and executed using assembled JARs only |
| Binary archive integrity | Archive hash and all 42 internal file checksums verified; library JARs contain no test classes |
| README media | Example rendered from .rct/JSON and captured in Chromium; banner and vector logo included |
| Gradle test/publication tasks | Blocked before compilation by local loopback failure |
| Physical printer / Extension Development Host | Not exercised in this checkout |

Build artifacts live in `artifacts/` and are excluded from Git. The existing POS
repository was not modified. The optional ESC/POS and plain-text drivers are
included; the visual portal is outside this extraction. Byte-level encoding,
table wrapping, raster slicing, QR decode and transport error paths were tested.
TCP socket delivery, Android runtime connections and physical printer output
have not been exercised.

The binary distribution build is independent of Gradle's daemon and succeeds in
this environment. It produces `receiptscript-0.3.0-binary.zip`, a checksum sidecar,
and individual project JARs. The Gradle limitation below still applies to Gradle
task/publication verification; it no longer prevents a tested binary distribution.

## JVM environment

The source package is designed for independent Node and JVM verification. The
Windows environment used for extraction has a Gradle daemon loopback failure:
`java.io.IOException: Unable to establish loopback connection`. This occurs before
project compilation, with both JBR 25 and JDK 17. Do not confuse it with a passing
Gradle build. The CI workflow remains the standard clean-environment build path.

As an independent source check, Kotlin sources and tests were compiled with the
cached Kotlin 2.3.20 compiler targeting JVM 11 and executed with JUnit 4.13.2 on
JDK 17. That check does not validate Gradle publication tasks or Maven metadata.

JavaScript verification includes the existing parser/filter/layout fixtures,
high-level API behavior, missing data, local shared sections, offline SVG output,
all public examples, TypeScript consumer usage, and VS Code preview integration.

Hardware acceptance is separate. Test native SDK typography, table alignment,
barcodes/QR scanning, long receipts and paper errors on your target devices.
