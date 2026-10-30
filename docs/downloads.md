# Binary distribution

ReceiptScript's Kotlin libraries are plain JVM JARs. Android can consume them
directly; an AAR would add packaging without providing Android resources,
manifest entries or native code. No source checkout is needed to use the binaries.

The versioned `receiptscript-0.3.0-binary.zip` archive contains:

| Location | Contents | When needed |
| --- | --- | --- |
| `lib/receiptscript-core-0.3.0.jar` | Engine, resolved receipt and driver interfaces | Every Kotlin integration |
| `lib/receiptscript-escpos-0.3.0.jar` | Optional ESC/POS/plain-text drivers and connections | Compatible portable/desktop printers |
| `optional/zxing-core-3.5.3.jar` | Barcode/QR encoder dependency | With the optional driver, if not resolved by Gradle |
| `runtime-jvm/kotlin-stdlib-2.3.20.jar` | Kotlin standard library | Standalone JVM programs that do not already supply it |
| `sources/` | Matching source JARs | IDE navigation and debugging; not application dependencies |
| `examples/` | The README receipt and sample context | A starting template |
| `SHA256SUMS`, `release.json` | File hashes, version and verification metadata | Artifact inspection |

The archive and individual project JARs are produced in `artifacts/` by the release
build. An adjacent `.sha256` file records the archive hash. This checkout includes
that local distribution. Public release assets have not yet been uploaded to the
project's [Releases page](https://github.com/DonnC/receiptscript/releases).

## Android: local JAR dependencies

Place the selected project JARs in your app's `libs` directory. Core only:

```kotlin
dependencies {
    implementation(files("libs/receiptscript-core-0.3.0.jar"))
}
```

For the bundled ESC/POS driver:

```kotlin
dependencies {
    implementation(files("libs/receiptscript-core-0.3.0.jar"))
    implementation(files("libs/receiptscript-escpos-0.3.0.jar"))
    implementation("com.google.zxing:core:3.5.3")
}
```

For an offline project, the included `optional/zxing-core-3.5.3.jar` can be added
as a local dependency instead of the Maven declaration. Use one copy of ZXing.
Local JARs do not carry Gradle transitive dependency resolution.

Do **not** add `runtime-jvm/kotlin-stdlib-2.3.20.jar` to an Android project whose
Kotlin plugin already supplies the standard library. Likewise, source JARs do not
belong in the app's dependency directory. Keeping these files separate avoids
duplicate classes. A compatible Kotlin 2.3 compiler is required; the bytecode
target is JVM 11. Android below API 26 requires Java time desugaring.

Applications render templates and implement drivers exactly as described in the
Android and driver guides; using a local JAR does not change the API.

## Standalone JVM applications

Add the core JAR and the Kotlin standard library to the runtime classpath. If using
ESC/POS, also add its JAR and ZXing. Standard Kotlin Gradle projects normally
resolve the standard library themselves. The project JARs intentionally do not
bundle duplicate Kotlin/runtime classes into a fat JAR.

## Release integrity

The binary builder pins compiler and dependency inputs with SHA-256 checksums,
executes engine/driver tests, and compiles and runs a separate consumer using only
the assembled JARs. Test classes are excluded from library artifacts. Source
JARs match the packaged source tree. File checksums support integrity checking;
they are not a cryptographic signature or a claim of printer certification.

The local `0.3.0` package is a versioned, tested candidate. A public stable release
also requires the maintainer's release review, clean-environment build and target
device acceptance. Registry/Marketplace publication is a separate operation.
