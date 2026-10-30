# JavaScript tooling

The JavaScript engine serves editor and portal integrations. Android applications
should start with the [Kotlin walkthrough](android.md).

```sh
npm ci
npm test
node examples/node/render.mjs
```

In a consuming JavaScript project, install a locally packed artifact until the
package is published:

```sh
# In this checkout
npm pack --workspace @receiptscript/core --pack-destination artifacts
# In the consuming application
npm install /path/to/receiptscript-core-0.3.0.tgz
```

```js
import {ReceiptScript} from '@receiptscript/core';
import {HtmlRenderer} from '@receiptscript/core/html';

const template = new ReceiptScript().compile('receipt "sale"\nkv "TOTAL" "{{total | money:USD}}"');
const receipt = template.render({total: '25.25'});
const html = receipt.render(new HtmlRenderer());
```

Use decimal strings for precise monetary inputs. `templates` and `loadTemplate`
provide named sources and shared fragments. The public API rejects missing
bindings by default; `missingBindings: 'empty'` opts into permissive behavior.

`/html` renders escaped HTML and offline SVG codes. Images use a host resolver.
`/text` provides wrapped character-grid output and rejects media unless supplied
an explicit host callback. Neither JavaScript renderer ships printer transport.
The Kotlin optional ESC/POS driver is documented separately.
