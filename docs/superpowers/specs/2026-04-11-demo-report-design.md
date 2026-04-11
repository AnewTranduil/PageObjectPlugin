# Demo Report Viewer Redesign: Execution Design

**Source task:** `docs/tasks/task-19-feature-demo-trace-viewer.md`
**Supersedes (UX section only):** `docs/superpowers/specs/2026-04-09-task-19-feature-demo-trace-viewer-design.md` — the "PR 2 — Renderer + viewer template" viewer description. The renderer / Gradle / CI sections of that spec remain authoritative.
**Date:** 2026-04-11
**Shape:** Retrospective design — captures the viewer UX as it stands on `claude/demo-report-design-bPEM7` after commits `82c521e`, `4c7ad24`, `c0947e8`, `c2fc060`. No code changes accompany this document.

## Goal

A reviewer who clicks the demo artifact link on a `[demo:<tag>]` PR should be able to walk every UI scenario covering that feature in one click, with the **screenshot — not the textual step list — as the primary visual artifact**, and with no framework, no bundler, and no network dependency in the rendered HTML.

## Scope

Viewer frontend only:

- `src/main/resources/demo-viewer/index.html`
- `src/main/resources/demo-viewer/styles.css`
- `src/main/resources/demo-viewer/app.js`

Out of scope (deferred to the predecessor spec): `DemoReportRenderer.kt`, `DemoTestSelector.kt`, the `@Feature` annotation pipeline, `demo.yml`, the `demoReport` Gradle task. These are fixed context for this design.

## Layout reference

```
┌──────────┬──────────────────────────────┬──────────┐
│ tests    │ screenshot                   │ steps    │
│ (260px)  │ (flex)                       │ (280px)  │
│ grouped  │                              │          │
│ by       │                              │ 1. open  │
│ feature  ├──── pane-divider (drag) ─────┤ 2. click │
│          │ tabs: DOM | Failure          │ 3. type  │
│          │ (height = bottomPaneHeight)  │ 4. wait  │
└──────────┴──────────────────────────────┴──────────┘
```

The original 2026-04-09 wireframe was `tests | step-timeline | details` — a horizontal arrangement that squeezed the screenshot into a "details" column. The redesign promotes the screenshot to the load-bearing center column and demotes both the step list (now a vertical click-list on the right) and the DOM/failure detail (now tabs *under* the screenshot, sharing vertical space via a draggable divider).

## Decisions

### 1. Three-pane CSS grid, screenshot in the middle
Replaces the original `tests | timeline | details` layout with a CSS grid:

```css
grid-template-columns: 260px minmax(0, 1fr) 280px;
```

The `minmax(0, 1fr)` on the center column is **load-bearing**. Without the explicit `0` minimum, a wide DOM-snapshot iframe (the inlined captured page can be very wide) would force the center column to grow past `1fr` and the screenshot would either overflow the viewport or collapse below `min-content`. This was the single bug that motivated the layout overhaul in commit `c2fc060`.

- Anchor: `src/main/resources/demo-viewer/styles.css:15-19`

### 2. Center column is a flex column: screenshot · divider · tabs
`section#artifact` is `display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden`. The screenshot pane uses `flex: 1 1 auto` and the image inside uses `object-fit: contain` so it fills the leftover vertical space above the tabs without overflowing in either dimension. `min-height: 80px` on the pane prevents the screenshot from collapsing to zero when the tabs are dragged tall.

- Anchor: `src/main/resources/demo-viewer/styles.css:29-49`

### 3. Resizable bottom pane via plain mouse-drag, no library
The divider is a 6 px `div` with three vanilla DOM listeners (`mousedown` → `mousemove` + `mouseup`) that write `tabsEl.style.height` directly. Bounded `[40, 1500]` px. Drag direction is **inverted** (drag UP grows the screenshot, drag DOWN grows the tabs) because the screenshot is the primary visual; reviewers expect "make the important thing bigger" to be the upward gesture. While dragging, `body.resizing-vert` swaps the cursor and disables text selection.

No third-party splitter component, no `ResizeObserver`, no `requestAnimationFrame` throttling — the budget is well below a frame even on cold cache.

- Anchor: `src/main/resources/demo-viewer/app.js:270-291`

### 4. Tabs auto-hide when empty
If the selected test has neither a DOM snapshot nor a failure stack, both `#tabs` and `#pane-divider` get the `.hidden` class so the screenshot takes the **full** center column. The default tab is picked per-test in `pickDefaultTab`: `failure` for failing tests, otherwise `dom`. Switching tests resets to that test's default tab (rather than preserving the user's previous selection) because the previous tab may not even be enabled for the new test.

If the user clicks a test where the currently selected tab is empty, the renderer falls back to the other enabled tab inline rather than waiting for a re-render.

- Anchor: `src/main/resources/demo-viewer/app.js:169-173, 175-192`

### 5. DOM snapshot in a fully sandboxed iframe
The captured page is rendered via `iframe.src = test.domHtmlDataUri` plus `iframe.setAttribute('sandbox', '')` — an **empty** allow-list. The captured page can therefore not run scripts, submit forms, navigate the top window, open popups, or access storage. This mirrors the same "Snapshot HTML in iframe" rule that the production plugin enforces for its tool window (see `CLAUDE.md` → "Critical Constraints"), and rules out CSS bleed into the viewer chrome.

- Anchor: `src/main/resources/demo-viewer/app.js:213-218`

