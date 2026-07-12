# Task 15.5: Resource Inlining for Trace-Extracted Snapshots

> **Status:** DONE. `snapshot-core` ports Playwright's `SnapshotRenderer`
> behind a framework-agnostic `TraceBackend` interface; resource
> inlining lives at
> `packages/snapshot-core/src/trace/{types,renderer,inline,extract,runtime-script,content-type}.ts`.
> The Playwright package (`packages/playwright-snapshot-saver/src/trace/playwright-backend.ts`)
> reshapes `TraceLoader.storage()` into that interface and
> `packages/playwright-snapshot-saver/src/extractor.ts` delegates to
> `extractFromBackend`. Every `<link>`, `<img>`, CSS `url(...)`,
> `@font-face`, and SVG `<use>` reference resolves to a file under
> `resources/`, and `<base>` is stripped. See CLAUDE.md's "Task 15.5"
> paragraph.
>
> **File-reference note.** Steps below reference the pre-implementation
> plan — `inline-resources.ts`, `resolveTraceResource`, `renderSnapshotAtMarker`,
> and the `__playwright_target__` strip at `extractor.ts:142` — none of
> which shipped in that shape. Read the paragraph above for the shipped
> file layout; the steps are retained to document what was actually
> executed against the earlier plan.
>
> **Scope expanded 2026-04-15.** The original plan post-processed Playwright's
> rendered HTML. The implementation instead owns rendering: `snapshot-core`
> now ports Playwright's `SnapshotRenderer` behind a framework-agnostic
> `TraceBackend` interface. Seven-category inlining still applies — only the
> mechanism changed.
>
> **Goal:** Make trace-extracted snapshots self-contained by walking the
> rendered HTML, resolving every external reference through the snapshot's
> `resourceByUrl` closure, and materializing the bytes into `resources/`
> (same layout as live-capture bundles from Task 15).
> **Depends on:** Task 15 (establishes the `resources/` bundle layout and the `Resource[]` concept in `snapshot-core`).
> **Output:** Trace-extracted bundles render correctly when opened outside the Playwright trace viewer — images, fonts, external CSS, canvas bitmaps, SVG sprites, and media all resolve to files under `resources/`.

## Background

`packages/playwright-snapshot-saver/src/trace/playwright-adapter.ts:232-257` calls `renderer.render()` to obtain snapshot HTML. That HTML looks complete, but every externally-referenced asset is rewritten to a URL like `/snapshot/<sha1>/...` that only Playwright's own trace-viewer HTTP server can serve. When the plugin opens the saved `index.html` in JCEF, every such reference is a dead link.

The underlying data is already in the trace: `loader.resourceForSha1(sha1)` returns a `Blob` for any referenced resource. The task is to walk the rendered HTML, find every `/snapshot/<sha1>/...` reference, fetch the blob, write it to `resources/`, and rewrite the reference to the new relative path.

Scope covers the seven resource categories identified during the trace-coverage review:

1. External stylesheets (`<link rel="stylesheet">`)
2. Images (`<img src>`, `srcset`, `<picture>`, CSS `background-image: url(...)`, SVG `<image href>`)
3. Fonts (`@font-face src: url(...)`)
4. Canvas bitmaps (Playwright stores canvas contents as sha1-addressed images)
5. Media (`<video>`, `<audio>`, `<source>`, `poster`)
6. SVG `<use href="#...">` / external SVG sprites
7. Stop stripping `__playwright_target__` attributes at `extractor.ts:142` — preserve them so the plugin can highlight assertion targets

Child iframe subframes are a separate, larger problem and are **out of scope** for this task (see Task 15.6 below if/when created).

## Key Files

- `packages/playwright-snapshot-saver/src/extractor.ts` — currently strips `__playwright_target__` and writes raw rendered HTML. Refactored to pipe rendered HTML + loader through a new resource resolver before writing.
- `packages/playwright-snapshot-saver/src/trace/playwright-adapter.ts` — add a helper `resolveTraceResource(loader, sha1): Promise<Buffer>` that wraps `loader.resourceForSha1(sha1)`.
- New: `packages/playwright-snapshot-saver/src/trace/inline-resources.ts` — walks HTML + CSS, resolves `/snapshot/<sha1>/...` references to `Resource` entries and rewritten HTML.
- `docs/snapshot-bundle-spec.md` — document that trace-extracted bundles use the same `resources/` layout as live-capture.
- `packages/playwright-snapshot-saver/tests/` — integration test against a fixture trace ZIP that is known to contain images and external CSS.

## Approach

The rewriting needs to happen in two passes because CSS can reference further resources transitively (a stylesheet `/snapshot/<sha1>/style.css` can itself contain `url(/snapshot/<other-sha1>/bg.png)`).

