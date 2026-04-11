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

## Test Loop

Tests run on **CI**, not locally. The plugin's test surface (Xvfb-driven
UI tests, npm/Playwright integration tests, JCEF, sandboxed IDE) is
expensive and platform-sensitive enough that the canonical "did my change
break anything?" signal is the CI run for your branch — not `./gradlew test`
in a developer terminal. The loop is:

1. Push your branch.
2. Wait for the `test-report` job in `.github/workflows/ci.yml` to
   complete. It depends on `unit-tests`, `ui-tests`, and `playwright-tests`
   and runs the buildSrc aggregator (`./gradlew aggregateTestReport`) on
   their combined raw output.
3. Read `claude-summary.md` from the `claude-summary` suite on
   `reports.artemon.cloud` (see "Report Dashboard Access" below for the
   token + read endpoints):

   ```bash
   BASE="${REPORT_DASHBOARD_URL:-https://reports.artemon.cloud}"
   AUTH="Authorization: Bearer $REPORT_DASHBOARD_TOKEN"
   RUN_ID=$(curl -sH "$AUTH" "$BASE/api/v1/external/runs" \
              | jq -r '.data[0].run_id')
   curl -sH "$AUTH" \
     "$BASE/api/v1/external/runs/$RUN_ID/claude-summary/claude-summary.md"
   ```

4. The Markdown lists every failing test with `file:line` and (for UI
   tests) the path to its trace bundle inside the same dashboard suite
   (`/api/v1/external/runs/$RUN_ID/claude-summary/traces/<Class>__<method>/`).
5. Fix locally. Push. Repeat from step 2.

The aggregated `claude-summary.json` schema is documented in
`docs/tasks/task-14-ci-test-reporting.md`. The same bundle is also
uploaded as the GitHub Actions artifact `test-report-<sha>` for ad-hoc
download, but the dashboard is the **primary** read path because it does
not require GitHub auth and survives across sessions.

`./gradlew testReport` exists as a developer-debugging side-tool that
runs every suite locally and produces the same `claude-summary.{json,md}`
under `build/reports/`. Use it when iterating on a single suite, but do
not treat its output as the source of truth — only the CI run for your
branch is authoritative.

**Redlines** (non-negotiable):

- **Never remove, disable, or `continue-on-error` the "Upload
  claude-summary bundle to reports.artemon.cloud" step in
  `.github/workflows/ci.yml`.** That upload is the main step of the
  Test Loop; if it stops running the loop is broken even when every
  test passes, because the remote dashboard endpoint is how this and
  any future Claude Code session sees CI results. If the upload step
  is flaky, fix the root cause — do not bypass it.
- **Never delete a failing test to turn CI green.**
- **Never add `@Ignore` / `@Disabled` without a ticket** linked in the
  same commit; bare skips are banned.
- **After a fix, always push and wait for the `test-report` CI job to
  complete** before declaring green. Never cherry-pick a single local
  test run as proof.
- **Never use `node -e` or inline scripts for verification** — add a
  real test (unit or integration).

## Workflow Rules

- **Never use `node -e` for ad-hoc verification.** Always create a proper test (unit or integration) instead of running inline scripts. Tests are reusable, documented, and run in CI.
- **No Claude attribution in git artifacts.** Do NOT add "Generated with Claude Code", "Co-Authored-By: Claude", `https://claude.ai/code` links, or any similar attribution to commit messages or PR descriptions. Keep commits and PRs clean of tool-identifying footers.

## Common Pitfalls

- **JCEF not rendering:** Confirm `JBCefApp.isSupported()` returns true. Test with `about:blank` first.
- **CSS bleed:** Always use `srcdoc` iframe. Never raw innerHTML injection.
- **JS bridge timing:** Create `JBCefJSQuery` before the page loads. Inject the callback name via `executeJavaScript` after load.
- **File watcher misses:** External changes (from Playwright) may not trigger `VirtualFileListener`. Call `VirtualFileManager.getInstance().refreshWithoutFileWatcher()`.
- **Stale gutter annotations:** Call `DaemonCodeAnalyzer.getInstance(project).restart()` after snapshot reload.
- **JCEF debugging:** Remote debugging available on port 9222.

## UI Test Conventions

UI tests live under `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/`
and run against a sandboxed IDE launched by `./gradlew runIdeForUiTests`.
The layout was overhauled in Task 13 — follow these rules.

- **Layering rule.** Tests call Flows or Pages, never raw fixtures.
  Flows orchestrate multiple Pages. Pages compose Fixtures and call
  `StepRecorder.step("...")` for each logical action so the action
  shows up in `trace.json`. Fixtures contain no literal XPath strings —
  every locator comes from `ui/locators/`.
