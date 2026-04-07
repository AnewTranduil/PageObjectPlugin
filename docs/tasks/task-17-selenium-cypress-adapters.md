# Task 17: Selenium + Cypress Snapshot Saver Adapters

> **Goal:** Ship two new npm packages, `selenium-snapshot-saver` and `cypress-snapshot-saver`, each a thin `PageAdapter` implementation over `snapshot-core` (Task 15). Enables Selenium/Cypress users to produce identical snapshot bundles.
> **Depends on:** Task 15 (`snapshot-core` extracted).
> **Output:** Two new packages under `packages/`, each with integration tests against a fixture page.

## Motivation

Task 15 built the `PageAdapter` abstraction specifically so new driver adapters become small. Selenium and Cypress are the other two dominant JS test frameworks — shipping adapters for them multiplies the plugin's reach without touching plugin code at all.

## Key Files

- `packages/snapshot-core/src/page-adapter.ts` — interface to implement.
- New: `packages/selenium-snapshot-saver/`
  - `src/index.ts` — `SeleniumAdapter implements PageAdapter`
  - `src/save.ts` — re-export `saveSnapshot` bound to the adapter
  - `tests/` — WebDriver integration test
- New: `packages/cypress-snapshot-saver/`
  - `src/index.ts` — `CypressAdapter` + Cypress task plugin (`cy.task('saveSnapshot', ...)`)
  - `tests/` — Cypress e2e fixture project

## Adapter Notes

### SeleniumAdapter
- Wraps `selenium-webdriver`'s `WebDriver`.
- `getHTML()` → `driver.getPageSource()`
- `getCSS()` → execute JS: walk `document.styleSheets`, serialize rules.
- `screenshot()` → `driver.takeScreenshot()` (returns base64 PNG) → optionally convert to WebP via `sharp`.
- `getViewport()` → `driver.manage().window().getRect()`
- `getURL()` / `getUserAgent()` → `driver.getCurrentUrl()` / `executeScript("return navigator.userAgent")`.

### CypressAdapter
- Cypress runs tests in-browser, so the adapter runs as a Node-side task plugin.
- Exposes a Cypress task (`cy.task('saveSnapshot', opts)`) that receives HTML + URL + viewport from a browser-side companion script and delegates to `snapshot-core`.
- Screenshot uses Cypress's `Cypress.screenshot()` output file.

## Steps

1. **Selenium package**:
   - Scaffold `packages/selenium-snapshot-saver/` (workspace member). Depend on `@pagemirror/snapshot-core` and peer-depend on `selenium-webdriver`.
   - Implement `SeleniumAdapter`.
   - Write a Mocha/Vitest integration test that launches ChromeDriver, opens a fixture HTML page, calls `saveSnapshot`, asserts bundle layout + manifest per `docs/snapshot-bundle-spec.md`.
   - README with usage example.
2. **Cypress package**:
   - Scaffold `packages/cypress-snapshot-saver/`. Depend on `@pagemirror/snapshot-core` and peer-depend on `cypress`.
   - Implement the Node task plugin + browser-side helper.
   - Integration test: a Cypress e2e spec that visits a fixture page and calls `cy.task('saveSnapshot', ...)`; assert bundle produced.
   - README with usage example.
3. Wire both packages into the Task 14 `testReport` aggregator so their test output shows up in `claude-summary.*`.

## Verification

- Both packages build and their integration tests pass locally and in CI.
- Bundles produced by Selenium and Cypress adapters load in the plugin tool window identically to Playwright bundles.
- Manifest declares the correct driver (`selenium` / `cypress`) per the spec.

## Out of Scope

- Bundling a Cypress component-test adapter (only e2e mode for now).
- Publishing to npm registry.
- Support for Selenium 3 (target Selenium 4+).
