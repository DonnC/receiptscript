# ReceiptScript Studio

Studio is an optional Android Compose module for editing one receipt template. It adds no
template database, list screen, account system or printer setup. The host owns those concerns.
The core library remains usable without Studio and has no dependency on it.

## Embed

Inside this repository, add `implementation(project(":studio"))`. Studio depends on the core
library. This development module has not yet been published as a release artifact.

```kotlin
val engine = remember { ReceiptScript(context, driver, repository, imageResolver) }
val state = rememberReceiptStudioState(templateSource, sampleJson)

ReceiptStudio(
    state = state,
    receiptScript = engine,
    onSave = { draft -> saveTemplate(draft) },
    onPrint = { receipt -> engine.print(receipt) },
    onExport = { receipt -> share(receipt.toPdf()) }
)
```

`saveTemplate` and `share` above are application callbacks, not library repository methods. They may
suspend; perform blocking file, database and export work on the appropriate dispatcher.
Omit output callbacks when the host has no output capability. `onPickImage` optionally returns
a reference that the configured image resolver understands; Studio never downloads an image.
`args` is passed through to core for shared-template lookups.

## Workspace

**Design** groups compact block rows into Header, Body and Footer in core print order. Receipt
names and metadata are in the Studio options menu rather than taking up outline space. Tables
and other nested blocks expand on demand. Tap a row to open its properties and choose **Done**
to apply the edit. Text properties use a single-row editor toolbar for alignment, size, bold
and underline. Key/value blocks omit alignment because core positions their label and value.
The size menu offers Small, Normal, Large and Custom dots, using the existing template syntax;
choosing a preset clears an explicit font override. Property sheets have a close button, not an
actions menu. Tables retain their
headings, per-column width/alignment controls, rows, conditions and loops as editable child statements.
Switches select default equal widths and centred alignment; advanced numeric configuration is not
shown until those defaults are turned off. Header
and footer references remain references. Core, not their position in the outline, determines
the final header/body/footer print order and footer spacing.

Simple bindings have a field picker based on the current JSON and native formatter controls,
including currency for money and general masking (visible prefix/suffix and mask character).
Live field samples are evaluated by core; invalid edits do not show a stale unmasked sample.
Images, QR codes and barcodes support bindings without text formatter/style controls. Image
previews use the host engine and resolver; selecting another image requires `onPickImage`.
The example demonstrates Android's document picker and persists access to selected image URIs.
Composite text and advanced filter chains remain editable as
expressions and are never silently reduced to a simple binding. Core parses and writes bindings.

The Add block sheet provides a block grid and section/container selector. Each block has
duplicate/delete icons; deletion asks for confirmation. Drag its handle to reorder siblings,
with live placement and edge scrolling.
Core validates each operation before committing it. Deleting the only child of a required
condition or loop is rejected; deleting the last local section block clears that optional section.
Each section has independent Add and Edit actions. Header/footer references can be configured
there or explicitly copied into local editable blocks, without changing the shared repository template.
Bindable fields share a searchable data-field bottom sheet, including currency, array paths and
the selected block's loop aliases. Fields are refreshed from the current sample JSON.
**Studio options > Template settings** exposes core metadata, including base font, font scaling
and character columns; blank values inherit core defaults. Use **Studio options > Template source** for comments and
complete `.rct` editing.

**Data** is a monospace JSON editor with syntax colouring, line numbers, native selection,
horizontal/vertical scrolling and a formatting action. It accepts strict JSON objects, not
JavaScript literals, comments or trailing commas. Invalid drafts remain editable. JSON
numbers are converted to `Long` or `BigDecimal`, never silently rounded to `Double`.
Sample data is bounded to 262,144 characters and 64 nesting levels. There is no schema inference
or static binding type checker: actual binding and rendering errors come from core.

**Preview** displays the core `Receipt` with `ReceiptPreview`. Studio does not draw a second
receipt layout. It updates after a brief debounce, offers a bottom zoom slider and icon output actions, and forwards that same receipt
to the supplied print/export callback. An old preview is visibly out of date and cannot be
printed or exported as the current draft. Driver failures are surfaced without automatic retries.

Narrow windows use tabs and a properties sheet. Wider windows show design and preview together;
sufficiently wide windows also show an inspector. UI colours, typography and shapes come from
the enclosing `MaterialTheme`; receipt typography remains controlled by the engine/template.

## State and persistence

`ReceiptStudioState` is hoistable. Read `draft` and `isDirty` to implement navigation or host
autosave. `editSource`, `editData`, `undo` and `redo` are also available. Both source and sample
data participate in undo/redo; history is bounded to 100 revisions and is memory-only.
The remember helper restores the current draft and saved baseline after configuration changes.
Use host persistence for durable drafts or large documents.
For a new unsaved template, pass `initiallySaved = false` to the state constructor or remember
helper. This enables Save before the first edit. Create a new state per document; initial
arguments do not replace an existing editor session.

`onSave` marks only the submitted snapshot saved, and only after the callback returns
successfully. Edits made during saving stay dirty. A valid template can be saved alongside
unfinished sample JSON, but output requires a successful render of both current revisions.
Save failures leave the draft intact. Saving is explicit; Studio does not own a storage format.

The Back button asks before leaving a dirty draft. The host owns system navigation and should
apply the same `state.isDirty` guard; the example demonstrates `BackHandler`.

## Source preservation

The public core tooling APIs `RctSourceDocument` and `RctStatementForm` support Studio.
They edit original source rather than reverse-engineering resolved `Receipt.blocks`.
Untouched source, comments, bindings and line endings are retained. Node handles belong to one
document revision; using a stale handle is rejected. Invalid source stays available in the
source editor and does not get silently normalised into a different template.

## Try it

Run the existing `example` app, open any sample and select **Edit**. The app saves a private
draft per sample and reopens it on subsequent visits. Export uses Android's PDF destination
picker. Printing appears only when the example has a connected driver. Android Studio layout
previews are provided for phone and tablet in `SampleStudio.kt`.

```shell
./gradlew :testDebugUnitTest :studio:testDebugUnitTest :example:assembleDebug
./gradlew :studio:connectedDebugAndroidTest
```
