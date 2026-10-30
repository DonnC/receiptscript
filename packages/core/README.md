# @receiptscript/core

Vendor-neutral receipt templates for JavaScript and TypeScript.

```js
import {ReceiptScript} from '@receiptscript/core';
import {HtmlRenderer} from '@receiptscript/core/html';
const template = new ReceiptScript().compile('receipt "sale"\nkv "TOTAL" "{{total | money:USD}}"');
const receipt = template.render({total: '25.25'});
const html = receipt.render(new HtmlRenderer());
```

Reuse compiled templates across renders. Supply `templates` or `loadTemplate(id)`
for named templates and shared header/footer fragments. Load remote data first;
the engine itself performs no I/O. Missing bindings fail by default. `if` marks
optional content; `default` supplies fallback values.

Adapters implement `render(receipt)` and choose the output type. Built-in optional
entry points are `/html` (HTML with offline QR/barcodes) and `/text` (wrapped
character-grid layout). Core imports do not load the barcode renderer. No printer
connection or hardware driver is included.

Source installation and the full language, Kotlin and adapter documentation are
in the [ReceiptScript repository](https://github.com/DonnC/receiptscript).
This checkout's 0.3.0 version has not yet been published to a registry.

Run `npm test` in this package for parser, engine, writer, layout and output tests.
MIT licensed. The optional HTML renderer uses BWIP-JS under its own license notices.
