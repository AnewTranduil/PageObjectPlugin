# PageObjectPlugin Roadmap

## Context

Tasks 0–15, 15.5, and 19 are complete and shipped on `main`: the
plugin works end-to-end for Playwright + TypeScript, the
`playwright-snapshot-saver` npm package ships snapshots from real
Playwright runs in the v2 bundle format, the framework-agnostic
`@pagemirror/snapshot-core` package owns HTML assembly + manifest
building + trace rendering (so future Selenium / Cypress / Appium /
Python / JVM adapters can plug in via `PageAdapter` and `TraceBackend`
without duplicating bundle logic), the settings UI is on Kotlin UI
DSL v2, the UI test suite runs under a layered Page Object structure
with polling / retry / trace-bundle diagnostics, CI aggregates unit +
UI + Playwright results into a single `claude-summary.{json,md}`
bundle consumed via `reports.artemon.cloud`, and PRs tagged `demo`
auto-render a Playwright-style trace viewer. The remaining
architectural coupling is on the IDE side — locator extraction is
regex-based and currently scoped to TypeScript, so Tasks 16 (Python),
17 (Selenium/Cypress), 18 (JVM), and 20 (Appium / mobile) layer
language- and driver-specific adapters on top of the now-stable core.

This roadmap broadens language/framework reach and tightens the inner dev loop so future work scales. It is organized into three tracks (A, B, C) that can progress semi-independently. Each roadmap item corresponds to one or more task docs under `docs/tasks/`.

---

## Track A — Language Support

Each language task has **two halves**: (1) IDE-side locator extraction so highlight/gutter work, and (2) a snapshot-saver sibling package in the target language so users can actually produce `.snapshots/` bundles from their non-TS test runs. The existing npm package cannot be consumed from Python/Java/Kotlin, so we ship native packages that reuse the same on-disk bundle format (`index.html` + `screenshot.webp` + `manifest.json`) defined in `CLAUDE.md` and frozen in `docs/snapshot-bundle-spec.md`.

- **A1. Python Playwright support** — `task-16-python-playwright-support.md`
- **A2. Java/Kotlin Playwright support** — `task-18-jvm-playwright-support.md`

Shared prerequisite: freeze `docs/snapshot-bundle-spec.md` before A1/A2 implementation so the three language savers (TS/Python/JVM) stay in lockstep.

---

## Track B — Snapshot Saver Consolidation & Multi-Framework

- **B1. Extract framework-agnostic core** — `task-15-snapshot-core-extraction.md` ✅ shipped (plus Task 15.5: trace rendering + resource inlining moved into `@pagemirror/snapshot-core`).
- **B2. Selenium + Cypress adapters** — `task-17-selenium-cypress-adapters.md`
- **B3. Mobile environment support (Appium)** — `task-20-appium-mobile-support.md`

B1 is a pure refactor; B2 and B3 layer new adapters on top of the extracted core.

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

C1, C2, B1, and C3 are done and shipped on `main`. The remaining
language- and driver-adapter tracks can proceed in roughly this order:

1. **A1** — Python Playwright (highest user demand for language expansion).
2. **B2** — Selenium/Cypress adapters.
3. **A2** — Java/Kotlin Playwright.
4. **B3** — mobile/Appium (largest unknowns).

---

## High-Level Verification

- **A1/A2**: new unit tests in `src/test/kotlin/.../locators/` pass; open a `.py`/`.java` file with locators and confirm gutter badges and caret-driven highlight in the tool window.
- **B1** *(shipped)*: `npm test` in both `snapshot-core` and `playwright-snapshot-saver` passes; no regression in `packages/test-project/` snapshots.
- **B2/B3**: each new package has its own integration test target green in CI.
- **C1** *(shipped)*: `./gradlew runIdeForUiTests` boots; existing UI scenarios run; failing tests produce trace bundles; refactored tests contain zero literal XPaths.
- **C2** *(shipped)*: `./gradlew testReport` produces `build/reports/claude-summary.{json,md}`; CI uploads them as artifacts and to `reports.artemon.cloud`.
- **C3** *(shipped)*: `./gradlew demoReport -PfeatureName=<tag>` produces a self-contained trace viewer; PR with `demo` label auto-comments a link.

See each task doc for its own detailed verification.
