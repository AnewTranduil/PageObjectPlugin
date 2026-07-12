# PageObjectPlugin Roadmap

## Context

Tasks 0–15 (+15.5) plus 19 are complete: the plugin works end-to-end for Playwright + TypeScript, the `playwright-snapshot-saver` npm package ships snapshots from real Playwright runs, the framework-agnostic `@pagemirror/snapshot-core` package is extracted and consumed via semver, the settings UI is on Kotlin UI DSL v2, the UI test suite runs under a layered Page Object structure with polling/retry/trace-bundle diagnostics, CI aggregates unit + UI + Playwright results into a single `claude-summary.{json,md}` bundle consumed via `reports.artemon.cloud`, and PRs tagged `demo` auto-render a Playwright-style trace viewer. The current architecture is still tightly coupled to TypeScript (regex-based locator extraction) on the IDE side. Track A extends to Python / JVM locator support; Tracks B2/B3 add Selenium / Cypress / Appium adapters on top of `@pagemirror/snapshot-core`.

This roadmap broadens language/framework reach and tightens the inner dev loop so future work scales. It is organized into three tracks (A, B, C) that can progress semi-independently. Each roadmap item corresponds to one or more task docs under `docs/tasks/`.

---

## Track A — Language Support

Each language task has **two halves**: (1) IDE-side locator extraction so highlight/gutter work, and (2) a snapshot-saver sibling package in the target language so users can actually produce `.snapshots/` bundles from their non-TS test runs. The existing npm package cannot be consumed from Python/Java/Kotlin, so we ship native packages that reuse the same on-disk bundle format (`index.html` + `screenshot.webp` + `manifest.json`) defined in `CLAUDE.md` and frozen in `docs/snapshot-bundle-spec.md`.

- **A1. Python Playwright support** — `task-16-python-playwright-support.md`
- **A2. Java/Kotlin Playwright support** — `task-18-jvm-playwright-support.md`

Shared prerequisite: freeze `docs/snapshot-bundle-spec.md` before A1/A2 implementation so the three language savers (TS/Python/JVM) stay in lockstep.

---

## Track B — Snapshot Saver Consolidation & Multi-Framework

- **B1. Extract framework-agnostic core** — `task-15-snapshot-core-extraction.md` ✅ done
- **B2. Selenium + Cypress adapters** — `task-17-selenium-cypress-adapters.md`
- **B3. Mobile environment support (Appium)** — `task-20-appium-mobile-support.md`

B1 shipped as `@pagemirror/snapshot-core@0.1.0`; B2 and B3 layer new adapters on top of the extracted core.

---

## Track C — Repo / Inner Loop Improvements

- **C1. UI test framework** — split into four subtasks:
  - `task-13a-ui-tests-unblock.md` — fix `splitMode` + fast-fail health check
  - `task-13b-ui-tests-reliability.md` — polling helpers, retry-once, `@Quarantine`
  - `task-13c-ui-tests-diagnostics.md` — per-test trace bundle (feeds C3)
  - `task-13d-ui-tests-page-object-refactor.md` — locators/pages/flows layering
- **C2. CI test reporting + Claude loop** — `task-14-ci-test-reporting.md`
- **C3. Feature demo reporting (Playwright-style trace viewer)** — `task-19-feature-demo-trace-viewer.md`

---

## Suggested Execution Order

1. **C1a** — unblock UI tests (everything else benefits).
2. **C1b–d** — reliability, diagnostics, page-object refactor.
3. **C2** — get the Claude inner loop solid before adding scope.
4. **B1** — refactor snapshot core (low risk, enables B2/B3).
5. **A1** — Python Playwright (highest user demand for language expansion).
6. **B2** — Selenium/Cypress adapters.
7. **A2** — Java/Kotlin Playwright.
8. **C3** — demo reporting (depends on C1c trace format + C2 stable).
9. **B3** — mobile/Appium (largest unknowns).

---

## High-Level Verification

- **A1/A2**: new unit tests in `src/test/kotlin/.../locators/` pass; open a `.py`/`.java` file with locators and confirm gutter badges and caret-driven highlight in the tool window.
- **B1**: `npm test` in both `snapshot-core` and `playwright-snapshot-saver` passes; no regression in `test-project/` snapshots.
- **B2/B3**: each new package has its own integration test target green in CI.
- **C1**: `./gradlew runIdeForUiTests` boots; all 30 existing UI scenarios run; failing tests produce trace bundles; refactored tests contain zero literal XPaths.
- **C2**: `./gradlew testReport` produces `build/reports/claude-summary.{json,md}`; CI uploads them as artifacts.
- **C3**: `./gradlew demoReport -PfeatureName=<tag>` produces a self-contained trace viewer; PR with `demo` label auto-comments a link.

See each task doc for its own detailed verification.
