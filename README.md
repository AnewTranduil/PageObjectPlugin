# Page Object Helper

An IntelliJ plugin that renders Playwright page snapshots inside a docked tool window, bridging the gap between test code and the actual UI. Place your cursor on a locator — the matching element highlights in the snapshot. Click an element — a locator is generated and inserted into your code.

## Features

- **Page Mirror panel** — displays captured page snapshots in an embedded browser (JCEF), docked to the right side of the IDE.
- **Code-to-UI highlight** — move your cursor to a Playwright locator and the corresponding element lights up in the snapshot.
- **Element picker** — click any element in the snapshot to generate a locator and insert it into your code (`Alt+Shift+I`).
- **Selector validation** — gutter badges show how many elements match each locator, catching ambiguous or broken selectors before you run tests.
- **Highlight All** — click the **Show All** button in the Page Mirror toolbar to highlight every locator from the current file at once with color-coded overlays and duplicate detection. `Alt+Shift+H` highlights just the locator under the caret.
- **Auto-discovery** — snapshots reload automatically when files change on disk.
- **Configurable** — adjust snapshot search depth, highlight color, auto-reload behavior, and code generation style in Settings > Tools > Page Mirror.

## Getting Started

### 1. Capture snapshots

Install the companion npm package in your Playwright project:

```bash
npm install -D playwright-snapshot-saver
```

The recommended workflow is the marker + reporter. Enable tracing and register the reporter in `playwright.config.ts`:

```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  use: { trace: 'on' },
  reporter: [
    ['html'],
    ['playwright-snapshot-saver/reporter', { outputDir: '.snapshots' }],
  ],
});
```

Then mark snapshot points inside your tests:

```typescript
import { test } from '@playwright/test';
import { snapshot } from 'playwright-snapshot-saver';

test('login page', async ({ page }) => {
  await page.goto('/login');
  await snapshot({ page: 'login', state: 'initial' });
});
```

Run your Playwright tests — snapshots are extracted from traces into `.snapshots/<page>/<state>/` after the run finishes. See the [package README](packages/playwright-snapshot-saver/README.md) for the direct `saveSnapshot()` API and trace-extraction CLI.

### 2. Use in the IDE

Open your project in IntelliJ IDEA or WebStorm. The **Page Mirror** tool window appears on the right panel. Open a test file — snapshots are discovered and loaded automatically.

## Use Cases

- **Writing locators without switching to a browser** — see the page right next to your code. Click elements to generate locators instead of manually inspecting the DOM.
- **Validating selectors during development** — gutter badges tell you if a locator matches 0, 1, or multiple elements, so you fix issues before running the test suite.
- **Reviewing page objects** — scroll through locators in a page object file and see each one highlighted on the snapshot. Spot stale or overlapping selectors instantly.
- **Onboarding new team members** — new contributors can visually explore what each locator targets without running the full test suite or navigating the app manually.
- **Debugging test failures** — compare the snapshot your test captured against the locators in code to see why a selector stopped matching.
- **Maintaining large test suites** — use Highlight All to see every locator on a page at once, identify duplicates, and detect overlap between page objects.

## Snapshot Saver (npm package)

The [`playwright-snapshot-saver`](packages/playwright-snapshot-saver/) package captures sanitized HTML snapshots from Playwright pages in the v2 bundle format the plugin consumes. It exposes a Playwright reporter, a direct `saveSnapshot()` API, and an `extract` CLI that pulls snapshots from existing Playwright HTML reports or trace ZIPs.

Key options:
- `group` — organize snapshots into subdirectories
- `screenshot` — `{ format: 'png' | 'webp', fullPage: boolean }` or `false` to disable
- `manifest` — include metadata (URL, viewport, timestamp, driver version)
- `extraSelectors` / `excludeSelectors` — control which elements are captured
- `extraAttributes` — preserve additional HTML attributes in the snapshot

The saver is a thin adapter on top of [`@pagemirror/snapshot-core`](packages/snapshot-core/), the framework-agnostic engine that owns HTML assembly, manifest building, and trace rendering. Selenium / Cypress / Appium adapters on the roadmap will reuse it.

See the [saver README](packages/playwright-snapshot-saver/README.md) and [snapshot-core README](packages/snapshot-core/README.md) for full API documentation.

## Compatibility

| IDE | Version |
|-----|---------|
| IntelliJ IDEA Community | 2024.3+ |
| IntelliJ IDEA Ultimate | 2024.3+ |
| WebStorm | 2024.3+ |

## Roadmap

- Selenium support
- Cypress support

## License

The IntelliJ plugin is licensed under [Apache License 2.0](LICENSE).

The `playwright-snapshot-saver` npm package is licensed under [MIT](packages/playwright-snapshot-saver/LICENSE).
