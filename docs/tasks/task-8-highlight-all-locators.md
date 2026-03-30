# Task 8: Highlight All Locators + Duplicate Detection

> **Goal:** A toolbar button that highlights every Playwright locator found in the current editor file on the snapshot, with visual indicators for overlapping or duplicate selectors.
> **Depends on:** Tasks 4, 6
> **Output:** "Show All" button -> all locators highlighted simultaneously with overlap/duplicate badges

## Motivation

Currently only the locator under the cursor is highlighted. When writing a page object with 10+ locators, developers have no way to see full coverage at a glance or spot selectors that accidentally resolve to the same element.

---

## Subtask 8a: Refactor page-mirror.html JavaScript

The current `page-mirror.html` has ~280 lines of JS in a single `<script>` block mixing four concerns: snapshot loading, DOM querying, highlight rendering, and inspect mode. Before adding highlight-all, extract the JS into separate modules for maintainability.

### Target structure

```
src/main/resources/html/
  page-mirror.html            # Shell HTML + CSS only, loads JS modules
  js/
    snapshot.js               # loadSnapshot, applyScale, viewport state
    query.js                  # getIframeDoc, getOwnText, queryIframeDom
    highlight.js              # highlightElement, clearHighlight, highlightAll (task 8)
    inspect.js                # toggleInspectMode, setupInspectListeners, findNearestElement
    theme.js                  # setTheme, setHighlightColor
```

### Module responsibilities

**`snapshot.js`** — Snapshot lifecycle and viewport scaling
- State: `_layoutElements`, `_layoutMap`, `_viewportWidth`, `_viewportHeight`, `_scale`
- Exports: `window.loadSnapshot(html, layoutJson)`, `applyScale()`
- Owns the iframe creation and layout JSON parsing
- Listens for `resize` events

**`query.js`** — iframe DOM querying
- Exports: `window.queryIframeDom(type, value)`, `getIframeDoc()`, `getOwnText(el)`
- Pure functions, no state

**`highlight.js`** — All highlight rendering
- Exports: `window.highlightElement(type, value)`, `window.highlightAll(locatorsJson)`, `window.clearHighlight()`
- Uses `queryIframeDom` from `query.js`
- Owns overlay DOM manipulation for highlight boxes and tooltips

**`inspect.js`** — Element picker mode
- State: `_inspectMode`
- Exports: `window.toggleInspectMode()`
- Uses `_layoutElements` from `snapshot.js` for `findNearestElement`
- Owns inspect highlight rendering and click handler

**`theme.js`** — Theme and color management
- Exports: `window.setTheme(theme)`, `window.setHighlightColor(color)`
- Pure DOM manipulation, no shared state

### Loading strategy

Since JCEF loads HTML via `loadHTML()` (data URL), external `<script src>` won't resolve. Two options:

**Option A (recommended): Inline at build time.**
Use a Gradle task to concatenate `js/*.js` files into the `<script>` block of `page-mirror.html` during `processResources`. Source files stay separate for development; the built artifact is a single file.

**Option B: Inline manually with build ordering.**
Keep separate `.js` files but load them via `<script>` tags. In `PageMirrorToolWindowFactory`, read each JS file from resources and inject via `executeJavaScript` after page load. Simpler but adds loading complexity.

### Shared state access

Modules share state through `window` globals (JCEF has a single global scope):
- `snapshot.js` sets `window._layoutElements`, `window._viewportWidth`, etc.
- `inspect.js` reads `window._layoutElements`
- `highlight.js` calls `window.queryIframeDom()`

This avoids module bundler complexity while keeping the source organized.

### Acceptance criteria (8a)

- [x] JS is split into 5 files under `src/main/resources/html/js/`
- [x] `page-mirror.html` contains only HTML + CSS + `/* __JS_BUNDLE__ */` placeholder
- [x] JS files assembled into HTML at runtime via `PageMirrorToolWindowFactory.assemblePageMirrorHtml()`
- [x] All existing functionality works unchanged (highlight, inspect, theme, snapshot loading)
- [x] `./gradlew test` passes
- [x] `./gradlew buildPlugin` produces a working plugin

---

## Subtask 8b: Highlight All Locators

### 1. Kotlin: Collect all locators from the open file

**New method in `SnapshotService.kt`:**

```kotlin
fun highlightAllLocators(locators: List<ExtractedLocator>)
```

- Serializes locators as JSON array: `[{"type":"getByTestId","value":"login-username"}, ...]`
- Calls `window.highlightAll(escapedJson)` via `jsExecutor`
- Sets `isHighlightAllActive = true` flag

