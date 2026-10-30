# ReceiptScript Kotlin

An Android-independent JVM library targeting Java 11. Build using JDK 17:

```sh
./gradlew :kotlin:test :kotlin:publishToMavenLocal
```

```kotlin
val engine = ReceiptScript(loadTemplate = { id -> templates[id] })
val template = engine.compile(source)
val receipt = template.render(context)
val output = receipt.render(ReceiptAdapter { document -> document.blocks })
```

Implement `ReceiptDriver` for a per-block vendor SDK, or `ReceiptAdapter<T>` for
a custom output result. The optional `drivers/escpos` module includes usable
ESC/POS and plain-text encoders with stream/TCP delivery.

`Receipt` preserves resolved text, typography, key/value blocks, semantic dividers,
rows, tables, images, QR and barcode format information. `ReceiptAdapter<T>` can
return your SDK result, bytes or a preview model. SDK/Android types do not enter
the engine. This core artifact has no printer transport or platform Bitmap/PDF exporter.

The original `zw.co.donnclab.rct` namespace is retained for source compatibility.
Publication coordinates are `io.github.donnclab.receiptscript:receiptscript-core`.
Use `BigDecimal` for exact monetary values. Maps work without reflection rules;
if passing DTOs on Android, preserve the names of properties bound by templates.

See the repository's integration, language and adapter documentation for contracts
and limitations. JVM and JavaScript output models are not a shared wire protocol.
