# Template language reference

ReceiptScript templates (`.rct` files) describe a receipt line by line. This page lists everything
the parser accepts. For a gentle introduction, see [Getting started](getting-started.md).

- [File structure](#file-structure)
- [Text](#text) · [Key/value](#keyvalue) · [Dividers and space](#dividers-and-space) · [Rows and tables](#rows-and-tables)
- [Images, QR codes and barcodes](#images-qr-codes-and-barcodes)
- [Conditions and loops](#conditions-and-loops)
- [Sections and shared fragments](#sections-and-shared-fragments)
- [Metadata](#metadata)
- [Bindings](#bindings) · [Filters](#filters)
- [Errors](#errors)

## File structure

```rct
receipt "sale"

# A comment. Comments take a whole line.
center bold "{{merchant.name}}"
kv "TOTAL" "{{total | money:currency}}"
```

- The first statement must be `receipt "<name>"`. The name is available as `Receipt.name`.
- One statement per line. Nested blocks are indented by **two spaces**; tabs are not accepted.
- `#` starts a comment only at the beginning of a line (after indentation).
- Text is always in double quotes. Escape a quote inside text as `\"`.

## Text

```rct
left "Left aligned"
center "Centred"
right "Right aligned"
text "Same as left"
center big bold "LARGE AND BOLD"
left small underline "Small print"
center "Exact size" 30
```

| Part | Values |
| --- | --- |
| alignment | `left`, `center`, `right`, `text` (left) |
| modifiers (any order, before the text) | `bold`, `underline`, `small`, `big` |
| size (optional, after the text) | a number in printer dots, such as `18` or `22.5`. Overrides `small`/`big`. |

Long text wraps to the paper width. Text may contain line breaks from bindings.

## Key/value

```rct
kv "REFERENCE" "{{reference}}"
kv bold "TOTAL" "{{total | money:currency}}"
kv small "VAT" "{{vat}}" 18
```

A label on the left and a value on the right. Takes the same modifiers and optional size as text.
If both do not fit on one line, the value moves to the next line, right-aligned.

## Dividers and space

```rct
divider
divider "="
divider "-" 20
divider "_"
spacer 2
```

- `divider` repeats its pattern (default `-`) across the receipt. The optional number sets the
  length in characters; the default is `meta.width`, or 34.
- `divider "_"` draws a solid line on raster output.
- `spacer <lines>` adds blank lines. The count is required.

## Rows and tables

```rct
row "ITEM" "QTY" "TOTAL"

table:
  headers: "ITEM" "QTY" "TOTAL"
  weights: 3 1 2
  alignments: start center end
  for item in items:
    row "{{item.name}}" "{{item.quantity}}" "{{item.total}}"
  if discount:
    row "Discount" "" "-{{discount}}"
```

- `row` on its own prints equal-width, centred columns.
- Inside `table:`:
  - `headers:` (optional) sets the header cells; headers print bold.
  - `weights:` (optional) sets relative column widths, so `3 1 2` gives the first column half the line.
  - `alignments:` (optional) takes one of `start`/`left`, `center`, `end`/`right` per column.
  - The body may contain only `row`, `for` and `if`.
- Every row must have the same number of cells as the headers. The `weights:` and `alignments:`
  lists must match that count too.
- Table text uses the small font.

## Images, QR codes and barcodes

```rct
image "asset:logos/shop.png" 96 64
image "{{merchant.logo}}" 120
qr "{{payment.link}}" 240
barcode "{{reference}}" 300 60
barcode "5901234123457" 300 80 "ean13"
```

| Statement | Arguments |
| --- | --- |
| `image "<ref>" [width] [height]` | `ref` is passed to your `ReceiptImageResolver`. Sizes are a bounding box in dots; the aspect ratio is kept, and images never exceed the paper width. |
| `qr "<content>" [size]` | square size in dots, default `300` |
| `barcode "<content>" [width] [height] ["format"]` | default `300` × `60`; formats `code128` (default), `code39`, `ean13`, `ean8`, `upca`, `itf` |

Codes include their quiet zones. If a code cannot fit the paper without losing modules, rendering fails
rather than printing an unscannable code.

## Conditions and loops

```rct
if customer.loyaltyNumber:
  kv "LOYALTY" "{{customer.loyaltyNumber}}"

for item in items:
  kv "{{@index}}. {{item.name}}" "{{item.total}}"
```

- `if <path>:` renders its block when the value is *truthy*. A value is falsy when it is null,
  `false`, a blank string, an empty collection or map, or the number zero. There is no `else`; use a
  second `if` on a different field.
- `for <name> in <path>:` repeats its block for each element of a list, array or other `Iterable`.
  A null collection renders nothing.
- Inside a loop, bindings can also use `@index` (from 0), `@first`, `@last`, `@count` and `@item`.
  These work in `{{…}}` bindings but not as `if` conditions.

## Sections and shared fragments

```rct
receipt "sale"
use header "merchant"
use footer "customer"

header:
  center small "Branch 12"
body:
  kv "TOTAL" "{{total}}"
footer:
  center "Thank you!"
```

- Statements outside a section belong to the body.
- `header:`, `body:` and `footer:` each appear at most once.
- `use header "<id>"` and `use footer "<id>"` load fragments through your `ReceiptTemplateRepository`.
  A fragment file contains plain statements, without a `receipt` line.
- Print order: `use header` fragments, `header:`, body, `footer:`, then `use footer` fragments.
  Two blank lines separate the body from the footer.

## Metadata

```rct
meta:
  width: 32
  font: 22
  fontScaleMode: "fixed"
  paper: "58mm"
```

| Key | Meaning |
| --- | --- |
| `width` | characters per line, used for dividers and native ESC/POS text |
| `font` | base font size in dots for raster output |
| `fontScaleMode` | `relative` (default): `small`/`big` are 3 dots smaller/larger than `font`. `fixed`: they stay at `font`. |
| `paper`, `copy`, `templateVersion` | informational; available to your code as `receipt.meta` |
| any other key | kept in `receipt.meta.extra` for your driver |

`meta` does not change the pixel width of the receipt; that is `ReceiptOptions.width` in your app.

## Bindings

`{{path}}` inserts a value from the data you pass to `render`. Data can be a `Map`, a Kotlin or Java
object (properties and getters are read by name), a list, or a combination.

| Syntax | Reads |
| --- | --- |
| `{{merchant}}` | a top-level value |
| `{{merchant.name}}` | a nested value |
| `{{items[0].name}}` | a list element |
| `{{item.total}}` | a loop variable |

By default a binding that resolves to null fails the render with `Missing binding: <path>`, so
missing data is caught before anything prints. Add a `default` filter to make a value optional.

> If your app uses R8 or ProGuard, keep the property names of classes you pass as receipt data, for
> example with `-keepclassmembers class com.example.receipts.** { *; }`.

## Filters

Filters transform a value: `{{value | filter}}`, or with arguments `{{value | filter:arg1,arg2}}`.
They chain from left to right: `{{name | upper | padRight:12}}`.

An argument is a number, a quoted string such as `"USD"`, or a binding path that is looked up in your
data. Quote any argument that contains symbols, such as `"#"` or `"HH:mm"`. Inside a template string,
write those quotes as `\"`.

| Filter | Arguments | Example | Result |
| --- | --- | --- | --- |
| `upper` | | `{{code \| upper}}` | `ABC12` |
| `lower` | | `{{code \| lower}}` | `abc12` |
| `default` | fallback | `{{note \| default:\"N/A\"}}` | `N/A` when `note` is falsy |
| `money` | currency code or path | `{{total \| money:currency}}` | `USD 10.00` |
| `date` | output pattern, input pattern | `{{paidAt \| date}}` | `30/10/2026` |
| `datetime` | output pattern, input pattern | `{{paidAt \| datetime:\"HH:mm\"}}` | `10:15` |
| `maskPan` | | `{{pan \| maskPan}}` | `450612******7890` |
| `mask` | show first, show last, mask character | `{{phone \| mask:2,2,\"#\"}}` | `07######21` |
| `padLeft` | width, pad character | `{{qty \| padLeft:4,\"0\"}}` | `0007` |
| `padRight` | width, pad character | `{{qty \| padRight:4}}` | `7   ` |
| `padCenter` | width, pad character | `{{tag \| padCenter:6,\"-\"}}` | `--ab--` |

Notes:

- **money** rounds half-up to two decimals and puts the code first. Pass amounts as `BigDecimal` or
  decimal strings: a `Double` cannot represent most cent values exactly.
- **date / datetime** accept `java.time` values, ISO-8601 strings, or strings in the input pattern you
  give. Patterns use [`DateTimeFormatter`](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html)
  syntax. Defaults: `dd/MM/yyyy` and `dd/MM/yyyy HH:mm:ss`.
- **maskPan** keeps the first 6 and last 4 digits of a card number of 10 or more digits.
  **mask** with no arguments hides every character.
- Never put PINs, CVVs, passwords or card track data on a receipt, masked or not.

## Errors

Templates are checked before anything prints:

| Message | Fix |
| --- | --- |
| `Receipt declaration is required.` | start the file with `receipt "<name>"` |
| `Unsupported statement at line N` | check spelling, quoting and that comments are on their own line |
| `Expected indented block after line N` | indent the block under `if`, `for`, `table:` or a section by two spaces |
| `Unknown filter: x` | use a filter from the table above |
| `Missing binding: path` | supply the value, or add `\| default:…` |
| `Missing fragment: id` | make the repository return the fragment for `use header`/`use footer` |
| `Table columns must match` | give every row the same number of cells as the headers, weights and alignments |

The VS Code extension shows these errors as you type.
