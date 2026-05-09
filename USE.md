# How It Works

Page Object Helper connects your test code to the UI through page snapshots. The workflow has three stages: **capture snapshots**, **load them in the IDE**, and **work with page objects**.

```
Playwright tests                    .snapshots/                      IDE (Page Mirror panel)
─────────────────                   ───────────                      ──────────────────────
 run tests with                      login/                          renders snapshot HTML
 snapshot markers        ──►          initial/index.html    ──►      highlights locators
 or saveSnapshot()                    error-state/index.html         generates new locators
```

## Stage 1: Capture Snapshots

The `playwright-snapshot-saver` npm package provides three ways to capture snapshots from Playwright pages.

### Option A: Reporter + Markers (recommended)

The reporter extracts snapshots automatically from Playwright traces after tests finish. You mark the moments you want to capture with the `snapshot()` function.

**Setup:**

1. Enable tracing and register the reporter in `playwright.config.ts`:

```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  reporter: [
    ['list'],
    ['playwright-snapshot-saver/reporter']
  ],
  use: {
    trace: 'on',
  },
});
```

2. Mark snapshot points in your tests:

```typescript
import { test, expect } from '@playwright/test';
import { snapshot } from 'playwright-snapshot-saver';

test('login flow', async ({ page }) => {
  await page.goto('/login');
  await snapshot({ page: 'login', state: 'initial' });

  await page.fill('#username', 'wrong');
  await page.fill('#password', 'wrong');
  await page.click('button[type="submit"]');
  await expect(page.locator('.error')).toBeVisible();
  await snapshot({ page: 'login', state: 'error' });
});
```

3. Run tests normally — the reporter saves snapshots to `.snapshots/`:

```bash
npx playwright test
```

**Reporter options** (passed as the second element of the reporter tuple):

| Option | Default | Description |
|--------|---------|-------------|
| `outputDir` | `.snapshots` | Where to save snapshots |
| `screenshot` | `true` | Extract screenshot from trace screencast |
| `manifest` | `true` | Generate `manifest.json` with metadata |

```typescript
reporter: [
  ['playwright-snapshot-saver/reporter', {
    outputDir: './my-snapshots',
    screenshot: false,
  }]
]
```

### Option B: Programmatic API (`saveSnapshot`)

Call `saveSnapshot()` directly to capture a snapshot at any point. This does not require tracing — it reads the live page DOM and inlines all CSS.

```typescript
import { saveSnapshot } from 'playwright-snapshot-saver';

await saveSnapshot(page, {
  outputDir: '.snapshots',
  name: 'login-initial',
  group: 'login',
  screenshot: { enabled: true, format: 'png', fullPage: false },
  manifest: true,
});
```

| Option | Default | Description |
|--------|---------|-------------|
| `outputDir` | (required) | Base output directory |
| `name` | (required) | Snapshot name — becomes the subdirectory |
| `group` | — | Parent group directory |
| `screenshot.enabled` | `true` | Capture a screenshot |
| `screenshot.format` | `png` | `png` or `jpeg` |
| `screenshot.fullPage` | `false` | Capture the full scrollable page |
| `manifest` | `true` | Generate `manifest.json` |

### Option C: Extract from Existing Sources (CLI / API)

Extract snapshots from an already-generated Playwright HTML report, a trace ZIP file, or a hosted report URL. Useful when you don't control the test code or want to extract snapshots after the fact.

**CLI:**

```bash
# From a Playwright HTML report directory
npx playwright-snapshot-saver extract --source ./playwright-report

# From a trace ZIP
npx playwright-snapshot-saver extract --source ./test-results/trace.zip

# From a hosted report URL
npx playwright-snapshot-saver extract --source https://example.com/report

# With filters
npx playwright-snapshot-saver extract --source ./playwright-report --page login --state initial --output ./my-snapshots
```

**Programmatic:**

```typescript
import { extractSnapshots } from 'playwright-snapshot-saver';

const result = await extractSnapshots({
  source: './playwright-report',
  outputDir: '.snapshots',
  filter: { page: 'login' },
});
```

