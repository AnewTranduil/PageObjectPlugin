# Task 15: Extract Framework-Agnostic `snapshot-core`

> **Goal:** Split `playwright-snapshot-saver` into a framework-agnostic `@pagemirror/snapshot-core` package plus a thin Playwright adapter, enabling future Selenium / Cypress / Appium adapters (Tasks 17, 20) without duplicating bundle assembly, manifest generation, and HTML post-processing.
> **Depends on:** Task 14 (aggregator) — recommended but not required.
> **Output:** New `packages/snapshot-core/`, refactored `packages/playwright-snapshot-saver/` depending on it.
>
> **Pre-1.0 policy:** this package has not shipped 1.0.0 yet. Breaking changes to the bundle format, public API, and options shape are allowed as long as re-running the tests regenerates valid bundles. Do not spend effort on compatibility shims or golden-file byte-equivalence — prefer a clean design.

## Motivation

Bundle assembly, manifest generation, and HTML post-processing currently live in `packages/playwright-snapshot-saver/src/`. Adding a Selenium or Cypress adapter would require duplicating them. A clean `PageAdapter` abstraction lets the core handle universal work; each driver package provides only a small adapter.

## Scope Decision: Live-Capture vs Trace-Extraction

The current package has **two independent pipelines**, and this task must address both explicitly:

| Pipeline | Entry point | Mechanism | Scope for this task |
|---|---|---|---|
| **Live capture** | `saveSnapshot(page, options)` | `page.evaluate` serializes DOM + inlines CSS from inside the browser | **In scope.** Refactor behind `PageAdapter`. |
| **Trace extraction** | `extractSnapshots({ source })` + `snapshot({ page, state })` marker | Post-hoc: reads trace ZIP via Playwright internals, renders via `SnapshotRenderer` | **Out of scope for core.** Stays inside `playwright-snapshot-saver`. Only Playwright has the trace format, so there is no framework-agnostic abstraction to extract here. |

`reporter.ts`, `snapshot-marker.ts`, `extractor.ts`, `trace/`, and `sources/` all stay in the Playwright package.

## Key Files

- `packages/playwright-snapshot-saver/src/index.ts` — keep as public entry; becomes a shim.
- `packages/playwright-snapshot-saver/src/html-inliner.ts` — **rewrite required**; cannot move verbatim (runs inside `page.evaluate`).
- `packages/playwright-snapshot-saver/src/manifest-generator.ts` — **rewrite required**; calls `page.viewportSize()`, `page.url()`, `page.evaluate()`.
- `packages/playwright-snapshot-saver/src/reporter.ts`, `snapshot-marker.ts`, `extractor.ts`, `trace/`, `sources/` — stay.
- `packages/playwright-snapshot-saver/tests/` — inliner/manifest unit tests move to `snapshot-core/tests/`; Playwright-integration tests and trace-extraction tests stay.
- New: `packages/snapshot-core/` (private workspace, name `@pagemirror/snapshot-core`).

## Inliner Refactor Strategy

`html-inliner.ts` today fetches stylesheet CSS text from inside the page context because cross-origin `cssRules` access requires same-origin (the page itself). It cannot simply "move to Node" — the browser is where the CSSOM lives.

**Approach:** split into two pieces.

1. **Browser-side collector** (lives in `snapshot-core`, distributed as a string for `page.evaluate` / `driver.executeScript`): walks the DOM, resolves `document.styleSheets` into an array of `{ href?, source }`, applies `extraSelectors`/`excludeSelectors`/`extraAttributes`, and returns a structured payload `{ html, stylesheets, url, viewport, userAgent }`.
2. **Node-side assembler** (pure, testable): takes that payload and produces the final `index.html` string (replaces `<link rel=stylesheet>` with `<style>`, applies post-processing). No browser APIs.

The adapter's responsibility shrinks to: evaluate the collector script in its driver's way, capture a screenshot, and return the payload. Selenium/Cypress adapters reuse the identical collector string.

## Bundle Format Change (breaking, intentional)

The screenshot is no longer a first-class top-level file. It is treated as just another captured resource and lives alongside CSS/images/fonts under a uniform `resources/` directory.

New bundle layout:

```
<snapshot-name>/
  index.html        # still required, now references resources/ by relative path
  manifest.json     # still required
  resources/
    screenshot.webp # (or .png)
    <sha1>.css
    <sha1>.woff2
    <sha1>.png
    ...
```