### 6. Feature grouping in the left sidebar
Tests are bucketed by `t.feature || data.feature || 'Ungrouped'`. Groups are collapsible with a caret (`▶` / `▼`); only the group containing the initially selected test is expanded on load. Selecting a test inside a collapsed group from another path auto-expands its group as a side effect.

Groups keep their **first-seen order** — no alphabetical sort — because the test discovery order conveys meaningful information (the feature tag is read off the test class, and the JUnit discovery order tends to match the package layout the developer uses to think about the feature).

- Anchor: `src/main/resources/demo-viewer/app.js:25-45, 62-104`

### 7. Right sidebar is a numbered step list, not a horizontal timeline
The step list is a vertical click-list, not a Playwright-style horizontal timeline:

- Index column uses `font-variant-numeric: tabular-nums` so step numbers stay aligned regardless of digit width.
- Steps with errors get the `.has-error` class (red text) — the failing step is the only piece of step metadata the screenshot doesn't already convey.
- Clicking a step updates **only** the screenshot pane; it does not change the test selection or the active tab.

It is intentionally a click-list, not a hover-scrubbable timeline, because the screenshot is the primary visual: reviewers should select a step deliberately, not skim across them.

- Anchor: `src/main/resources/demo-viewer/styles.css:135-161`, `src/main/resources/demo-viewer/app.js:107-143`

### 8. Click-to-zoom lightbox with a fit/actual toggle
Clicking the screenshot appends a fixed-position overlay to `<body>`. The overlay has two image modes:

- `.fit` — `max-width: 95vw; max-height: 90vh`, cursor `zoom-in`. The default.
- `.actual` — `max-width: none; max-height: none`, cursor `zoom-out`. Native pixels, scrollable inside the overlay.

Clicking the image toggles between them. Esc and clicking the overlay background both close the lightbox. There is **no focus trap and no `role="dialog"` ARIA wiring** — by design, the lightbox is a quick reviewer affordance, not a modal that needs to be bullet-proof against keyboard tabbing. The viewer is mouse-only end-to-end (see Risks).

- Anchor: `src/main/resources/demo-viewer/app.js:227-265`, `src/main/resources/demo-viewer/styles.css:167-194`

### 9. Vanilla ES5 IIFE — no bundler, no framework
The whole app is one `(function () { ... })()` with `var`, manual `innerHTML`, an `escapeHtml` helper, and zero dependencies. The contract is that `DemoReportRenderer` substitutes `/*__APP__*/` directly into the inlined `<script>` tag of a single self-contained HTML file the reviewer opens from `file://` (often via "Download artifact" on a CI run). Anything that needs a bundler, polyfills, or a network round-trip would compromise that contract.

- Anchor: `src/main/resources/demo-viewer/app.js:1-6, 300-303`

### 10. `color-scheme: light dark` plus translucent neutrals
`:root { color-scheme: light dark; font-family: system-ui, sans-serif; }` lets the viewer adopt the reviewer's OS theme without any JS toggle. Borders and hover states use translucent grays (`#8884`, `#8882`, `#8883`) and the accent color (`#3b82f6`) is used at low opacity for selection highlights. There is no explicit dark-mode CSS branch — every color is either a theme-aware system default or a translucent neutral over the system background.

- Anchor: `src/main/resources/demo-viewer/styles.css:1`

## HTML shell

The 25-line `index.html` is intentionally trivial. It owns three substitution placeholders that `DemoReportRenderer` fills in:

- `/*__STYLES__*/` inside `<style>` — the contents of `styles.css`.
- `/*__DATA__*/` inside `window.__TRACE_DATA__ = ...` — the JSON blob of test records.
- `/*__APP__*/` inside the trailing `<script>` — the contents of `app.js`.

Every interactive element is created dynamically by `app.js`; the shell only declares the empty mount points (`#summary`, `#test-list`, `#screenshot-pane`, `#pane-divider`, `#tabs`, `#step-list`).

- Anchor: `src/main/resources/demo-viewer/index.html:1-25`

## Risks & Notes

- **No keyboard navigation.** Test list, step list, tabs, and lightbox are all mouse-only. Acceptable for a reviewer-facing demo viewer; flag as follow-up if accessibility becomes a hard requirement.
- **`bottomPaneHeight` is not persisted.** Reloading the viewer resets the divider to `260` px. Local storage would help but the viewer is opened from `file://` where storage quotas and cross-file isolation are awkward; not worth the complexity for v1.
- **`expandedGroups` is not persisted.** Same reasoning.
- **`escapeHtml` only escapes `& < >`.** Adequate for the data we currently render (`feature`, `displayName`, `method`, `label`, `gitSha`) which are interpolated into element text — never into attribute values or URLs. Don't extend the data shape with attribute-context strings without revisiting this.
- **No size cap on the rendered HTML.** Inherited from the predecessor spec — long traces with many screenshots produce tens-of-MB single files. The reviewer download path tolerates this; the dashboard upload path also tolerates this (see `CLAUDE.md` → Report Dashboard Access).
- **Drag-resize uses pixel deltas, not pointer events.** Touch and pen input are not handled. Acceptable: the viewer is a desktop-reviewer tool.

## Out of Scope

- Renderer (`DemoReportRenderer.kt`), Gradle task (`demoReport`), CI workflow (`demo.yml`), and the `@Feature` annotation pipeline. All four remain governed by `2026-04-09-task-19-feature-demo-trace-viewer-design.md`.
- Server-side or hosted viewer modes; the demo viewer is `file://`-only.
- Cross-test diffing, animations, video timeline scrubbing.
- Accessibility (keyboard nav, focus trap, ARIA roles) — flagged as a follow-up under Risks, not designed here.
