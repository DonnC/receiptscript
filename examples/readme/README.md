# The README receipt

`sale.rct` is the receipt shown on the project front page. `sale.preview.json`
provides fictional sample data; `ReadmeExample.kt` uses the same values in typed
Kotlin objects and prints through the optional ESC/POS driver.

The binary release builder compiles this implementation against the assembled
library JARs. The README preview is rendered from the `.rct` and JSON files by
`npm run media`, so changes to the example can be reflected in the image.