1. **HTML pass.** Parse `rendered.html` with `parse5` (already an indirect dep of Playwright — check before adding). For every attribute value that looks like a `/snapshot/<sha1>/...` URL — `src`, `href`, `srcset` (per-candidate), `poster`, `data-*` used by the snapshot format, plus `style=""` inline values — enqueue the sha1 and rewrite the attribute to `resources/<sha1><.ext>`.
2. **CSS pass.** For every `<style>` block *and* every resolved CSS resource, run a CSS-aware URL walker (not a regex — `postcss` or a small hand-rolled tokenizer that understands strings/comments). Enqueue sha1s, rewrite `url(...)` to `resources/<sha1><.ext>`.
3. **Blob resolution.** For each enqueued sha1, call `loader.resourceForSha1(sha1)`, read the `Blob` → `Buffer`, and determine the file extension from the resource's stored `contentType` (accessible via Playwright's trace metadata — verify the exact shape in `traceLoader.js` and document it in `playwright-adapter.ts`). Fall back to `.bin` if unknown.
4. **Collision handling.** Two resources can have the same sha1 only if they are identical bytes, so `resources/<sha1>.<ext>` is inherently deduplicated. Verify this assumption in the test.
5. **Preserve `__playwright_target__`.** Remove the `rendered.html.replace(...)` at `extractor.ts:142` — the attribute survives to the plugin, which can use it to highlight assertion targets.

## Steps

1. Add `resolveTraceResource(loader, sha1): Promise<{ bytes: Buffer; contentType?: string }>` to `trace/playwright-adapter.ts`. Investigate how Playwright stores `contentType` alongside sha1 (likely in `_resources` or similar on the storage object) and document the access path in a comment.
2. Add an extension-from-content-type helper (e.g. `image/png` → `png`, `font/woff2` → `woff2`, `text/css` → `css`). Keep the mapping narrow and explicit; unknown types fall back to `.bin`.
3. Add `trace/inline-resources.ts` exporting:
   ```ts
   export async function inlineTraceResources(
     renderedHtml: string,
     loader: TraceLoaderType,
   ): Promise<{ html: string; resources: Resource[] }>;
   ```
   where `Resource` is imported from `@pagemirror/snapshot-core` (Task 15 dependency).
4. Implement the HTML pass using `parse5` (confirm dep availability; otherwise add it). Walk attributes listed above. Collect sha1s + rewrite in place. Serialize back.
5. Implement the CSS pass using a real CSS tokenizer (prefer `postcss` — add as a dep). Handle `url(...)`, `@import`, and `image-set()`. Do **not** use a regex.
6. Drive the worklist: every newly-resolved CSS blob is fed back through the CSS pass to discover nested references. Guard against cycles with a `Set<string>` of seen sha1s.
7. Update `extractor.ts`:
   - Remove the `__playwright_target__` strip at line 142.
   - After `renderSnapshotAtMarker`, call `inlineTraceResources(rendered.html, loader)`.
   - Write `index.html` + write each `resource` under `<snapshotDir>/resources/`.
   - The existing screencast-frame logic still applies but now lands as `resources/screenshot.webp` per the Task 15 layout.
8. Add an integration test: check in a small fixture trace ZIP under `packages/playwright-snapshot-saver/tests/fixtures/` that contains at least one image, one web font, and one external stylesheet. Assert that after `extractSnapshots`, the bundle contains `resources/` entries for each, that `index.html` references them by relative path, and that opening the HTML in `cheerio` + following relative links finds each file on disk.
9. Add a unit test for `inlineTraceResources` with a synthetic HTML fragment and a fake loader — no real Playwright trace needed.
10. Update `docs/snapshot-bundle-spec.md` to note that trace-extracted bundles share the same `resources/` layout as live-capture bundles.

## Verification

- `npm test` in `packages/playwright-snapshot-saver/` passes, including the new fixture-trace integration test.
- Run `extractSnapshots` against `test-project/`'s actual trace output (after Task 15 has regenerated the bundle layout). Inspect a produced `index.html`: every `src`/`href`/`url(...)` that is not a data URI must resolve to a file under `resources/`.
- Open a trace-extracted bundle's `index.html` directly in a browser (outside the plugin). Images, fonts, and styles render correctly. No console 404s for `/snapshot/...` URLs.
- The plugin's JCEF tool window renders a trace-extracted snapshot with full visual fidelity.
- `__playwright_target__` attributes survive into the saved HTML (grep the produced file).

## Out of Scope

- **Child iframe subframes.** These live under separate `frame@<id>` keys in `_frameSnapshots` and require recursively rendering + inlining sub-snapshots and injecting them via `srcdoc`. Deserves its own task if/when real test pages demand it.
- **Mouse cursor / click overlay** from action metadata. Nice-to-have but unrelated to resource resolution.
- **Network / console / action timeline data.** Separate plugin features; see the trace-coverage review for the full list of unused trace data.
- **Live-capture resource inlining.** Live capture already produces a self-contained bundle via the CSS-in-`<style>` path; it doesn't need this logic. If image/font inlining is later desired for live capture, that is a separate task.