**New action `HighlightAllAction.kt`:**

- Reuses the line-scanning loop from `SelectorValidationAnnotator.doAnnotate()`: iterate all lines, call `LocatorExtractor.extract(lineText)`, collect results
- Calls `service.highlightAllLocators(locators)`
- Toggle: first press shows all, second press clears

### 2. Toolbar: "Show All" button

**In `PageMirrorToolWindowFactory.kt`:**

- Add `JButton("Show All")` next to the Pick button
- On click: scan the active editor file, call `highlightAllLocators`
- Toggle state: text changes to `"Show All *"` when active
- Deactivates when user moves cursor to a locator line (single-highlight takes over)

### 3. JavaScript: `window.highlightAll(locatorsJson)` in `highlight.js`

```js
window.highlightAll = function(locatorsJson) {
    window.clearHighlight();
    var locators = JSON.parse(locatorsJson);
    var highlights = [];

    // Query iframe DOM for each locator
    for (var i = 0; i < locators.length; i++) {
        var matches = queryIframeDom(locators[i].type, locators[i].value);
        for (var j = 0; j < matches.length; j++) {
            highlights.push({
                locator: locators[i],
                el: matches[j],
                rect: matches[j].getBoundingClientRect(),
                index: i
            });
        }
    }

    var analysis = analyzeOverlaps(highlights);
    renderAllHighlights(highlights, analysis);
    updateStatus(locators.length, analysis);
};
```

### 4. Overlap and duplicate detection

Two locators are **duplicates** if they resolve to the exact same DOM element (`el === el`).
Two locators **overlap** if their bounding rectangles intersect but are different elements.

```js
function analyzeOverlaps(highlights) {
    var duplicates = new Set();  // indices with duplicate targets
    var overlaps = new Set();    // indices with overlapping bounds

    for (var i = 0; i < highlights.length; i++) {
        for (var j = i + 1; j < highlights.length; j++) {
            if (highlights[i].el === highlights[j].el) {
                duplicates.add(i);
                duplicates.add(j);
            } else if (rectsOverlap(highlights[i].rect, highlights[j].rect)) {
                overlaps.add(i);
                overlaps.add(j);
            }
        }
    }
    return { duplicates: duplicates, overlaps: overlaps };
}
```

### 5. Visual design

| State | Border | Background | Badge |
|-------|--------|------------|-------|
| Normal (unique match) | 2px solid, cycled color | 20% opacity fill | Tooltip: locator type + value |
| Duplicate (same element) | 2px dashed `#ef4444` | 15% red | "DUPLICATE" badge |
| Overlap (intersecting) | 2px dashed `#eab308` | 15% yellow | "OVERLAP" badge |

**Color palette** for distinguishing locators:

```js
var PALETTE = [
    '#3b82f6', '#22c55e', '#a855f7', '#f97316',
    '#06b6d4', '#ec4899', '#84cc16', '#f43f5e'
];
```

Each locator gets `PALETTE[index % PALETTE.length]`. Duplicate/overlap borders override the palette color.

### 6. Interaction with caret highlight

- When "Show All" active + cursor moves to locator line: single highlight is suppressed
- Toggle off: clears all, resumes caret-based highlight
- `CaretHighlightListener` checks `service.isHighlightAllActive` before calling `highlightElement`

### Files to create/modify

| File | Change |
|------|--------|
| `resources/html/js/highlight.js` | Add `highlightAll`, `analyzeOverlaps`, `renderAllHighlights` |
| `actions/HighlightAllAction.kt` | **New** — collects all locators, triggers highlight-all |
| `services/SnapshotService.kt` | Add `highlightAllLocators()`, `isHighlightAllActive` flag |
| `PageMirrorToolWindowFactory.kt` | Add "Show All" button to toolbar |
| `listeners/CaretHighlightListener.kt` | Skip single-highlight when all-mode active |

### Acceptance criteria (8b)

- [x] "Show All" button appears in toolbar, toggles highlight-all mode
- [x] All locators from the current file highlighted simultaneously with distinct colors
- [x] Locators resolving to the same DOM element show red dashed border + "DUPLICATE" badge
- [x] Locators with intersecting bounds show yellow dashed border + "OVERLAP" badge
- [x] Status line: `Showing 8 locators | 1 duplicate | 2 overlaps`
- [x] Caret highlight suppressed while "Show All" active
- [x] Toggle off clears all, resumes normal caret behavior
- [ ] Unit tests for overlap detection logic (JS-only; not separately unit tested)
- [x] Integration test: file with known duplicates produces correct JS call
