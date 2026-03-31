# Playwright Snapshot Internals — Research Report

> **Date:** 2026-03-30
> **Purpose:** Understand how Playwright captures DOM snapshots in traces, evaluate reuse potential for `playwright-snapshot-saver`.

---

## Architecture Overview

Playwright's tracing system captures DOM snapshots at three points during each action: **before**, **input**, and **after**. The mechanism is pure JavaScript injection — no Chrome DevTools Protocol (CDP) is used for DOM capture.

### Key Source Files (microsoft/playwright)

| File | Role |
|------|------|
| `packages/playwright-core/src/server/trace/recorder/snapshotter.ts` | Orchestrator — injects capture code, coordinates snapshot timing |
| `packages/playwright-core/src/server/trace/recorder/snapshotterInjected.ts` | Browser-injected DOM/CSS capture logic |
| `packages/playwright-core/src/server/trace/recorder/tracing.ts` | Packages snapshots + events into .zip |
| `packages/trace/src/snapshot.ts` | TypeScript type definitions for snapshot format |
| `packages/trace/src/trace.ts` | Type definitions for trace events |

---

## Capture Mechanism

### 1. Injection

`Snapshotter` injects a `FrameSnapshotStreamer` class into every frame via `addInitScript()`. The streamer lives on the `window` object and is invoked on demand:

```typescript
const expression = `window["${this._snapshotStreamer}"].captureSnapshot(${needsReset})`;
await frame.nonStallingRawEvaluateInExistingMainContext(expression);
```

`nonStallingRawEvaluateInExistingMainContext()` is a non-blocking evaluation that avoids interfering with in-flight async operations on the page.

### 2. DOM Traversal

The injected `FrameSnapshotStreamer` recursively walks all DOM nodes:

- **Elements:** tag name, attributes, children
- **Text nodes:** content
- **Shadow DOM:** captured via `element.shadowRoot`
- **Skipped:** `<script>` tags, CSP `<meta>` tags, preload/prefetch `<link>` tags

### 3. State Capture

Beyond static DOM, the streamer captures dynamic state:

| State | Mechanism |
|-------|-----------|
| Input values | `element.value` (stored as `__playwright_value_`) |
| Checkbox/radio | `element.checked` (stored as `__playwright_checked_`) |
| Selected options | `option.selected` (stored as `__playwright_selected_`) |
| Scroll positions | `scrollTop`, `scrollLeft` (stored as `__playwright_scroll_top_`, `__playwright_scroll_left_`) |
| Canvas content | Bounding rect captured for replacement |
| Dialog/popover | `.open` state |

### 4. CSS Capture

CSS is captured through two mechanisms:

**Inline `<style>` tags:** reads `sheet.cssRules` and joins their `cssText`.

**`<link>` stylesheets:** tracked as resource overrides with URL + content.

**Runtime mutations:** Playwright intercepts native CSS APIs to detect changes:
- `CSSStyleSheet.prototype.insertRule()`
- `CSSStyleSheet.prototype.deleteRule()`
- `CSSStyleSheet.prototype.replaceSync()`
- `CSSStyleSheet.prototype.replace()`
- `CSSGroupingRule.prototype.insertRule()`
- `CSSGroupingRule.prototype.deleteRule()`

When a mutation is detected, the stylesheet is marked "stale" and re-read on the next snapshot capture. Unchanged stylesheets are cached.

---

## Snapshot Data Format

### NodeSnapshot (array-based tree)

```
[elementName, attributesObject, ...childNodes]
```

Example:
```json
["div", {"class": "container"},
  ["input", {"type": "text", "__playwright_value_": "hello"}],
  ["button", {}, "Submit"]
]
```

### FrameSnapshot Object

```typescript
interface FrameSnapshot {
  callId: string;           // Associated action ID
  snapshotName: string;     // e.g., "before@123", "after@123"
  html: NodeSnapshot;       // Root of DOM tree
  doctype: string;          // e.g., "<!DOCTYPE html>"
  viewport: { width: number; height: number };
  resourceOverrides: Array<{
    url: string;
    content?: string;
    sha1?: string;
    contentType: string;    // e.g., "text/css"
  }>;
  timestamp: number;
  wallTime: number;
  collectionTime: number;
  pageId: string;
  frameId: string;
  frameUrl: string;
  isMainFrame: boolean;
}
```

---

## Trace File Format

Traces are packaged as `.zip` archives:

```
trace.zip
  trace.trace       # Newline-delimited JSON events
  trace.network     # HAR format (network resources)
  resources/
    <sha1>          # Binary blobs (CSS content, images, etc.)
    <sha1>
    ...
```

### Event Types in `trace.trace`

