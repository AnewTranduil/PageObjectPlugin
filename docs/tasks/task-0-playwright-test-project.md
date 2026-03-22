# Task 0: Dummy Playwright Test Project

> **Goal:** Create a minimal Playwright project that produces real snapshot bundles for testing all IDE features.
> **Depends on:** Nothing
> **Output:** `test-project/` with `.snapshots/login/initial/` and `.snapshots/login/error-state/`

## Prompt

Create a minimal Playwright + TypeScript project in `./test-project/` that:

1. Has a single page object: `LoginPage` (`page-objects/login.page.ts`)
   - locators: `usernameInput`, `passwordInput`, `loginButton`, `errorMessage`
   - methods: `goto()`, `login(user, pass)`, `getError()`

2. Has one test: `login.spec.ts` that navigates to `https://the-internet.herokuapp.com/login` and tests valid + invalid login.

3. Has a utility: `utils/save-state.ts` that exports:
   ```ts
   async function saveState(
     page: Page,
     handle: string,
     snapshotDir: string,
     options?: { fullPage?: boolean }
   ): Promise<void>
   ```
   This function creates `snapshotDir/handle/` containing:
   - `index.html` — `page.content()` with all CSS inlined via a script
   - `screenshot.webp` — `page.screenshot({ type: 'webp' })`
   - `layout.json` — see schema below
   - `manifest.json` — url, viewport, timestamp, playwright version

4. `layout.json` schema:
   ```json
   {
     "version": 1,
     "viewport": { "width": number, "height": number },
     "elements": [
       {
         "selector": "<best unique CSS selector>",
         "role": "<ARIA role or null>",
         "text": "<visible text, trimmed to 80 chars>",
         "tag": "<tag name>",
         "bounds": { "x": number, "y": number, "w": number, "h": number },
         "interactive": boolean,
         "attributes": { "<key>": "<value>" }
       }
     ]
   }
   ```
   Elements to capture: all interactive elements (`button`, `input`, `select`, `textarea`, `a`, `[role=button]`, `[role=link]`, `[tabindex]`) plus any element with an `id` or `data-testid` attribute.

5. The test calls `saveState` at 2 points:
   - After navigation: `saveState(page, "initial", snapshotsDir)`
   - After failed login: `saveState(page, "error-state", snapshotsDir)`
   - `snapshotsDir = path.join(__dirname, '..', '.snapshots', 'login')`

6. Include `package.json`, `tsconfig.json`, `playwright.config.ts`.
   The project should run with: `cd test-project && npm install && npx playwright test`

Do NOT use any external snapshot libraries. Keep it minimal and explicit.

## Acceptance Criteria

- [x] Running the test creates `.snapshots/login/initial/` and `.snapshots/login/error-state/` each with all 4 files
- [x] `layout.json` contains at least 4 elements with correct bounds (non-zero width/height)
- [x] `index.html` renders in a browser and looks like the original page
- [x] `manifest.json` contains a valid URL and viewport dimensions

## Implementation Notes

**Status: COMPLETE** (merged to main via PR #1)

### Adaptations from original spec
- **Local fixture instead of external site:** Used a local `fixtures/login.html` served via `serve` on port 8089, since `the-internet.herokuapp.com` is not reachable from all environments. The fixture faithfully replicates the login page structure including `#username`, `#password`, `button[type="submit"]`, `#flash` error div, and `data-testid` attributes.
- **PNG screenshots instead of WebP:** Playwright 1.49.1 (pinned for browser compatibility) does not support `type: 'webp'`. Screenshots are saved as `screenshot.png`.
- **Playwright 1.49.1 pinned:** The environment had chromium build v1194 pre-installed; 1.49.1 was the compatible version.

### Files created
- `test-project/package.json` — deps: `@playwright/test@1.49.1`, `serve@^14.2.0`
- `test-project/tsconfig.json` — ES2020, commonjs, strict
- `test-project/playwright.config.ts` — chromium, 1280x720 viewport, local webServer on port 8089
- `test-project/fixtures/login.html` — local mock of the login page
- `test-project/page-objects/login.page.ts` — `LoginPage` with 4 locators, 3 methods
- `test-project/tests/login.spec.ts` — captures initial + error-state snapshots
- `test-project/utils/save-state.ts` — generates index.html (CSS inlined), layout.json, manifest.json, screenshot.png

### Verified output
- `.snapshots/login/initial/` — 4 files, 8 elements in layout.json with non-zero bounds
- `.snapshots/login/error-state/` — 4 files, includes `#flash.error` element
