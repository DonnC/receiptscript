# Android: from template to printer

This walkthrough uses the shipped ESC/POS driver. If your integrated POS exposes
a vendor SDK instead, render the same receipt and use [your own driver](adapters.md).

## Install

The core artifact is `io.github.donnclab.receiptscript:receiptscript-core:0.3.0`.
The optional driver artifact is `io.github.donnclab.receiptscript:receiptscript-escpos:0.3.0`.
It transitively includes the core. Neither artifact has been published publicly yet.

Build with JDK 17 in this checkout:

```sh
./gradlew :kotlin:publishToMavenLocal :drivers:escpos:publishToMavenLocal
```

On Windows use `gradlew.bat`. The extraction machine has a documented Gradle
loopback issue; [verification notes](verification.md) distinguish source tests
from Gradle publication checks. In your Android project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

In the app module:

```kotlin
dependencies {
    implementation("io.github.donnclab.receiptscript:receiptscript-escpos:0.3.0")
}
```

Use a compiler compatible with Kotlin 2.3 metadata. The artifacts target JVM 11.
If your app supports Android below API 26, configure
[Java time desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring).

## Create a real receipt

Copy [`sale.rct`](../examples/android/assets/receipt_templates/sale.rct) into
`app/src/main/assets/receipt_templates/sale.rct`. It includes a weighted item
table, total, QR and barcode. The companion preview JSON uses the same shape
as these Kotlin classes:

```kotlin
import java.math.BigDecimal

data class Merchant(val name: String)
data class Sale(val reference: String, val date: String, val currency: String, val total: BigDecimal)
data class SaleItem(val name: String, val quantity: Int, val total: BigDecimal)
data class SaleData(val merchant: Merchant, val sale: Sale, val items: List<SaleItem>)
```

The complete data construction and byte-driver examples are in
[`SaleExample.kt`](../examples/kotlin-drivers/SaleExample.kt), which is compiled
with the driver tests. Use `BigDecimal` for monetary inputs. ReceiptScript formats
the supplied values; your application calculates the sale.

## Load, bind and print

```kotlin
import android.content.Context
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.ReceiptScript
import zw.co.donnclab.rct.escpos.*

suspend fun printSale(context: Context, output: OutputStream, data: SaleData) =
    withContext(Dispatchers.IO) {
        val source = context.assets.open("receipt_templates/sale.rct")
            .bufferedReader().use { it.readText() }
        val template = ReceiptScript().compile(source)
        val receipt = template.render(data)
        val printer = ReceiptPrinter(
            EscPosDriver(EscPosProfile.Portable58),
            OutputStreamConnection(output)
        )
        printer.print(receipt)
    }
```

For Bluetooth Classic, pass a connected `BluetoothSocket.outputStream`.
[`ConnectedPrinterExample.kt`](../examples/android/ConnectedPrinterExample.kt)
contains the Android function. Your app owns permissions, pairing, connecting and
closing the socket. For BLE, use the connection library's characteristic-write
queue through `PrinterConnection`; BLE is not an `OutputStream` transport.

Keep a compiled template and one connection/printer instance in your application's
print service for repeated jobs. Share the connection instance to serialize jobs.
Loading assets on every print is unnecessary. Replace cached compiled templates
when your synced template revision changes.

Exceptions propagate. Rendering finishes before transmission begins. A later
connection error can still leave a partially printed job, so do not automatically
retry it. Returning from `print` confirms delivery to the connection API, not a
printer sensor acknowledgement. Closing a socket belongs to the host; coroutine
cancellation alone cannot reliably interrupt every blocking stream implementation.

## Connecting a paired Bluetooth Classic printer

[`PairedBluetoothExample.kt`](../examples/android/PairedBluetoothExample.kt) opens
the standard SPP service on a device already paired and selected by your app,
connects, sends the receipt and closes its owned socket. It uses the shipped
driver/stream connection and runs on `Dispatchers.IO`. It does not discover,
silently pair devices or fall back to insecure sockets. A device using a custom
service UUID or BLE needs its documented connection method instead.

For this paired-device example, declare:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

On Android 12 and later, request `BLUETOOTH_CONNECT` at runtime before selecting
or connecting to a paired device. The helper checks permission and throws if it
was not granted. Device discovery and BLE scanning have additional permissions
and are outside this example. For TCP printing add `INTERNET` to the manifest.
See the [Android Bluetooth permission guide](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
for your application's target SDK requirements.

## Source strings and a catalog

```kotlin
val template = ReceiptScript().compile(sourceFromDatabase)
val receipt = template.render(data)
```

For named receipts and shared header/footer fragments:

```kotlin
val engine = ReceiptScript(loadTemplate = { id -> localSources[id] })
val template = engine.template("sale")
val receipt = template.render(data)
```

`loadTemplate` returns source or null and is synchronous. Load remote templates
before printing. Fragment sources are bare statements. The engine handles ordering
and the default footer gap; neither the driver nor the connection resolves fragments.

## Logos in Android assets or resources

```rct
image "{{brand.logoRef}}" 88 88
```

The engine leaves `logoRef` as a source reference. Resolve it in the driver:

```kotlin
val driver = EscPosDriver(images = { reference ->
    context.assets.open(reference.removePrefix("asset:")).use { stream ->
        android.graphics.BitmapFactory.decodeStream(stream)?.let { bitmap ->
            try { bitmap.toReceiptImage() } finally { bitmap.recycle() }
        }
    }
})
```

Import the extension from [`BitmapImages.kt`](../examples/android/BitmapImages.kt).
`toReceiptImage` converts Android ARGB pixels to the driver's platform-neutral
image. The driver fits the image into the requested box and printable width and
sends bounded raster strips. It does not decode Android assets or fetch URLs.
For a client-owned drawable, resolve the reference using that module's resource
ID and `BitmapFactory.decodeResource` instead. Bound context need not contain IDs.

Decode large images with Android sampling before conversion. The optional driver
limits source images to 16,777,216 pixels. Thresholding suits crisp logos; it is
not a photo dithering pipeline. PNG is a practical source format, and runtime
Bitmap use does not require BMP files.

## DTO names and minification

The JVM engine reads Kotlin properties by name. Put receipt DTOs in a dedicated
package and preserve those members in the application's R8 configuration:

```proguard
-keep class com.example.app.receipts.model.** { *; }
```

Replace that package with your DTO package, rather than preserving your whole app.
Alternatively use maps with explicit field keys and no reflection keep rule.

Missing bound data is an error by default. Wrap optional content in `if`, use a
`default` filter, or choose `strictBindings = false` for legacy empty-value behavior.

## Preview

Open the same `.rct` in VS Code with a companion `.preview.json` reflecting the
DTO fields. A screen preview in Android should consume the rendered `Receipt`.
The core provides the resolved model; the host supplies its Compose/View renderer.
Printer-native font sizes are discrete, so a browser preview is not a device
calibration tool. See [font mapping](drivers.md#fonts-and-columns).
