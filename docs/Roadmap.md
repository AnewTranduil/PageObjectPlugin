# PageObjectPlugin Roadmap

## Context

Tasks 0–15.5 plus 19 are complete: the plugin works end-to-end for Playwright + TypeScript, both `@pagemirror/snapshot-core` and `playwright-snapshot-saver` are published on npm and produce v2 snapshot bundles (`resources/` sidecars, framework-agnostic trace rendering), the settings UI is on Kotlin UI DSL v2, the UI test suite runs under a layered Page Object structure with polling/retry/trace-bundle diagnostics, CI aggregates unit + UI + Playwright results into a single `claude-summary.{json,md}` bundle consumed via `reports.artemon.cloud`, and PRs tagged `demo` auto-render a Playwright-style trace viewer. The plugin is still tightly coupled to TypeScript (regex-based locator extraction) and Playwright (`PlaywrightAdapter` + Playwright trace loader). The remaining roadmap tracks add Python / JVM language support (A1, A2) and Selenium / Cypress / Appium driver adapters that plug into the existing `PageAdapter` + `TraceBackend` interfaces in `@pagemirror/snapshot-core` (B2, B3).

This roadmap broadens language/framework reach and tightens the inner dev loop so future work scales. It is organized into three tracks (A, B, C) that can progress semi-independently. Each roadmap item corresponds to one or more task docs under `docs/tasks/`.

---

## Track A — Language Support

Each language task has **two halves**: (1) IDE-side locator extraction so highlight/gutter work, and (2) a snapshot-saver sibling package in the target language so users can actually produce `.snapshots/` bundles from their non-TS test runs. The existing npm packages cannot be consumed from Python/Java/Kotlin, so we ship native packages that reuse the v2 bundle layout (`index.html` + `manifest.json` + `resources/`) frozen in `docs/snapshot-bundle-spec.md`.

- **A1. Python Playwright support** — `task-16-python-playwright-support.md` (not started)
- **A2. Java/Kotlin Playwright support** — `task-18-jvm-playwright-support.md` (not started)

---

## Track B — Snapshot Saver Consolidation & Multi-Framework

- **B1. Extract framework-agnostic core** — `task-15-snapshot-core-extraction.md` — **done.** `@pagemirror/snapshot-core@0.1.0` is published, `playwright-snapshot-saver@0.7.0` depends on it. Trace rendering and resource inlining were also moved into core under Task 15.5 (`task-15.5-trace-resource-inlining.md`, done).
- **B2. Selenium + Cypress adapters** — `task-17-selenium-cypress-adapters.md` (not started)
- **B3. Mobile environment support (Appium)** — `task-20-appium-mobile-support.md` (not started)

B2 and B3 implement the existing `PageAdapter` + `TraceBackend` interfaces in `@pagemirror/snapshot-core`; no new abstractions are needed.

---

## Track C — Repo / Inner Loop Improvements

All three sub-tracks are **done**:

- **C1. UI test framework** — four subtasks (all done):
  - `task-13a-ui-tests-unblock.md` — fix `splitMode` + fast-fail health check
  - `task-13b-ui-tests-reliability.md` — polling helpers, retry-once, `@Quarantine`
  - `task-13c-ui-tests-diagnostics.md` — per-test trace bundle (feeds C3)
  - `task-13d-ui-tests-page-object-refactor.md` — locators/pages/flows layering
- **C2. CI test reporting + Claude loop** — `task-14-ci-test-reporting.md` — done.
- **C3. Feature demo reporting (Playwright-style trace viewer)** — `task-19-feature-demo-trace-viewer.md` — done.

---

## Suggested Execution Order

C1, C2, C3, and B1 (incl. 15.5) are done. Remaining order:

1. **A1** — Python Playwright (highest user demand for language expansion).
2. **B2** — Selenium/Cypress adapters.
3. **A2** — Java/Kotlin Playwright.
4. **B3** — mobile/Appium (largest unknowns).

---

## High-Level Verification

- **A1/A2**: new unit tests in `src/test/kotlin/.../locators/` pass; open a `.py`/`.java` file with locators and confirm gutter badges and caret-driven highlight in the tool window.
- **B1**: `npm test` in both `snapshot-core` and `playwright-snapshot-saver` passes; no regression in `test-project/` snapshots.
- **B2/B3**: each new package has its own integration test target green in CI.
- **C1**: `./gradlew runIdeForUiTests` boots; all 30 existing UI scenarios run; failing tests produce trace bundles; refactored tests contain zero literal XPaths.
- **C2**: `./gradlew testReport` produces `build/reports/claude-summary.{json,md}`; CI uploads them as artifacts.
- **C3**: `./gradlew demoReport -PfeatureName=<tag>` produces a self-contained trace viewer; PR with `demo` label auto-comments a link.

See each task doc for its own detailed verification.
