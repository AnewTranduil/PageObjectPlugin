# Task 2: Snapshot Loading via CefQueryRouter

> **Status:** DONE — bundle model has since evolved:
> - `SnapshotBundle` (`src/main/kotlin/.../model/SnapshotBundle.kt`)
>   drops `layoutPath` and adds `dir` + `resourcesDir`; the shipped
>   fields are `name`, `dir`, `htmlPath`, `resourcesDir`,
>   `screenshotPath`, `manifestPath`.
> - `layout.json` was removed in Task 15 (v2 bundle format); loading
>   now inlines sidecar CSS via `SnapshotHtmlResolver` rather than
>   passing a JSON string to JS.
> - Bundle version validation runs against `SUPPORTED_BUNDLE_VERSION = 2`
>   (top-level `const val` in `SnapshotBundle.kt`); unknown versions
>   are refused via `BundleLoadResult`.
>
> **Goal:** Establish the Kotlin-to-JCEF communication bridge. Push snapshot data into the browser and render it.
> **Depends on:** Task 0 (for test data), Task 1 (for plugin shell)
> **Output:** Menu action that loads a snapshot folder and renders it in the Tool Window

## Prompt

Extend the Page Mirror IntelliJ plugin (Kotlin, 2024.3+, JCEF Tool Window already working from Task 1).

Add snapshot loading capability:

1. **SnapshotBundle.kt** (data class):
   ```kotlin
   data class SnapshotBundle(
     val name: String,
     val htmlPath: Path,
     val layoutPath: Path,
     val screenshotPath: Path?,
     val manifestPath: Path?
   )
   ```
   Add a companion factory: `fromDirectory(dir: Path): SnapshotBundle?` that validates required files exist (`index.html` and `layout.json` are required, others optional).

2. **SnapshotService.kt:**
   - A project-level service registered in `plugin.xml`
   - Method: `loadSnapshot(bundle: SnapshotBundle)` that reads the HTML and `layout.json`, then pushes them into JCEF
   - Uses `JBCefJSQuery` for Kotlin → JS communication
   - Calls `window.loadSnapshot(htmlString, layoutJsonString)` in the browser via `browser.cefBrowser.executeJavaScript()`

3. **Update page-mirror.html:**
   - `window.loadSnapshot(html, layoutJson)`:
     - Set `#viewport` innerHTML to an iframe with `srcdoc` set to the html (sandboxed to avoid CSS bleed)
     - Parse `layoutJson` and store the element map in memory
     - Show a status bar: "Loaded: {name} | {elementCount} elements"
   - `window.highlightElement(selector)`:
     - Look up the selector in the stored layout map
     - Draw a semi-transparent blue box (`rgba(59, 130, 246, 0.3)`) over the element in `#overlay`
     - Position the box using the `bounds` from `layout.json`
     - If selector not found, show a red "not found" indicator
   - `window.clearHighlight()`:
     - Remove all highlight boxes from `#overlay`

4. **LoadSnapshotAction.kt** — a temporary action (Tools menu): "Load Snapshot Directory..." that opens a file chooser, calls `SnapshotBundle.fromDirectory()`, then `SnapshotService.loadSnapshot()`.

**Communication pattern:** Use `JBCefJSQuery` to create a bridge, and `browser.cefBrowser.executeJavaScript()` to call JS functions. Do NOT use `CefRequestHandler` or custom scheme handlers.

**Important:** HTML string must be JSON-escaped before passing to `executeJavaScript()` to avoid quote/newline issues.

Test with the snapshot files from `./test-project/.snapshots/login/initial/`.

## Acceptance Criteria

- [x] Tools > Load Snapshot Directory > select a snapshot folder > the page renders in the Tool Window inside an iframe
- [x] Status bar shows element count matching `layout.json`
- [x] Calling `highlightElement` from the Kotlin side draws a visible blue box at correct coordinates
- [x] No CSS bleed between the snapshot HTML and the plugin chrome
- [x] Highlight box disappears when `clearHighlight` is called

**Status: COMPLETE** (merged to main via PR #3)
