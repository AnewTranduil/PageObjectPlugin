# Page Object Helper

An IntelliJ plugin that renders Playwright page snapshots inside a docked tool window, bridging the gap between test code and the actual UI. Place your cursor on a locator — the matching element highlights in the snapshot. Click an element — a locator is generated and inserted into your code.

## Features

- **Page Mirror panel** — displays captured page snapshots in an embedded browser (JCEF), docked to the right side of the IDE.
- **Code-to-UI highlight** — move your cursor to a Playwright locator and the corresponding element lights up in the snapshot.
- **Element picker** — click any element in the snapshot to generate a locator and insert it into your code (`Alt+Shift+I`).
- **Selector validation** — gutter badges show how many elements match each locator, catching ambiguous or broken selectors before you run tests.
- **Highlight All** — highlight every locator on the page at once with color-coded overlays and duplicate detection (`Alt+Shift+H`).
- **Auto-discovery** — snapshots reload automatically when files change on disk.
- **Configurable** — adjust snapshot search depth, highlight color, auto-reload behavior, and code generation style in Settings > Tools > Page Mirror.

## Getting Started

### 1. Capture snapshots

Install the companion npm package in your Playwright project:

```bash
npm install -D playwright-snapshot-saver
```

Add a snapshot capture call to your test or global setup:

```typescript
import { saveSnapshot } from 'playwright-snapshot-saver';

await saveSnapshot(page, 'login-page');
```

Run your Playwright tests — snapshots are saved to the `.snapshots/` directory.

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

The [`playwright-snapshot-saver`](packages/playwright-snapshot-saver/) package captures sanitized HTML snapshots from Playwright pages. It can be used as a programmatic API or as a Playwright reporter.

Key options:
- `group` — organize snapshots into subdirectories
- `screenshotFormat` — `png`, `jpeg`, or `webp`
- `manifest` — include metadata (URL, viewport, timestamp)
- `extraSelectors` / `excludeSelectors` — control which elements are captured
- `extraAttributes` — preserve additional HTML attributes in the snapshot

See the [package directory](packages/playwright-snapshot-saver/) for full API documentation.

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
