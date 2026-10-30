# Architecture and extraction report

## Package boundary

ReceiptScript was extracted into an independent checkout. The POS application's
sources remain in their original repository. No application build or device run
is required to build this package.

```text
.rct source + catalog            application context
          |                             |
          v                             |
      parser -> syntax tree             |
          |                             |
          +-> validation                |
          +-> writer (JavaScript)       |
          |                             |
          +---------- bind <------------+
                       |
                 resolved Receipt
                       |
          +------------+------------+
          |            |            |
       HTML/UI       text grid    host SDK adapter
                                    |
                                printer/transport
```

The core neither discovers printers nor sends network/device requests. Adapters
own hardware behavior. Header/footer repositories and image resolvers are
application-supplied functions. The Kotlin library depends on the Kotlin standard
library and JDK; its tests use JUnit. JavaScript's engine has no renderer imports;
its optional HTML entry point imports the barcode encoder.

## Public entry points

Both engines expose `ReceiptScript`, compilation, named loading, render-with-data,
and a typed adapter boundary. Existing low-level parser/binding APIs remain
available. The JavaScript writer supports editors that round-trip syntax trees
to canonical `.rct`; comments and hand formatting are not retained by the writer.

The extension bundles the JavaScript core rather than maintaining another parser
and filter engine. Highlighting and completion metadata still live in the
extension; diagnostics and preview use core behavior.

## Barcode support

Barcode nodes existed previously, but the extension represented them with
decorative bars. They now carry an explicit format across parser, writer, neutral
model, typed declarations and Kotlin output. Local SVG encoding is delegated to
the established BWIP-JS encoder. QR preview uses the same offline route.

The extension enforces a restrictive webview content policy and resolves image
and fragment paths relative to the open template. It does not fetch online QR
images or contact a barcode API. It rejects references escaping the template's
directory, including through symlinks. The application engine intentionally has
no filesystem policy because it never accesses files itself.

## Thermal-printer notes assessed

The supplied portable-printer notes correctly identify the value of a neutral
document, host adapters, measured columns, native versus raster codes, and image
thresholding. The package incorporates those boundaries and a wrapped table
fallback. Some claims need qualification:

- 58/80 mm roll widths do not establish a universal character count.
- Vendor SDKs can provide tables even when a raw command protocol has no table primitive.
- ESC/POS support varies; it is not a universal portable-printer driver.
- Raster output controls glyph placement but cannot guarantee identical physical output.
- Thermal image conversion and transport belong in the renderer/adapter, not the parser.

## Compatibility and limits

Kotlin retains semantic divider/row blocks while JavaScript currently lowers
them to role-tagged text. Adapter models are structurally similar, not a
versioned cross-language wire protocol. Both preserve the established language.

JavaScript now treats an out-of-range collection index as missing rather than
accidentally returning the collection. Strict public APIs reject missing bindings
and inconsistent table shapes. Legacy low-level binding calls remain permissive.

There is no runtime-code evaluation in conditions. Nevertheless, template source
and context must be trusted or validated by the host: huge nested loops, large
dimensions, object getters and large strings can consume resources. Impose input
and output limits when accepting templates from untrusted authors.

Kotlin accepts objects through reflection. For Android release builds, prefer
maps or keep the names of DTO properties used by templates. Date parsing and
object semantics are not guaranteed identical between all JavaScript/JVM types;
use shared JSON-shaped contracts and fixture tests for your deployments.

No physical printer has been certified by this extraction. The optional Kotlin
`drivers/escpos` module now supplies an ESC/POS encoder with local raster codes,
plain-text output, stream/TCP connections and an Android USB connection example.
The core still has no hardware dependencies. The visual portal, standalone
Android image/PDF exporter and arbitrary-font raster text renderer remain host
integrations. See the driver reference for explicit protocol/profile assumptions.

## Release evidence

The checkout provides package scripts for core/extension tests, TypeScript API
checks, VSIX packaging, a separate Gradle test/publication build, and CI jobs.
The release checklist records what must be verified before publishing. Build
artifacts and local dependencies are ignored by Git. Dependencies retain license
notices; project source contains no generated-attribution headers.
