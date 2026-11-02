# ReceiptScript documentation

Start with **Getting started** if you are new. The guides cover one task each and can be read in any order.

| Page | Read it when you want to… |
| --- | --- |
| [Getting started](getting-started.md) | print your first receipt |
| [Template language reference](language-reference.md) | look up a statement, binding or filter |
| [Writing a driver](guides/drivers.md) | connect a vendor printer SDK |
| [Templates and images](guides/templates-and-images.md) | load templates from a database or server, or use logos |
| [Printing](guides/printing.md) | print over Bluetooth, USB, TCP or serial with ESC/POS |
| [Preview and export](guides/preview-and-export.md) | show receipts in Compose, or save them as PNG or PDF |
| [Designing in VS Code](guides/vscode.md) | edit templates with a live preview |
| [Using the JVM engine](guides/jvm.md) | render on a server or in plain Kotlin tests |
| [Architecture](architecture.md) | understand how the library is built and why |
| [Migrating to 0.7](migration/0.7.md) | upgrade from 0.6 |

The driver, template, printing and JVM examples in the guides are copied from compiled code in
[`src/test/java/examples`](../src/test/java/examples) and [`src/test/kotlin/examples`](../src/test/kotlin/examples),
so they are built and checked along with the library.
