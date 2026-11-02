# Using the engine on the JVM

The template engine and ESC/POS encoder do not depend on Android. The release page publishes them as
`receiptscript-<version>.jar` for servers, desktop tools and plain Kotlin tests. They need only
`kotlin-stdlib`, plus ZXing if templates contain QR codes or barcodes.

```kotlin
fun renderOnServer(source: String, data: Any?): ReceiptDocument = RctCompiler().compile(source).render(data)

fun printFromServer(document: ReceiptDocument, host: String) {
    val bytes = EscPosEncoder(EscPosProfile.Receipt80).encode(document)
    TcpPrinterConnection(host).send(bytes)
}
```

## What is available

| Type | Purpose |
| --- | --- |
| `RctCompiler` | compiles template source and binds data into a `ReceiptDocument` |
| `RctTemplate` | a compiled template; `render(data)` many times with no further I/O |
| `ReceiptDocument` | `name`, `meta` and the printer-neutral `blocks` |
| `EscPosEncoder` | turns a document, or pre-rendered `MonochromeImage` bands, into ESC/POS bytes |
| `PlainTextEncoder` | text-only output for printers or displays that accept raw text |
| `PrinterConnection`, `OutputStreamConnection`, `TcpPrinterConnection` | transports |
| `tooling.RctLinter`, `tooling.RctLexerScanner` | checking and highlighting template source |

Android apps get all of these in the AAR too, but normally only need `ReceiptScript`.

## Fragments

`RctCompiler` loads `use header`/`use footer` fragments either from a synchronous lookup:

```kotlin
val compiler = RctCompiler(templates = mapOf("merchant" to merchantSource))
compiler.compile(saleSource).render(data)
```

or from a `ReceiptTemplateRepository` in a coroutine:

```kotlin
compiler.template("sale", repository, args = emptyMap()).render(data)
```

## Images

`EscPosEncoder` resolves `image` references with a function you pass in:

```kotlin
EscPosEncoder(EscPosProfile.Portable58) { source -> MonochromeImage.fromArgb(width, height, argbPixels) }
```

Build a `MonochromeImage` from ARGB pixels (`fromArgb`) or from a function (`fromPixels`).

The JVM example is in [`src/test/kotlin/examples/Jvm.kt`](../../src/test/kotlin/examples/Jvm.kt).
