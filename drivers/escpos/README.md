# ReceiptScript optional Kotlin drivers

`receiptscript-escpos` provides native-text and raster output for ESC/POS-compatible
portable and desktop receipt printers. It is distributed as an optional JAR;
the template engine remains independent of the driver.

```kotlin
val printer = ReceiptPrinter(
    EscPosDriver(EscPosProfile.Portable58),
    OutputStreamConnection(connectedSocket.outputStream)
)
printer.print(receipt)
```

The module provides `EscPosDriver`, editable 58/80 mm profiles, `PlainTextDriver`,
`MonochromeImage`, `PrinterConnection`, `OutputStreamConnection`,
`TcpPrinterConnection`, and `ReceiptPrinter`. Codes are encoded locally with
ZXing and sent as raster strips. Android permissions and connection discovery
belong to the application.

[Binary distribution](../../docs/downloads.md), [Android integration](../../docs/android.md)
and [driver reference](../../docs/drivers.md). Presets are protocol assumptions;
no printer model is hardware-certified by this checkout.
