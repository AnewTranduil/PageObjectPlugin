# playwright-snapshot-saver

Capture Playwright page snapshots (HTML with inlined CSS, screenshots, metadata) for the [Page Mirror](https://github.com/AnewTranduil/PageObjectPlugin) IntelliJ plugin.

## Installation

```bash
npm install playwright-snapshot-saver
```

## Usage

There are three ways to capture snapshots, from simplest to most flexible.

### 1. Marker + Reporter (recommended)

Mark snapshot points in your tests with `snapshot()`, then let the reporter extract them automatically from Playwright traces.

**playwright.config.ts**

```ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  use: {
    trace: 'on', // required for snapshot extraction
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

After `npx playwright test`, the reporter extracts snapshots into:

```
.snapshots/
  login/
    initial/  { index.html, screenshot.webp, manifest.json }
    error/    { index.html, screenshot.webp, manifest.json }
```

#### Reporter options

| Option      | Default        | Description                        |
|-------------|----------------|------------------------------------|
| `outputDir` | `'.snapshots'` | Base directory for extracted files  |
| `screenshot`| `true`         | Extract screencast frame as screenshot |
| `manifest`  | `true`         | Generate `manifest.json`           |

### 2. Direct API (`saveSnapshot`)

Call `saveSnapshot()` with a live Playwright `Page` to capture a snapshot immediately during test execution.

```ts
import { test } from '@playwright/test';
import { saveSnapshot } from 'playwright-snapshot-saver';

test('capture snapshot', async ({ page }) => {
  await page.goto('https://example.com');

  const result = await saveSnapshot(page, {
    outputDir: '.snapshots',
    name: 'initial',
    group: 'example',          // optional parent directory
    screenshot: { format: 'png', fullPage: true },
    manifest: true,
  });

  console.log(result.outputDir);  // .snapshots/example/initial
  console.log(result.files.html); // .snapshots/example/initial/index.html
});
```

#### `SaveSnapshotOptions`

| Option       | Type     | Default  | Description                              |
|--------------|----------|----------|------------------------------------------|
| `outputDir`  | `string` | required | Base output directory                    |
| `name`       | `string` | required | Snapshot name (becomes subdirectory)     |
| `group`      | `string` | —        | Parent directory (e.g. page name)        |
| `screenshot.enabled` | `boolean` | `true` | Capture a screenshot               |
| `screenshot.format`  | `'png' \| 'jpeg'` | `'png'` | Screenshot format        |
| `screenshot.fullPage`| `boolean` | `false` | Capture the full scrollable page  |
| `manifest`   | `boolean`| `true`   | Generate `manifest.json`                 |

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
  --no-screenshot
```

#### CLI options

| Option            | Description                                      |
|-------------------|--------------------------------------------------|
| `--source <path>` | Report directory, trace ZIP, or URL (required)   |
| `--output <dir>`  | Output directory (default: `.snapshots`)         |
| `--page <name>`   | Filter by page name                              |
| `--state <name>`  | Filter by state name                             |
| `--no-screenshot` | Skip screenshot extraction                       |
| `--no-manifest`   | Skip manifest generation                         |

#### `extractSnapshots` API

```ts
import { extractSnapshots } from 'playwright-snapshot-saver';

const result = await extractSnapshots({
  source: 'playwright-report',  // directory, .zip path, or URL
  outputDir: '.snapshots',
  screenshot: true,
  manifest: true,
  filter: { page: 'login' },
});

for (const snap of result.snapshots) {
  console.log(`${snap.page}/${snap.state} -> ${snap.outputDir}`);
}
```

## Snapshot bundle format

Each snapshot is a directory containing:

```
<name>/
  index.html       # DOM with all CSS inlined
  screenshot.webp   # Visual reference (optional)
  manifest.json     # Metadata (optional)
```

### manifest.json

```json
{
  "version": 1,
  "url": "https://example.com/login",
  "viewport": { "width": 1280, "height": 720 },
  "timestamp": "2025-01-15T10:30:00Z",
  "playwright": "1.48.0"
}
```

## Requirements

- Playwright `>=1.40.0`
- Node.js 18+
- For the marker/reporter workflow: `trace: 'on'` in Playwright config

## License

MIT
