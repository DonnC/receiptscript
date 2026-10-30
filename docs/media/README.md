# Project media

| Asset | Purpose |
| --- | --- |
| `banner.png` | Project identity and a conceptual portable-printer illustration |
| `logo.svg` | Scalable receipt/code mark |
| `receipt-example.png` | Real engine output beside the README template |

`receipt-example.png` is rebuilt by `npm run media` from
`examples/readme/sale.rct` and `sale.preview.json`. It uses the JavaScript engine
and HTML renderer, then captures the result in an installed Chromium browser.
`RECEIPTSCRIPT_BROWSER` can select Chrome, Edge or Chromium explicitly. The image
is a preview, not a photograph of a hardware acceptance test.
