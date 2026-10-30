# Release checklist

`0.3.0` is a local release candidate. The package coordinates and repository URLs
are proposed project metadata; publication is a separate maintainer action.

## Binary artifacts

`npm run release:kotlin` builds the versioned JAR distribution with JDK 17 or newer.
It uses the pinned Kotlin compiler and dependencies in
`scripts/kotlin-toolchain.lock.json`; verified Gradle cache entries are reused or
the same artifacts are downloaded from Maven Central. `JAVA_HOME` selects the JDK.
The script is independent of Gradle's daemon, making it usable in environments
where the documented loopback issue prevents Gradle from starting.

The release build compiles production code, executes the Kotlin test suite,
assembles engine/driver and source JARs, and compiles/runs a separate consumer
against those JARs. Only then are the binary ZIP, individual project JARs and
archive checksum written into `artifacts/`. Compiler and test-tool dependencies
are not distributed as runtime libraries. The optional ZXing dependency and the
standalone JVM standard library are clearly separated from Android project JARs.

Standard Gradle builds remain available through `:kotlin:assemble` and
`:drivers:escpos:assemble`. Their JARs use the same versioned project names and
can be published through the existing Maven publications. `releaseBundle` is a
Gradle entry point to the verified binary distribution script after `npm ci`.

The binary-distribution workflow builds on Windows and Linux and uploads reviewable
artifacts on demand or for a version tag. It does not publish a GitHub release or
promote a build to stable automatically. Version tags, release notes and hardware
qualification are maintainer decisions. See [binary consumption](downloads.md).

## Verify

1. Run `npm ci`, `npm test`, and `npm run check:types` on supported Node versions.
2. Run `./gradlew :kotlin:test :drivers:escpos:test :kotlin:publishToMavenLocal :drivers:escpos:publishToMavenLocal` with JDK 17.
3. Run `npm run check:packages` and inspect the archive contents.
4. Run `npm run package:extension`; install the VSIX into a clean VS Code profile.
5. Open each example and test live template, JSON, fragment and image updates.
6. Scan barcode and QR output at the intended printed dimensions.
7. Test adapters on real hardware, including long tables, diacritics and paper errors.
8. Review license ownership, public coordinates, registry access, signing and repository links.
9. Check the changelog and dependency audit. Tag only after required checks pass.

## Publish

The JS artifact is `@receiptscript/core`; a scoped registry owner must authorize
publication. `npm pack` builds a reviewable local archive without publishing.
The VS Code publisher is `donnclab`; verify ownership before a Marketplace release.

The Gradle core module provides sources, documentation jars and Maven POM metadata;
the optional ESC/POS module has a separate publication and sources jar.
`publishToMavenLocal` is for local integration. Maven Central repository credentials,
deployment configuration, signatures, namespace verification and a staging release
must be configured by the maintainer before a public Maven release. The generated
Javadoc jar does not yet contain a full Dokka API reference; API guidance and KDoc
are provided in source and these docs.

No workflow in this repository automatically publishes packages or uses secrets.
Keep publishing as a reviewed, separate release action.