| Option | Default | Description |
|--------|---------|-------------|
| `source` | (required) | Report directory, trace ZIP path, or URL |
| `outputDir` | `.snapshots` | Where to save snapshots |
| `screenshot` | `true` | Extract screenshot from trace |
| `manifest` | `true` | Generate `manifest.json` |
| `filter.page` | — | Only extract this page |
| `filter.state` | — | Only extract this state |

## Snapshot Bundle Format

Each snapshot is a directory containing the **v2** layout:

```
.snapshots/
├── login/
│   ├── initial/
│   │   ├── index.html              # Sanitized DOM, references resources/
│   │   ├── manifest.json           # Metadata (version=2, viewport, timestamp, ...)
│   │   └── resources/
│   │       ├── screenshot.webp     # Visual reference
│   │       └── <sha1>.css          # Stylesheet sidecars referenced by <link>
│   └── error/
│       ├── index.html
│       ├── manifest.json
│       └── resources/
│           ├── screenshot.webp
│           └── <sha1>.css
├── dashboard/
│   └── main/
│       └── ...
```

**`index.html`** — the full page DOM. External stylesheets are written
as sidecar files under `resources/<sha1>.css` and referenced by `<link
rel="stylesheet" href="resources/...">`. The plugin inlines those
sidecars into `<style>` blocks before handing the HTML to JCEF, because
`<iframe srcdoc>` has no base URL and cannot resolve relative paths.

**`resources/screenshot.webp`** (or `.png`) — a visual reference of the
page at capture time. Lives under `resources/` in v2 (top-level in v1).

**`manifest.json`** — metadata about the snapshot. The `version` field
is the **schema** version and is always `2`. The plugin refuses to load
bundles with any other declared version and shows an outdated-bundle
banner. See [`docs/migration-v1-to-v2.md`](docs/migration-v1-to-v2.md)
for upgrading older bundles.

```json
{
  "version": 2,
  "url": "https://example.com/login",
  "viewport": { "width": 1280, "height": 720 },
  "timestamp": "2026-04-15T21:07:18.357Z",
  "playwright": "1.58.2"
}
```

## Stage 2: Load in the IDE

Once snapshots exist in the project, the plugin picks them up automatically:

1. **Auto-discovery** — when you open a test file, the plugin searches for `.snapshots/` directories and loads matching snapshots.
2. **Manual load** — use **Tools > Load Snapshot Directory...** to load a specific snapshot directory.
3. **File watcher** — when snapshots change on disk (e.g., after re-running tests), the plugin reloads them automatically.

The loaded snapshot renders in the **Page Mirror** tool window on the right side of the IDE.

## Stage 3: Work with Page Objects

With a snapshot loaded, the plugin bridges your code and the UI:

### Cursor-driven highlighting

Place your cursor on any Playwright locator in a page object or test file. The plugin parses the locator and highlights the matching element in the snapshot.

```typescript
export class LoginPage {
  readonly usernameInput = this.page.locator('#username');
  //                                         ▲
  //                          cursor here → element highlights in Page Mirror
}
```

Supported locator patterns:
- `page.locator('css-selector')`
- `page.getByRole('role')`
- `page.getByText('text')`
- `page.getByTestId('test-id')`
- `page.getByPlaceholder('placeholder')`

### Element picker

Press `Alt+Shift+I` to enter inspect mode. Click any element in the snapshot — the plugin generates a locator and inserts it at your cursor position in the editor.

### Selector validation (gutter badges)

The editor gutter shows a badge next to each locator with the number of matching elements in the current snapshot:
- **1** — unique match (good)
- **0** — no match (broken selector)
- **2+** — multiple matches (ambiguous selector)

### Highlight current selector (`Alt+Shift+H`)

Press `Alt+Shift+H` in the editor to manually highlight the locator on the
caret line in the snapshot. This mirrors the cursor-driven highlight
above, but is useful when a hook or another caret listener has cleared
the highlight and you want to bring it back without moving the caret.

### Highlight All

Click the **Show All** button in the Page Mirror tool window toolbar to
highlight every locator in the current file on the snapshot at once.
Color-coded overlays distinguish different locators. Duplicate and
overlapping selectors are flagged with visual badges. There is no
keyboard shortcut for Highlight All — toggle it from the tool window.