Rationale: unifies the "things referenced from index.html" storage path, removes special-casing in the saver, reader, and plugin. The Kotlin side (`SnapshotBundle`, `SnapshotService`, `SnapshotDiscoveryTest`) and `docs/snapshot-bundle-spec.md` + `CLAUDE.md` schema must be updated as part of this task. Re-running `test-project/` regenerates the new layout.

## Interfaces

```ts
// snapshot-core/src/types.ts
export interface StylesheetData {
  href?: string;
  source: string;
}

export interface Resource {
  /** Relative path under resources/, e.g. "screenshot.webp" or "a1b2c3.woff2" */
  filename: string;
  bytes: Buffer;
}

export interface CapturedPage {
  html: string;
  stylesheets: StylesheetData[];
  resources: Resource[];         // screenshot, if any, is just an entry here
  url: string;
  viewport: { width: number; height: number };
  userAgent?: string;
}

export interface CollectorOptions {
  extraSelectors?: string[];
  excludeSelectors?: string[];
  extraAttributes?: string[];
}

export interface PageAdapter {
  /**
   * Runs the core collector script in the browser and returns its result.
   * The adapter is responsible for also pushing a screenshot Resource into
   * the returned payload when the caller requested one.
   */
  capture(options: CollectorOptions & { screenshot?: { format: "webp" | "png"; fullPage: boolean } }): Promise<CapturedPage>;
}

export interface SaveSnapshotOptions extends CollectorOptions {
  outputDir: string;
  name: string;
  group?: string;
  screenshot?: { format?: "webp" | "png"; fullPage?: boolean } | false;
  manifest?: boolean;
}

export interface SnapshotResult {
  outputDir: string;
  files: { html: string; manifest?: string; resources: string[] };
}

export async function saveSnapshot(
  adapter: PageAdapter,
  options: SaveSnapshotOptions
): Promise<SnapshotResult>;
```

No compatibility shim. `packages/playwright-snapshot-saver/src/index.ts` exports the new `saveSnapshot(adapter, options)` directly, and additionally provides a tiny convenience:

```ts
// packages/playwright-snapshot-saver/src/index.ts
import { Page } from '@playwright/test';
import { saveSnapshot as coreSaveSnapshot, SaveSnapshotOptions, SnapshotResult } from '@pagemirror/snapshot-core';
import { PlaywrightAdapter } from './playwright-adapter';

export function saveSnapshot(page: Page, options: SaveSnapshotOptions): Promise<SnapshotResult> {
  return coreSaveSnapshot(new PlaywrightAdapter(page), options);
}
export { extractSnapshots, snapshot } from /* trace pipeline, unchanged */;
export type { SaveSnapshotOptions, SnapshotResult } from '@pagemirror/snapshot-core';
```

The Playwright entry point keeps a `page`-first signature because it is the ergonomic call site for Playwright users — this is not a compat layer, it is the public Playwright API. Other adapters will expose their own driver-first helpers.

**Note on `driver` field:** deliberately omitted. Adding a `driver: { name, version }` field to `SaveSnapshotOptions` / manifest can happen later; Task 17/20 will drive that change when a second adapter actually needs to distinguish itself.

## Steps

