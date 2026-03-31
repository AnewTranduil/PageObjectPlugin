# Page Mirror — IntelliJ Plugin for Playwright

## What This Project Is

An IntelliJ plugin that renders Playwright page snapshots inside a docked Tool Window (JCEF browser). When a developer places their cursor on a Playwright locator in their TypeScript code, the corresponding element highlights in the snapshot view. Developers can also click elements in the snapshot to generate locator code.

## Project Configuration

| Parameter       | Value                                      |
|-----------------|--------------------------------------------|
| Plugin SDK      | IntelliJ Platform Gradle Plugin 2.x        |
| Language         | Kotlin (no Java)                           |
| Target IDEs     | IntelliJ Community + Ultimate + WebStorm   |
| Min Platform    | 2024.3+                                    |
| Plugin ID       | `com.example.pagemirror`                   |
| UI Location     | Tool Window, right panel, anchor=right     |
| JCEF            | Guaranteed available (bundled in 2024.3+)  |

## Critical Constraints

- **No JS/TS module dependency.** The plugin must NOT declare `com.intellij.modules.javascript` or any WebStorm-specific module in `plugin.xml`. It must load in IntelliJ Community. All TypeScript parsing is done via regex, not PSI.
- **JCEF only.** No Swing-based HTML rendering. Always check `JBCefApp.isSupported()` with a fallback label.
- **Snapshot HTML in iframe.** Always render snapshot HTML via `srcdoc` iframe inside the JCEF page. Never inject raw snapshot HTML into the tool window DOM (CSS bleed).
- **JBCefJSQuery for communication.** Use `JBCefJSQuery` for Kotlin↔JS messaging. Do NOT use `CefRequestHandler` or custom scheme handlers.
- **Jsoup for server-side DOM queries.** Gutter validation runs `querySelectorAll` via Jsoup on the Kotlin side, not in JCEF (too slow for real-time annotation).

## Snapshot Bundle Format

Each snapshot is a directory containing:

```
<snapshot-name>/
  index.html       # Sanitized DOM with CSS inlined (REQUIRED)
  screenshot.webp  # Visual reference (optional)
  manifest.json    # Metadata (optional)
```

### manifest.json Schema

```json
{
  "version": 1,
  "url": "https://example.com/login",
  "viewport": { "width": 1280, "height": 720 },
  "timestamp": "2025-01-15T10:30:00Z",
  "playwright": "1.48.0",
  "userAgent": "Mozilla/5.0 ..."
}
```

## Plugin Source Layout

```
src/main/
  kotlin/com/example/pagemirror/
    PageMirrorToolWindowFactory.kt
    model/
      SnapshotBundle.kt
    services/
      SnapshotService.kt
    listeners/
      SnapshotDiscoveryListener.kt
      SnapshotWatcher.kt
      CaretHighlightListener.kt
    locators/
      LocatorExtractor.kt
      PickerResultHandler.kt
    actions/
      LoadSnapshotAction.kt
      InsertLocatorAction.kt
      ToggleInspectAction.kt
    annotators/
      SelectorValidationAnnotator.kt
    settings/
      PageMirrorSettings.kt
      PageMirrorConfigurable.kt
  resources/
    META-INF/plugin.xml
    html/
      page-mirror.html
      js/
        snapshot.js
        query.js
        highlight.js
        inspect.js
        theme.js
```

## Test Project Layout

```
test-project/
  package.json
  playwright.config.ts
  page-objects/login.page.ts
  tests/login.spec.ts
  utils/save-state.ts
  .snapshots/login/
    initial/      {index.html, screenshot.webp, manifest.json}
    error-state/  {index.html, screenshot.webp, manifest.json}
```

## Task Sequence

Tasks MUST be completed in order. Each task is in `docs/tasks/`.

| #  | Task                              | Key Output                        | Depends On |
|----|-----------------------------------|-----------------------------------|------------|
| 0  | Dummy Playwright test project     | `.snapshots/` with real data      | Nothing    |
| 1  | Plugin shell + JCEF Tool Window   | Tool Window renders static HTML   | Nothing    |
| 2  | Snapshot loading via CefQuery     | HTML renders in iframe + highlight| 0, 1       |
| 3  | File watcher + auto-discovery     | Auto-loads snapshots on file open | 2          |
| 4  | Code-to-UI highlight bridge       | Cursor on locator → highlight     | 3          |
| 5  | Element picker + code generation  | Click element → insert locator    | 4          |
| 6  | Live selector validation (gutter) | Match count badges in editor      | 4          |
| 7  | Refinements and polish            | Settings, shortcuts, themes       | 5, 6       |
| 8  | Highlight all + duplicates        | Show All button, overlap detection| 4, 6       |
| 9  | Snapshot saver npm package        | Standalone `playwright-snapshot-saver`             | 0 |

## Current State

**Tasks 0–9 are complete.** All plugin features and the snapshot saver npm package are implemented. Unit/integration tests pass.

- **Task 8a (JS refactor):** Complete. JS split into 5 modules under `resources/html/js/`, assembled at runtime by `PageMirrorToolWindowFactory.assemblePageMirrorHtml()`.
- **Task 8b (Highlight All):** Complete. "Show All" toolbar button, color-coded multi-highlight, duplicate/overlap detection with visual badges, caret suppression, integration tests. One gap: JS-only overlap detection logic has no standalone unit test.
- **Task 9 (Snapshot saver npm package):** Complete. `packages/snapshot-saver/` with `saveSnapshot()` API, configurable options (group, screenshot format, manifest toggle, extraSelectors, excludeSelectors, extraAttributes). 10 Playwright integration tests pass. `test-project/` migrated to use the package via `file:` dependency.

**UI tests (30 scenarios) are BLOCKED** — see `docs/UI_tests/diagnostic-report.md` for full details. Summary:
- `./gradlew runIdeForUiTests` fails due to missing `splitMode` property (IPG 2.13.1 requirement) and configuration cache incompatibility
- The custom `RunIdeTask` registration at `build.gradle.kts:108` needs `splitMode.set(false)` and `splitModeTarget` configured
- `BaseUiTest` needs a fast-fail health check instead of a 2-minute blind timeout
- CI/CD pipeline is not yet configured

## Working with the Build

When investigating Gradle plugin APIs or build tooling, prefer reading project docs and running `./gradlew` commands (`help --task`, `dependencies`, `buildEnvironment`, etc.) over exploring files outside the project directory (e.g., `.gradle/caches/`, `.intellijPlatform/`). Stay within the project boundary.

## Workflow Rules

- **Never use `node -e` for ad-hoc verification.** Always create a proper test (unit or integration) instead of running inline scripts. Tests are reusable, documented, and run in CI.

## Common Pitfalls

- **JCEF not rendering:** Confirm `JBCefApp.isSupported()` returns true. Test with `about:blank` first.
- **CSS bleed:** Always use `srcdoc` iframe. Never raw innerHTML injection.
- **JS bridge timing:** Create `JBCefJSQuery` before the page loads. Inject the callback name via `executeJavaScript` after load.
- **File watcher misses:** External changes (from Playwright) may not trigger `VirtualFileListener`. Call `VirtualFileManager.getInstance().refreshWithoutFileWatcher()`.
- **Stale gutter annotations:** Call `DaemonCodeAnalyzer.getInstance(project).restart()` after snapshot reload.
- **JCEF debugging:** Remote debugging available on port 9222.
