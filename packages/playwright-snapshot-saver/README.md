# playwright-snapshot-saver

Capture Playwright page snapshots (sanitized HTML, screenshots, metadata) into the Page Mirror v2 bundle format consumed by the [Page Mirror IntelliJ plugin](https://github.com/AnewTranduil/PageObjectPlugin).

This package is a thin Playwright adapter on top of [`@pagemirror/snapshot-core`](../snapshot-core), which owns the actual HTML assembly, manifest building, and trace rendering. Selenium / Cypress / Appium adapters are planned and will produce the same on-disk format.

## Installation

```bash
npm install playwright-snapshot-saver
```

## Usage

Three ways to capture snapshots, from simplest to most flexible.

### 1. Marker + reporter (recommended)

Mark snapshot points in your tests with `snapshot()`, then let the reporter extract them from Playwright traces after the run finishes.

**playwright.config.ts**

```ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  use: {
    trace: 'on', // required — the reporter reads trace ZIPs
  },
  reporter: [
    ['html'],
    ['playwright-snapshot-saver/reporter', { outputDir: '.snapshots' }],
  ],
});
```

**tests/login.spec.ts**

```ts
import { test, expect } from '@playwright/test';
import { snapshot } from 'playwright-snapshot-saver';

test('login page', async ({ page }) => {
  await page.goto('/login');
  await snapshot({ page: 'login', state: 'initial' });

  await page.fill('#email', 'bad');
  await page.click('button[type="submit"]');
  await snapshot({ page: 'login', state: 'error' });
});
```

After `npx playwright test`, the reporter extracts:

```
.snapshots/
  login/
    initial/  { index.html, manifest.json, resources/… }
    error/    { index.html, manifest.json, resources/… }
```

#### Reporter options

| Option       | Default        | Description                                         |
|--------------|----------------|-----------------------------------------------------|
| `outputDir`  | `'.snapshots'` | Base directory for extracted bundles                 |
| `screenshot` | `false`        | Extract a screencast frame as `resources/screenshot.webp`. Off by default since trace screencast frames are low-fidelity and frequently blank. |
| `manifest`   | `true`         | Generate `manifest.json`                            |

> **Changed in 0.6.0:** screenshot extraction is opt-in. Pass `screenshot: true` to re-enable it.

### 2. Direct API (`saveSnapshot`)

Call `saveSnapshot()` with a live Playwright `Page` to capture a snapshot immediately during test execution. This path uses Playwright's real `page.screenshot()`, so screenshots are always sharp.

```ts
import { test } from '@playwright/test';
import { saveSnapshot } from 'playwright-snapshot-saver';

test('capture snapshot', async ({ page }) => {
  await page.goto('https://example.com');

  const result = await saveSnapshot(page, {
    outputDir: '.snapshots',
    name: 'initial',
    group: 'example',                     // optional parent directory
    screenshot: { fullPage: true },       // or `false` to skip, or omit for defaults
    manifest: true,
  });

  console.log(result.outputDir);         // .snapshots/example/initial
  console.log(result.files.html);         // .snapshots/example/initial/index.html
  console.log(result.files.resources);    // [ …/resources/<sha1>.css, …/resources/screenshot.png ]
});
```

#### `SaveSnapshotOptions`

| Option              | Type                                 | Default                              | Description                                            |
|---------------------|--------------------------------------|--------------------------------------|--------------------------------------------------------|
| `outputDir`         | `string`                             | required                             | Base output directory                                   |
| `name`              | `string`                             | required                             | Snapshot name — becomes a subdirectory                  |
| `group`             | `string`                             | —                                    | Parent directory (e.g. page name)                       |
| `screenshot`        | `Partial<ScreenshotOptions>` \| `false` | `{ format: 'png', fullPage: false }` | Screenshot options. Pass `false` to disable. |
| `screenshot.format` | `'png'` \| `'webp'`                  | `'png'`                              | Live-capture supports `png` only; `webp` throws. For webp bytes, use the trace-extraction path. |
| `screenshot.fullPage` | `boolean`                          | `false`                              | Capture the full scrollable page                        |
| `manifest`          | `boolean`                            | `true`                               | Generate `manifest.json`                                |
| `extraSelectors`    | `string[]`                           | —                                    | Extra selectors the collector keeps                     |
| `excludeSelectors`  | `string[]`                           | —                                    | Selectors the collector drops                           |
| `extraAttributes`   | `string[]`                           | —                                    | Extra attributes the collector preserves                |

`saveSnapshot()` is **skip-write-if-unchanged**: when the assembled HTML matches the existing `index.html`, nothing is rewritten, but the returned `files` still references the existing paths.

### 3. Extract from existing traces (CLI or API)

Extract snapshots from Playwright HTML reports, trace ZIPs, or hosted report URLs — without re-running tests.

#### CLI

```bash
# From a local HTML report directory
npx playwright-snapshot-saver extract --source playwright-report

# From a trace ZIP file
npx playwright-snapshot-saver extract --source test-results/login/trace.zip

# From a hosted report URL
npx playwright-snapshot-saver extract --source https://example.com/report

# With filters and options
npx playwright-snapshot-saver extract \
  --source playwright-report \
  --output .snapshots \
  --page login \
  --state initial \
  --screenshot
```

#### CLI options

| Option            | Description                                                 |
|-------------------|-------------------------------------------------------------|
| `--source <path>` | Report directory, trace ZIP, or URL (required)              |
| `--output <dir>`  | Output directory (default: `.snapshots`)                    |
| `--page <name>`   | Filter by page name                                         |
| `--state <name>`  | Filter by state name                                        |
| `--screenshot`    | Opt in to `resources/screenshot.webp` extraction (off by default) |
| `--no-screenshot` | Explicitly disable (accepted for backwards compatibility)    |
| `--no-manifest`   | Skip `manifest.json` generation                             |

#### `extractSnapshots` API

```ts
import { extractSnapshots } from 'playwright-snapshot-saver';

const result = await extractSnapshots({
  source: 'playwright-report',  // directory, .zip path, or URL
  outputDir: '.snapshots',
  screenshot: true,              // default is false since 0.6.0
  manifest: true,
  filter: { page: 'login' },
});

for (const snap of result.snapshots) {
  console.log(`${snap.page}/${snap.state} -> ${snap.outputDir}`);
  console.log(snap.files.html);          // always present
  console.log(snap.files.manifest);       // when manifest: true
  console.log(snap.files.screenshot);     // when screenshot: true and a frame was available
}
```

Trace-extracted bundles are **fully self-contained**: every `<link>`, `<img>`, CSS `url(...)`, `@font-face`, and SVG `<use>` reference in the source page is rewritten to point at a file under `resources/`, and the original `<base>` element is stripped. Open the bundle anywhere — it renders without network access.

## Snapshot bundle format (v2)

Each snapshot is a directory containing:

```
<name>/
  index.html                   # Sanitized DOM referencing resources/ (REQUIRED)
  manifest.json                # Metadata, schema version 2 (REQUIRED)
  resources/
    screenshot.png|webp        # Visual reference (optional)
    <sha1>.css                 # Stylesheet sidecars referenced by <link>
    <sha1>.woff2|png|jpg|…     # Fonts, images, media (trace-extracted bundles)
```

`index.html` references every resource via a relative `resources/<filename>` path. The plugin inlines CSS sidecars before passing the HTML to the JCEF `<iframe srcdoc>` because `srcdoc` iframes cannot resolve relative URLs. Producers ship sidecars — **do not pre-inline CSS.**

### manifest.json

```json
{
  "version": 2,
  "url": "https://example.com/login",
  "viewport": { "width": 1280, "height": 720 },
  "timestamp": "2025-01-15T10:30:00Z",
  "userAgent": "Mozilla/5.0 …",
  "playwright": "1.58.2"
}
```

Exactly one driver field (`playwright` / `selenium` / `cypress` / `appium`) is populated per manifest, matching the driver that produced the bundle. The Playwright version is auto-detected from your installed `playwright-core`.

The v1 format is **not backwards compatible** — the plugin refuses to load v1 bundles with a user-visible error. Regenerate them by re-running your tests.

See [`docs/snapshot-bundle-spec.md`](../../docs/snapshot-bundle-spec.md) for the authoritative spec.

## Requirements

- Playwright `>=1.40.0`
- Node.js 18+
- For the marker/reporter workflow: `trace: 'on'` in Playwright config

## License

MIT