| Type | Content |
|------|---------|
| `context-options` | Browser config, version info |
| `before` | Pre-action event with `beforeSnapshot` reference |
| `input` | User input event with `inputSnapshot` reference |
| `after` | Post-action event |
| `frame-snapshot` | Full DOM snapshot (FrameSnapshot object) |
| `resource-snapshot` | Network resource captured |

### Resource Deduplication

Resources are stored as binary blobs keyed by SHA1 hash. Multiple snapshots referencing the same CSS file share a single blob. This makes traces compact even with many snapshots.

---

## Comparison with `playwright-snapshot-saver`

| Capability | Playwright Traces | Our Package |
|------------|-------------------|-------------|
| DOM capture | Array-based tree format | Standard HTML string (`outerHTML`) |
| CSS inlining | Resource overrides + mutation tracking | `cssRules` read + inline `<style>` replacement |
| Element bounds | Not captured | `getBoundingClientRect()` per element |
| Selector generation | Not captured | `bestSelector()` algorithm (data-testid > #id > [name] > positional) |
| Element roles | Not captured | `inferRole()` (semantic tag mapping + explicit role) |
| Interactive flag | Not captured | Tag-based + role-based detection |
| Element attributes | Only raw DOM attributes | Curated attribute set (configurable) |
| Output format | Proprietary .zip with array-tree snapshots | `index.html` + `layout.json` + `screenshot` + `manifest.json` |
| Self-contained HTML | No (requires trace viewer to render) | Yes (CSS inlined, no external dependencies) |
| Screenshot | Stored in trace as action attachment | Standalone file (png/jpeg) |
| Primary use case | Test replay & debugging in trace viewer | Static snapshot for IDE plugin rendering |

### What Playwright traces provide that we don't

- **Dynamic state:** input values, checkbox states, scroll positions
- **Action timeline:** before/input/after snapshots per action
- **Network resources:** full HAR capture
- **CSS mutation tracking:** intercepts runtime CSS changes
- **Shadow DOM:** full shadow root traversal

### What we provide that Playwright traces don't

- **`layout.json`:** element positions, dimensions, selectors, roles, interactivity flags
- **Self-contained HTML:** renderable without tooling
- **Selector generation:** CSS selectors that resolve in the captured HTML
- **Configurable element discovery:** `extraSelectors`, `excludeSelectors`, `extraAttributes`
- **Direct Jsoup compatibility:** HTML parseable server-side for gutter validation

---

## Reuse Assessment

### Could we use Playwright's snapshot mechanism?

**No, for several reasons:**

1. **Format incompatibility.** Playwright's array-tree format requires the trace viewer to reconstruct HTML. Our Kotlin plugin needs standard HTML for Jsoup parsing and JCEF iframe rendering.

2. **No layout data.** Playwright traces don't capture `getBoundingClientRect()` or generate selectors. These are core to our `layout.json` contract — used for highlight positioning, element picker, and gutter validation.

3. **Internal API.** The `FrameSnapshotStreamer` is not exported. It's injected via `addInitScript()` and accessed through internal frame evaluation methods. No public API to invoke it standalone.

4. **Overhead.** Trace snapshots capture everything (full DOM tree, all resources, mutation tracking). Our package only needs interactive elements + elements with IDs/test-ids.

### What CSS techniques could we borrow?

Playwright's CSS mutation interception (patching `insertRule`, `deleteRule`, etc.) is more robust than our approach for pages with dynamic CSS. However:

- Our target pages are static snapshots captured at a point in time — dynamic CSS mutations aren't a concern.
- Our `cssRules` + `fetch` fallback approach (inherited from `save-state.ts`) handles the common cases.
- Adding mutation tracking would add complexity without clear benefit for our use case.

### Alternative: CDP `DOMSnapshot.captureSnapshot`

A potentially useful alternative not used by Playwright:

```typescript
const client = await page.context().newCDPSession(page);
const snapshot = await client.send('DOMSnapshot.captureSnapshot', {
  computedStyles: ['display', 'visibility', 'position', ...],
});
```

This returns DOM + computed styles + layout info in one call. Worth investigating separately if we need bounds data without `page.evaluate()`.

---

## Conclusion

Playwright's trace snapshot system is purpose-built for the trace viewer's replay functionality. Its proprietary format, lack of layout/selector data, and internal-only API make it unsuitable for direct reuse in `playwright-snapshot-saver`. Our package's approach — `page.evaluate()` for HTML inlining and element discovery — is the right level of abstraction for producing IDE-consumable snapshots.

The one area where Playwright's approach is superior is CSS mutation tracking, but this isn't needed for our point-in-time capture use case.
