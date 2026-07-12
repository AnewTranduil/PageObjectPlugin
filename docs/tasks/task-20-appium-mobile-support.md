# Task 20: Appium Mobile Snapshot Saver

> **Status:** PLANNED — not yet started. The bundle spec is already v2
> (`MANIFEST_VERSION = 2`); the "spec-v1" wording and the
> `"version": 1` manifest example below are stale and must be rebased
> onto v2 before starting.
>
> **Goal:** Ship `appium-snapshot-saver` — a `PageAdapter` over Appium that captures native mobile page source + screenshot into a spec-v2 bundle, extending the manifest `viewport` with platform + device metadata.
> **Depends on:** Task 15 (`snapshot-core` — done), Task 17 (pattern for adapter packages — planned).
> **Output:** New `packages/appium-snapshot-saver/`, decision on HTML-vs-XML rendering fallback tracked as follow-up.

## Motivation

Appium exposes UI tree dumps as XML, not HTML. Supporting it stretches the snapshot-bundle spec in a productive way: it forces the core to acknowledge that "page source" isn't always HTML, and it opens the plugin to mobile QA workflows. This is the largest unknown in the roadmap — scoped last deliberately.

## Key Files

- `PageAdapter` interface — defined in `packages/snapshot-core/src/types.ts`. May need a `sourceFormat: "html" | "xml"` field on the adapter result.
- New: `packages/appium-snapshot-saver/`
  - `src/index.ts` — `AppiumAdapter implements PageAdapter`
  - `tests/` — integration test against Appium's demo app or a stubbed driver

## Adapter Notes

### AppiumAdapter
- Wraps `webdriverio`'s Appium client.
- `getHTML()` → `driver.getPageSource()` (returns XML on native, HTML in webview mode).
- `getCSS()` → empty list for native contexts (no CSS concept).
- `screenshot()` → `driver.takeScreenshot()`.
- `getViewport()` → `driver.getWindowSize()` + device metadata from session capabilities.
- Manifest extension:
  ```json
  {
    "version": 2,
    "viewport": {
      "width": 390,
      "height": 844,
      "platform": "iOS",
      "deviceName": "iPhone 14",
      "orientation": "portrait"
    },
    "appium": "2.5.4"
  }
  ```
  This is an **additive** manifest change against the v2 schema — does not bump `manifest.version`.

## Open Question: Plugin-side Rendering of XML

The plugin's tool window iframe is HTML-only. XML page-source cannot render there directly. Options:

1. **Out of scope for this task** — the saver produces bundles; the plugin renderer adds an XML fallback later (track as new task, "Task 21: Plugin XML viewer for native mobile snapshots").
2. **Convert at capture time** — the Appium adapter transforms XML into a debug-styled HTML tree representation before writing. Lossy for some attributes, but renders immediately.

**Decision:** take option 1 — keep bundles faithful to the source format and defer rendering. The saver writes `index.xml` *alongside* `index.html` (a minimal HTML that loads/displays the XML for debugging). The plugin continues to treat these bundles as "supported for file listing and screenshot only" until Task 21 lands.

## Steps

1. Add an optional `sourceFormat: "html" | "xml"` field to `PageAdapter`'s return from `getHTML()` (or rename to `getSource()`). Update Playwright/Selenium/Cypress adapters to pass `"html"` explicitly.
2. Update `snapshot-core` to write both `index.html` (a minimal wrapper showing "native snapshot — see index.xml") and `index.xml` when `sourceFormat === "xml"`.
3. Scaffold `packages/appium-snapshot-saver/`. Depend on `@pagemirror/snapshot-core`, peer-depend on `webdriverio`.
4. Implement `AppiumAdapter`. Extract platform/device from session capabilities and pass into `SaveSnapshotOptions.viewport` extras.
5. Update `docs/snapshot-bundle-spec.md` with the additive manifest fields and the dual-file layout. Do **not** bump schema version (additive change per spec rules).
6. Integration test: either against the Appium demo app via a CI-provisioned emulator, or against a stubbed driver that returns canned page source + screenshot. Prefer stubbed for CI stability; real-device test stays manual.
7. README with platform-specific setup notes.

## Verification

- Bundles produced by `AppiumAdapter` conform to spec v2 with the additive mobile fields.
- Both `index.html` and `index.xml` present for native snapshots; only `index.html` for webview snapshots.
- `snapshot-core` tests still pass; no regression for Playwright/Selenium/Cypress bundles.
- The plugin tool window can list and show screenshots for mobile bundles (full XML rendering waits for the follow-up task).

## Out of Scope

- Plugin-side XML tree rendering (→ follow-up Task 21).
- iOS-specific accessibility inspection.
- Publishing to npm registry.