- **Polling.** Use `ui.support.Wait.pollUntil` / `pollUntilTrue`. Never
  `Thread.sleep` in infrastructure code (BaseUiTest + fixtures + pages).
  A rare bounded sleep is allowed only with a `TODO(13b)` comment
  explaining why no completion signal exists.
- **Diagnostics.** Failures produce a trace bundle at
  `build/reports/uiTest/traces/<Class>__<method>/` with `trace.json`,
  `idea.log`, `dom.html`, `jcef-console.log`, `threads.txt`, and
  `screenshots/`. Pass `-PcaptureAllTraces=true` to also bundle passing
  tests.
- **Retry.** `RetryOnceExtension` re-runs a failing test method exactly
  once; the retry is surfaced via `publishReportEntry("flaky","true")`
  and the `flaky` field in `trace.json`. JUnit does NOT re-fire
  `@BeforeEach` / `@AfterEach` on retry, so per-test setup must be
  idempotent. Tests should always be in working shape — fix flakiness,
  do not hide it.
- **Reference tests.** `tests/ToolWindowUiTest.kt` is the canonical
  Page/Flow example for active tests. `tests/SettingsUiTest.kt` is the
  canonical example for tests that compose `SettingsChangeFlow`; it is
  currently `@Disabled` for unrelated reasons (UI DSL component
  wrapping) but kept as a structural reference.

## Report Dashboard Access

CI publishes Playwright report bundles to the `reportdashboard` service
(repo: `anewtranduil/reportdashboard`). Future Claude Code sessions can both
upload new reports and **read past reports** using the same bearer token.

- **Base URL**: `https://reports.artemon.cloud` (override via
  `REPORT_DASHBOARD_URL`).
- **Auth**: `Authorization: Bearer $REPORT_DASHBOARD_TOKEN`. The token is
  bound to a single project (this plugin's) — no slug needs to be passed.
  Never log or echo the token.
- **Whitelisted past Authentik** (see Traefik labels in
  `reportdashboard/docker-compose.yml`): only `/health`, `/api/v1/upload`,
  `/api/v1/executions/*`, and `/api/v1/external/*`. Any other path returns a
  302 to `auth.artemon.cloud` and must NOT be scripted.

### Upload (used by `.github/workflows/ci.yml`)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/upload` | Upload one suite's report bundle (`-F report=@bundle.zip` + `suite`, `run_id`, `run_url`, `job_url`, `git_ref`, `git_sha`) |
| POST | `/api/v1/executions/{run_id}/finalize` | Finalize a run after every suite has been uploaded |

### Read (use from a local shell or Claude session)

All responses are JSON `{"ok":true,"data":...}` except the file-serving route.

| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/external/project` | Project metadata (`slug`, `name`, `github_repo`, `max_executions`) |
| GET | `/api/v1/external/runs` | Array of executions, newest first, each with `run_id`, `status`, `git_ref`, `git_sha`, `run_url`, `suite_count` |
| GET | `/api/v1/external/runs/{run_id}` | Execution details + list of suites (`slug`, `name`, `job_url`, `size_bytes`, `file_count`) |
| GET | `/api/v1/external/runs/{run_id}/{suite}/{path...}` | Raw file from the report; a directory path falls back to `index.html` |

Example — find the latest run and download its rendered HTML report for the
`chromium` suite:

```bash
BASE="${REPORT_DASHBOARD_URL:-https://reports.artemon.cloud}"
AUTH="Authorization: Bearer $REPORT_DASHBOARD_TOKEN"

RUN_ID=$(curl -sH "$AUTH" "$BASE/api/v1/external/runs" \
           | jq -r '.data[0].run_id')

curl -sH "$AUTH" "$BASE/api/v1/external/runs/$RUN_ID" | jq .

curl -sH "$AUTH" \
  "$BASE/api/v1/external/runs/$RUN_ID/chromium/index.html" \
  -o report.html
```

### Troubleshooting

- **302 to `auth.artemon.cloud/application/o/authorize/...`** → the path is
  not in the Authentik whitelist. Only `/health`, `/api/v1/upload`,
  `/api/v1/executions/*`, and `/api/v1/external/*` bypass Authentik.
- **`{"ok":false,"error":"Missing API key"}`** → the bearer header was not
  sent or was rejected. Re-check `REPORT_DASHBOARD_TOKEN`.
- **404 on a file path** → the suite slug is case-sensitive and must match
  what CI uploaded; list suites via `/api/v1/external/runs/{run_id}` first.
