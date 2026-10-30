# ReceiptScript language

A document begins with `receipt "name"`. Indentation is two spaces; tabs are
invalid. Full lines beginning with `#` are comments. Trailing comments are not
supported. Unknown statements and filters fail validation.

## Metadata and typography

```rct
receipt "sale"
meta:
  width: 32
  paper: "58mm"
  font: 14
  fontScaleMode: "relative"
  copy: "customer"
  templateVersion: 1

center bold underline "SALE" 18
left small "Address"
kv big bold "TOTAL" "{{total | money:currency}}"
```

`width` is logical character width, not millimetres. `paper` is a preview hint,
not a printer configuration. Unknown metadata is retained as `extra`.

Font precedence is trailing numeric override, style token, base font, then the
renderer default. With `fontScaleMode: "relative"`, `small` is base minus 3,
`big` is base plus 3, and `middle` is the base. `fixed` pins style sizes to the
base but permits explicit trailing overrides. The renderer maps the resulting
font value to its platform's units; hardware may quantize it.

| Statement | Properties |
| --- | --- |
| `text`, `left`, `center`, `right` | Quoted content; `small`, `big`, `bold`, `underline`; optional trailing font number |
| `kv` | Two quoted templates; same typography modifiers |
| `divider` | Optional quoted repeat pattern, optional character length; default `-` and `meta.width` |
| `spacer` | Nonnegative line count |
| `row` | At least two quoted column templates |
| `table:` | Headers, weights, alignments, rows, conditional/looped rows |
| `image` | Source template, optional width then height |
| `qr` | Content template, optional size, default 300 |
| `barcode` | Content template, width default 300, height default 60, optional quoted format |
| `if path:` | Child blocks when the path is truthy |
| `for item in collection:` | Child blocks repeated for each collection entry |

`small`, `big` are source tokens; `middle` is the internal/default size and
does not need a source modifier. Image/code sizes are renderer units, typically
dots for thermal adapters. Positive dimensions are required by the public API.

## Tables and rows

```rct
table:
  headers: "ITEM" "QTY" "AMOUNT"
  weights: 3 1 2
  alignments: start center end
  for item in items:
    row "{{item.name}}" "{{item.qty}}" "{{item.total | money:currency}}"
```

The number of columns is unconstrained by the grammar; it must fit the target
layout. Headers, row cells, weights and alignments must agree on column count.
Weights must be positive. Omitted weights divide space equally; omitted
alignments center all cells. Only rows and `if`/`for` containers belong inside
a table. A standalone `row` is a simple line of columns; use a table for
multi-line content that needs wrapping and alignment.

## Data bindings and filters

```rct
left "Hello {{customer.name | upper}}"
kv "PHONE" "{{customer.phone | mask:0,3}}"
left "{{optional | default:Unknown}}"
```

Paths support nested properties, indexed collections in bindings, `@root`, and
loop locals. A loop exposes `@item`, `@index`, `@first`, `@last` and `@count`.
Conditions test truthiness of a path; they are not JavaScript expressions. In
particular `items && items.length > 0` is not valid ReceiptScript.

| Filter | Arguments / behavior |
| --- | --- |
| `upper`, `lower` | Text casing |
| `default:value` | Fallback for a false/empty value |
| `maskPan` | Card display mask; callers remain responsible for their masking policy |
| `mask:first,last,char` | Reveal edges and mask the remainder; no arguments masks all |
| `money:currency` | Two decimal places, half-up rounding; optional currency label/path |
| `date:output,input` | Default `dd/MM/yyyy`; optional format patterns |
| `datetime:output,input` | Default `dd/MM/yyyy HH:mm:ss`; optional format patterns |
| `padLeft:n,char`, `padRight:n,char`, `padCenter:n,char` | Pad to character length |

Arguments may be quoted literals, numbers, or context paths. Quotes nested in a
quoted statement must be escaped:

```rct
left "{{txn.date | datetime:\"dd MMM yyyy HH:mm\",\"yyyyMMddHHmmss\"}}"
```

Prefer ISO local date/time strings for portable sample data. Kotlin's Java time
implementation accepts more input types/patterns than the JavaScript subset.
Use tested patterns and supply local business time explicitly; do not depend on
the host timezone for settlement dates.

## Media

```rct
image "{{brand.logoRef}}" 88 88
qr "{{payment.url}}" 230
barcode "{{txn.reference}}" 300 60
barcode "5901234123457" 300 60 "ean13"
```

Supported barcode format IDs are `code128` (default), `code39`, `ean13`, `ean8`,
`upca`, and `itf`. Existing barcode syntax remains valid. The SVG encoder validates
content according to the chosen format. Native printer adapters must validate
against their SDK too; declaring a format does not guarantee hardware support.

There is no separate link primitive. Print a URL as text or encode it in QR.

## Header and footer

```rct
receipt "sale"
use header "merchant-default"
use footer "customer-default"

body:
  kv "INVOICE" "{{txn.reference}}"
```

Or define inline `header:`, `body:` and `footer:` sections. A shared fragment is
a source string of bare statements, without `receipt`, `meta` or nested section
declarations. The host resolves fragment IDs from the same catalog as templates.

Order is shared headers, inline header, body, two blank lines, inline footer,
shared footers. Footer declarations can occur anywhere in source and still render
at the end. The gap is omitted when no footer nodes are defined. Multiple shared
references preserve their declared order; inline and shared content may coexist.
