# Task 13c: UI Tests — Per-Test Diagnostics Trace Bundle

> **Status:** DONE — `ui/support/TraceBundleExtension.kt`,
> `TraceBundle.kt`, `StepRecorder.kt`, `TraceIndexGenerator.kt`, and
> `CdpConsoleCollector.kt` capture per-test bundles. `trace.json` is
> emitted on failure (and on success when `-PcaptureAllTraces=true`).
> The planned `ScreenshotOnFailureExtension` was never checked in — the
> replacement shipped without a preceding version, so the "delete
> `ScreenshotOnFailureExtension`" step below never had a file to delete.
> Paths in "Key Files" and "Steps" below reference the pre-13d layout;
> actual files live under
> `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/…`.
>
> **Goal:** On UI test failure, capture a structured bundle of artifacts (IDE log, tool-window DOM, JCEF console, screenshot timeline, thread dump) with a machine-readable `trace.json` manifest. This bundle is the direct input to the Task 19 (C3) Playwright-style trace viewer.
> **Depends on:** Task 13a (unblock), Task 13b (polling — for step recording)
> **Output:** `TraceBundleExtension` — one bundle per failing test under `build/reports/uiTest/traces/<test>/`.

## Motivation

`ScreenshotOnFailureExtension` captures a single PNG on failure and nothing else. When a UI test breaks in CI, debugging requires guessing: was the IDE frozen? Did the tool window's JCEF DOM render? Did a JS error fire in the snapshot iframe? Was there a deadlock? Right now none of this is visible.

We want a single extension that, on failure, writes a self-contained directory per test with every signal a human or Claude needs to diagnose it — and crucially, that directory's layout and `trace.json` schema is designed up-front to match what Task 19's trace viewer will consume. C3 then becomes a filter + rendering layer over the same artifacts instead of a parallel capture system.

## Key Files

- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/extensions/ScreenshotOnFailureExtension.kt:18-50` — replace with `TraceBundleExtension`.
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/SnapshotBrowserFixture.kt` — reuse existing `callJs` path to dump tool-window DOM.
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/BaseUiTest.kt` — register `TraceBundleExtension` globally.
- New: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/TraceBundleExtension.kt`
- New: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/StepRecorder.kt`

## Bundle Layout

```
build/reports/uiTest/traces/<ClassName>__<testName>/
  trace.json           # Manifest listing all artifacts + step timeline
  idea.log             # Tail of IDE's idea.log
  dom.html             # Tool-window JCEF DOM snapshot
  jcef-console.log     # Captured console.log/warn/error from JCEF (port 9222 CDP)
  threads.txt          # Thread dump of the IDE process
  screenshots/
    001-<step>.png
    002-<step>.png
    ...
```

## `trace.json` Schema (v1)

```json
{
  "version": 1,
  "test": { "className": "...", "method": "...", "displayName": "...", "feature": "highlight-all" },
  "startedAt": "2026-04-07T12:00:00Z",
  "durationMs": 1234,
  "status": "failed",
  "failure": { "message": "...", "stack": "...", "file": "...", "line": 42 },
  "steps": [
    { "index": 1, "label": "open settings", "at": "...", "screenshot": "screenshots/001-open-settings.png" }
  ],
  "artifacts": {
    "ideaLog": "idea.log",
    "dom": "dom.html",
    "jcefConsole": "jcef-console.log",
    "threads": "threads.txt"
  }
}
```

This schema is the contract with Task 19.

## Steps

1. **StepRecorder** — a thread-local collector. Pages/Flows from Task 13d will call `StepRecorder.step("label") { ... }`, which captures a timestamped entry and a screenshot. Until 13d lands, `BaseUiTest` exposes `step(label)` as a thin wrapper.
2. **JCEF console capture** — enable remote debug on port 9222 (already documented in `CLAUDE.md` pitfalls) and open a CDP client that tees `Runtime.consoleAPICalled` events to a list. Flush to `jcef-console.log` on test end.
3. **DOM dump** — reuse the existing `callJs` helper in `SnapshotBrowserFixture` to run `document.documentElement.outerHTML` and write to `dom.html`.
4. **IDE log tail** — copy the last N KB of `idea.log` from the sandbox (`runIdeForUiTests` working dir).
5. **Thread dump** — invoke `jstack` against the IDE PID if available; otherwise fall back to `Thread.getAllStackTraces()` from inside the IDE via Remote Robot `callJs`.
6. **TraceBundleExtension** — implements `TestWatcher` + `AfterTestExecutionCallback`. On **failure OR** (when `-PcaptureAllTraces=true`) **any** result, assemble the bundle directory and write `trace.json`.
7. Delete `ScreenshotOnFailureExtension` (superseded).
8. Register the new extension in `BaseUiTest` so all UI tests inherit it.

## Verification

- Running a deliberately-failing UI test produces a directory at `build/reports/uiTest/traces/<name>/` containing all files listed in the layout above.
- `trace.json` validates against the v1 schema and can be round-tripped via `kotlinx.serialization`.
- With `-PcaptureAllTraces=true`, passing tests also produce bundles (needed by Task 19).
- CI artifact upload (Task 14) picks up the `traces/` directory.

## Out of Scope

- Rendering the trace viewer UI (→ Task 19).
- Parallel test execution across shards.
- Page-object refactor that adds `step(...)` call sites (→ 13d).
