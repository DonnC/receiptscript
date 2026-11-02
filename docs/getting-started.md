# Getting started

This page takes you from an empty app to a printed receipt. It assumes a Jetpack Compose app.

## 1. Add the library

Download `receiptscript-<version>.aar` from [Releases](https://github.com/DonnC/receiptscript/releases)
and copy it to `app/libs/`. Then add it and its dependencies to `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/receiptscript-0.7.0.aar"))
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.3")
}
```

A local AAR does not carry its dependencies, which is why they are listed here.

## 2. Add a template

Create `app/src/main/assets/receipts/hello.rct`:

```rct
receipt "hello"

center bold "{{shop}}"
divider
kv "TOTAL" "{{total | money:\"USD\"}}"
footer:
  center small "Thank you!"
```

The ID of this template is `hello`: the default repository reads `assets/receipts/<id>.rct`.

## 3. Render and preview

```kotlin
data class Hello(val shop: String, val total: String)

@Composable
fun HelloReceipt() {
    val context = LocalContext.current
    val receipts = remember { ReceiptScript(context) }
    val receipt by produceState<Receipt?>(null) {
        value = receipts.render("hello", Hello(shop = "Coffee & Code", total = "4.50"))
    }
    receipt?.let { ReceiptPreview(it, Modifier.width(280.dp)) }
}
```

`render` is a suspend function and does its work off the main thread. It never prints.

## 4. Print

Pass a driver when you create `ReceiptScript`. For an ESC/POS Bluetooth printer that the user
has already paired:

```kotlin
val receipts = ReceiptScript(context, EscPosDriver(BluetoothPrinterConnection(device)))
receipts.print(receipt)
```

On Android 12 and newer, your app must hold the `BLUETOOTH_CONNECT` permission first. For other
printers, see [Writing a driver](guides/drivers.md) or [Printing](guides/printing.md).

## 5. Handle errors

`render` and `print` throw on failure. Typical causes:

| Error | Cause |
| --- | --- |
| `RctParseException` | the template has a syntax error; the message gives the line |
| `IllegalArgumentException: Missing binding: …` | the data has no value for a `{{binding}}`; add a `default` filter if it is optional |
| `IllegalArgumentException: Unresolved image: …` | the image resolver returned null for a reference |
| `IllegalStateException` from `print` | no driver was passed to `ReceiptScript` |
| anything from your driver | printer faults such as paper out; ReceiptScript never retries a print |

## Next steps

- Learn the template syntax in the [language reference](language-reference.md).
- Load templates from your database or server: [Templates and images](guides/templates-and-images.md).
- Design templates with a live preview in [VS Code](guides/vscode.md).
