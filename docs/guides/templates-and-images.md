# Templates and images

`ReceiptScript` asks two interfaces for content. By default both read from your app's assets.
Replace either when templates or images live somewhere else.

## Where templates come from

```kotlin
public fun interface ReceiptTemplateRepository {
    public suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String
}
```

`receipts.render("sale", data)` calls `getTemplate("sale", emptyMap())` and also loads any fragments
the template uses with `use header`/`use footer`.

### The default: app assets

`AssetTemplateRepository` reads `assets/receipts/<id>.rct`. Template IDs can include sub-folders,
such as `"stores/harare/sale"`. To use another folder or extension:

```kotlin
ReceiptScript(context, templates = AssetTemplateRepository(context, directory = "templates", extension = ".txt"))
```

### A database

```kotlin
class DatabaseTemplates(private val dao: TemplateDao) : ReceiptTemplateRepository {
    override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String =
        dao.source(templateId, args["merchantId"] as String?)
            ?: throw NoSuchElementException("No template '$templateId'")
}
```

### Several sources

Fall back from the database, to the server, to the templates bundled in the APK:

```kotlin
fun mixedTemplates(context: Context, dao: TemplateDao, api: Api): ReceiptTemplateRepository {
    val bundled = AssetTemplateRepository(context)
    return ReceiptTemplateRepository { id, args ->
        dao.source(id, args["merchantId"] as String?)
            ?: runCatching { api.template(id) }.getOrNull()
            ?: bundled.getTemplate(id, args)
    }
}
```

### Lookup arguments

The third parameter of `render` is optional and is passed to your repository unchanged. It is
never visible to the template. Use it for anything your lookup needs to decide, such as a merchant,
a locale, or a flag that picks between sources in a single repository:

```kotlin
val templates = ReceiptTemplateRepository { id, args ->
    if (args["source"] == "db") dao.source(id, null) ?: error("No template '$id'") else bundled.getTemplate(id)
}
receipts.render("sale", sale, args = mapOf("source" to "db", "merchantId" to "M001"))
```

The same `args` are used when loading that template's fragments. To call a repository yourself
without options, `repository.getTemplate(id)` passes an empty map.

### Templates you already have in memory

```kotlin
receipts.renderSource(source, data)
```

Fragments referenced by `source` still come from the repository.

### Repository contract

- Return the template source. Throw when a template does not exist.
- Do your own caching. ReceiptScript caches parsed templates by their text, so returning the same
  source again is cheap and returning changed source takes effect immediately.
- Exceptions and cancellation propagate to the caller of `render`.

## Shared headers and footers

Keep branding in one place by putting it in fragments:

```rct
receipt "sale"
use header "merchant"
use footer "customer"
kv "TOTAL" "{{total | money:currency}}"
```

`merchant.rct` and `customer.rct` contain plain statements, without a `receipt` line. They are loaded
through the same repository and bound to the same data.

## Where images come from

```kotlin
public fun interface ReceiptImageResolver {
    public suspend fun resolve(source: String): Bitmap?
}
```

Every `image "<source>"` in a template is passed to the resolver once per render, after bindings are
applied. Return null for references you do not recognise; rendering then fails with
`Unresolved image: <source>`.

### The default: app assets

`AssetImageResolver` handles `asset:` references, relative to the assets root:

```rct
image "asset:logos/shop.png" 96 64
```

loads `app/src/main/assets/logos/shop.png`. To resolve relative to a sub-folder, pass `directory`:

```kotlin
ReceiptScript(context, images = AssetImageResolver(context, directory = "receipts"))
```

Now the same line loads `assets/receipts/logos/shop.png`. The VS Code preview resolves images
relative to the template file, so with templates in `assets/receipts/`, `directory = "receipts"`
makes the app and the preview read the same file.

### Network and asset images together

```kotlin
fun images(context: Context, api: Api): ReceiptImageResolver {
    val assets = AssetImageResolver(context)
    val cache = HashMap<String, Bitmap>()
    return ReceiptImageResolver { source ->
        when {
            source.startsWith("https://") -> cache.getOrPut(source) {
                val bytes = api.bytes(source)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            else -> assets.resolve(source)
        }
    }
}
```

A template can then use `image "{{merchant.logoUrl}}" 120`.

### Image tips

- PNG logos with a transparent or white background print best. Colours are converted to black and white.
- Keep logos small. 384 dots is the full width of 58 mm paper.
- Keep returned bitmaps alive until `render` returns. ReceiptScript never recycles them.

The complete examples are in [`src/test/java/examples/Sources.kt`](../../src/test/java/examples/Sources.kt).