1. **Scaffold** `packages/snapshot-core/` with `package.json` (name `@pagemirror/snapshot-core`, `private: true`), `tsconfig.json` matching the Playwright package, `src/`, `tests/`. Add to root `package.json` `workspaces` array.
2. **Define interfaces** in `snapshot-core/src/types.ts` as above.
3. **Write the browser-side collector** at `snapshot-core/src/browser/collector.ts`. Authored so it can be stringified and injected (no Node imports; export a `collectorSource: string` built from the function's `.toString()`). Port the existing `document.querySelectorAll('link[rel="stylesheet"]')` logic from `html-inliner.ts:4-34`, plus `extraSelectors`/`excludeSelectors`/`extraAttributes` handling.
4. **Write the Node-side assembler** `snapshot-core/src/assemble-html.ts` — pure `(captured: CapturedPage) => string`. Rewrites CSS/image/font references to `resources/<filename>` and replaces `<link rel=stylesheet>` with either inline `<style>` or a `resources/<sha1>.css` reference (design choice: consistent with how other resources are handled — prefer sidecar files).
5. **Write `saveSnapshot` orchestration** in `snapshot-core/src/save-snapshot.ts`: call `adapter.capture()`, run `assembleHtml`, write `index.html`, write every `CapturedPage.resources[]` entry (including the screenshot, if the adapter added one) into `<outDir>/resources/`, generate manifest via `buildManifest`, preserve the "skip write if unchanged" optimization from `playwright-snapshot-saver/src/index.ts:51-81`.
6. **Write `buildManifest`** in `snapshot-core/src/manifest.ts` — pure `(captured: CapturedPage, previousVersion?: number) => ManifestJson`. No `page` parameter.
7. **Port unit tests** for inliner and manifest from `packages/playwright-snapshot-saver/tests/` to `packages/snapshot-core/tests/`. Playwright-integration tests and trace-extraction tests stay put.
8. **Write `PlaywrightAdapter`** in `packages/playwright-snapshot-saver/src/playwright-adapter.ts`:
   - `capture()`: runs the collector via `page.evaluate(new Function('opts', 'return (' + collectorSource + ')(opts)'), options)`, merges in `page.viewportSize()`, `page.url()`, `await page.evaluate(() => navigator.userAgent)`. If `options.screenshot` is present, calls `page.screenshot({ type, fullPage })` (no `path`) and pushes the resulting buffer into `capturedPage.resources` as `screenshot.<ext>`.
9. **Update `packages/playwright-snapshot-saver/src/index.ts`** to the new shape shown above. Delete `html-inliner.ts` and `manifest-generator.ts`. Trim `types.ts` of anything now re-exported from core.
10. **Declare the dependency.** `packages/playwright-snapshot-saver/package.json` adds `"@pagemirror/snapshot-core": "*"` under `dependencies`. Do **not** use `workspace:*` — npm workspaces understand `*`, and this keeps `file:` consumers working.
11. **Resolve `test-project/` dependency.** `test-project/package.json` uses `file:../packages/playwright-snapshot-saver`. `file:` installs do **not** follow workspaces, so `@pagemirror/snapshot-core` won't auto-resolve for it. Fix: add `"@pagemirror/snapshot-core": "file:../packages/snapshot-core"` alongside the existing saver entry.
12. **Update the Kotlin plugin side** to read the new bundle layout: `SnapshotBundle`, `SnapshotService`, any code that hard-codes `screenshot.webp` / `screenshot.png` at the top level, and `SnapshotDiscoveryTest`. Existing `.snapshots/` fixtures under `test-project/` are regenerated by re-running the Playwright tests.
13. **Update docs**: bump `docs/snapshot-bundle-spec.md` and the bundle-format block in `CLAUDE.md` to reflect the `resources/` layout. Add a CHANGELOG entry noting the pre-1.0 breaking change.
14. **Regenerate fixtures**: run `test-project/` Playwright tests to produce new-format bundles, commit the results.
15. **Run** `./gradlew test` to confirm the Kotlin side loads the new layout.

## Verification

- `npm test` at the repo root runs both `snapshot-core` and `playwright-snapshot-saver` suites green.
- `npm run build` in both packages succeeds.
- `import { saveSnapshot, extractSnapshots, snapshot } from 'playwright-snapshot-saver'` compiles and runs; `saveSnapshot(page, options)` still works from a test.
- `test-project/` Playwright tests produce bundles in the new `resources/` layout with valid `index.html`, `manifest.json`, and `resources/screenshot.*`.
- `./gradlew test` passes — in particular `SnapshotDiscoveryTest` discovers and reads the new-layout bundles under `test-project/.snapshots/`.
- Opening a regenerated `index.html` in the plugin's JCEF tool window renders correctly, with the screenshot resolved via its `resources/` path.

## Out of Scope

- **Selenium / Cypress adapters** (→ Task 17). `PageAdapter` must be designed to support them, but no second adapter is implemented here.
- **Appium / mobile** (→ Task 20).
- **Trace-extraction pipeline** — stays entirely in `playwright-snapshot-saver`. No `TraceSource` abstraction in core.
- **`driver` field in manifest / `SaveSnapshotOptions`.** Deferred to Task 17/20.
- **Resource inlining for trace-extracted HTML** (images/fonts/external CSS behind `/snapshot/<sha1>` URLs). Separate concern, lives in the trace pipeline — see **Task 15.5**.
