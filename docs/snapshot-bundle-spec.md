# Snapshot Bundle Spec (v2)

> **Status:** Source of truth for the on-disk `.snapshots/<name>/` bundle
> format consumed by the PageObjectPlugin tool window and produced by all
> language-specific snapshot savers (TS / Python / JVM / Selenium /
> Cypress / Appium).
>
> Any change to the format MUST be accompanied by a version bump in
> `manifest.json.version` and a migration note below.

---

## Directory Layout

```
<snapshot-name>/
  index.html                 # REQUIRED — sanitized DOM referencing resources/
  manifest.json              # REQUIRED — schema version 2
  resources/                 # Everything index.html references by relative path
    screenshot.png|webp      # Visual reference (optional)
    <sha1>.css               # Stylesheet sidecars
    <sha1>.woff2|png|jpg|... # Fonts, images, media, SVG sprites, etc.
                             # (trace-extracted bundles, Task 15.5)
```

The directory name is the snapshot identifier surfaced in the tool
window dropdown.

## `index.html` requirements

- **Self-contained relative to the bundle directory.** Every `<link>`,
  `<img>`, `<script src>`, and CSS `url(...)` reference points either
  (a) at a sidecar under `resources/` (resolved at load time by the
  plugin) or (b) at a valid absolute URL that does not need resolution.
- **No `<base>` element.** Trace-extracted bundles strip the original
  `<base href>` after resolving URLs against it. A surviving `<base>`
  would redirect every `resources/...` reference to the original origin
  and break the bundle when opened outside its origin.
- Scripts are NOT stripped by the saver or the plugin — any `<script>`
  the captured page carried is preserved verbatim in `index.html`. The
  plugin renders the HTML in a `srcdoc` iframe whose sandbox is
  `sandbox="allow-same-origin allow-scripts"` (see
  `html/js/snapshot.js`), so snapshot-borne scripts CAN execute inside
  that iframe. Treat snapshot HTML as untrusted; do not assume it is
  script-free.
- UTF-8 encoded.
- Preserves original element attributes so locators (`data-testid`,
  `role`, `aria-*`, `id`, `name`, classes) resolve identically to the
  live page.
- **Important rendering note:** the plugin loads snapshot HTML via
  `iframe.srcdoc`. `srcdoc` iframes have base URL `about:srcdoc`, which
  cannot resolve relative filesystem paths, so the plugin inlines
  sidecar CSS into `<style>` blocks on the Kotlin side before JCEF
  sees the HTML. Producers do NOT need to pre-inline CSS — shipping
  sidecars is fine.

## `resources/` directory

- **One flat level.** No nested subdirectories under `resources/`.
  Filenames are all that's referenced from `index.html`.
- **CSS sidecars** are content-addressed by the `sha1` of the
  stylesheet source so identical stylesheets across snapshots
  de-duplicate naturally. The live saver (`@pagemirror/snapshot-core`'s
  `assemble-html`) uses a 16-hex-char prefix of that sha1; trace-extracted
  bundles reuse Playwright's native full-length sha1 filename. Consumers
  must treat the filename as an opaque identifier, not a fixed-width hash.
- **Screenshots** are named `screenshot.png` or `screenshot.webp`.
  Only one screenshot per bundle is expected.
- **Path traversal is rejected.** The plugin refuses to load any
  resource whose resolved path escapes the bundle directory.

## `manifest.json` schema (v2)

```json
{
  "version": 2,
  "viewport": { "width": 1280, "height": 720 },
  "timestamp": "2025-01-15T10:30:00Z",
  "playwright": "1.58.0",
  "url": "https://example.com/login",
  "userAgent": "Mozilla/5.0 ..."
}
```

Fields:
- `version` (int, required) — **schema** version. Currently `2`. This
  is NOT a monotonic write counter (v1 briefly used it that way under
  Task 11; v2 restores the schema-only semantics).
- `viewport` (object, required) — `{width, height}` in CSS pixels.
  Mobile adapters (Task 20) MAY add `{platform, deviceName}`.
- `timestamp` (string, required) — ISO-8601 UTC.
- `url` (string, optional) — the page URL at capture time. Live-capture
  bundles populate it from `page.url()`. Trace-extracted bundles may
  omit the field entirely (or carry the empty string) when the source
  trace did not record a URL — `snapshot-core/src/trace/extract.ts`
  passes the URL straight through `buildTraceManifest`, and
  `JSON.stringify` drops it if undefined.
- `userAgent` (string, optional).
- Driver version — exactly one of `playwright` / `selenium` / `cypress`
  / `appium` SHOULD be present, matching the driver that produced the
  snapshot.

## Versioning & Compatibility

- The plugin reads `manifest.version` and **refuses to load** any
  bundle whose version is neither `2` nor absent. An absent or
  unparseable `version` field is treated permissively — the bundle
  still loads, just without driver metadata.
- Breaking changes to the layout or required fields bump the integer.
  Additive changes (new optional fields) do NOT bump the version.

## Migration v1 → v2

Differences:

| Aspect | v1 | v2 |
|---|---|---|
| Screenshot location | `<bundle>/screenshot.<ext>` (top-level) | `<bundle>/resources/screenshot.<ext>` |
| CSS | Inlined as `<style>` inside `index.html` by the saver | Written as `resources/<sha1>.css` sidecars referenced by `<link>`; plugin inlines them on read |
| `manifest.version` | Sometimes a schema version, sometimes a write counter (Task 11 overloaded it) | Strictly the schema version — always `2` |
| Top-level files | `index.html`, `screenshot.*`, `manifest.json` | `index.html`, `manifest.json`, `resources/` |

To migrate an existing `.snapshots/` directory:

1. Re-run your snapshot saver. The TypeScript saver
   (`@pagemirror/snapshot-core` + `playwright-snapshot-saver`)
   produces v2 bundles out of the box after Task 15. Trace extraction
   via `extractSnapshots` also emits v2.
2. If you need to hand-edit a v1 bundle (e.g. a fixture for unit
   tests): bump `manifest.version` to `2`, create a `resources/`
   directory, move any top-level `screenshot.*` into it, and bundle
   any external stylesheets as `resources/<sha1>.css`. CSS that was
   already inlined in `<style>` tags stays as-is.

## Language Saver Contract

All language savers (`packages/playwright-snapshot-saver`, future
`packages/playwright-snapshot-saver-python`, future
`packages/playwright-snapshot-saver-jvm`, and every future adapter)
MUST emit bundles conforming to this spec. Any divergence is a bug.

The framework-agnostic core lives in `packages/snapshot-core` and
provides `saveSnapshot(adapter, options)` + the `PageAdapter` interface
— see `docs/tasks/task-15-snapshot-core-extraction.md`.
