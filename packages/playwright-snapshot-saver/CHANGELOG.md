# Changelog

## 0.7.0

### ⚠️ Breaking changes

- Bundles now emit the **v2 layout**: `resources/` directory,
  `<sha1>.css` stylesheet sidecars referenced by `<link>` tags, and
  `manifest.json` with `"version": 2`. Older versions of the Page
  Mirror IntelliJ plugin (< 0.5.0) do **not** load v2 bundles — they'll
  silently skip them. Upgrade both halves of the stack together, or
  pin `playwright-snapshot-saver@^0.6` until you're ready to upgrade
  the plugin. See
  [`docs/migration-v1-to-v2.md`](https://github.com/AnewTranduil/PageObjectPlugin/blob/main/docs/migration-v1-to-v2.md)
  for the migration guide.
- `manifest.json` `version` is now a **fixed schema version** (`2`),
  not an incrementing write counter. Tools that previously read
  `version` to detect bundle updates should key off `timestamp` instead.
- `screenshot.<ext>` moved from the bundle root to
  `resources/screenshot.<ext>`.
- CSS is no longer inlined as `<style>` inside `index.html`. The plugin
  inlines sidecars on read since `<iframe srcdoc>` cannot resolve
  relative URLs.

### Added

- `@pagemirror/snapshot-core` extracted as the framework-agnostic shared
  engine; this package is now a thin Playwright adapter on top of it.
  Selenium / Cypress / Appium adapters are on the roadmap and will reuse
  the same on-disk format.
- Trace-extracted bundles are fully self-contained — every `<link>`,
  `<img>`, CSS `url(...)`, `@font-face`, and SVG `<use>` reference
  points at a real file under `resources/`, and the original `<base>`
  element is stripped.

## 0.6.0

### Breaking Changes

- **Screenshot extraction disabled by default.** `extractSnapshots()` and the reporter no longer generate `screenshot.webp` from trace screencast frames unless explicitly opted in. Screencast frames are low-fidelity and often blank, providing no value.
  - To re-enable: pass `screenshot: true` in `ExtractOptions`, or `--screenshot` on the CLI.
  - `saveSnapshot()` (direct API) is **not affected** -- it still captures real `page.screenshot()` by default.

### Changed

- CLI: added `--screenshot` flag to opt in to screenshot extraction. `--no-screenshot` is still accepted for backwards compatibility.
- `ExtractOptions.screenshot` default changed from `true` to `false`.

### Removed

- Deleted blank `screenshot.webp` files from `test-project/` snapshots.
