# Task 10 — Trace Extraction & Reporter Integration

> **Status:** DONE — the package path referenced below
> (`packages/snapshot-saver/`) is stale; the shipped package is
> `packages/playwright-snapshot-saver/`. `html-inliner.ts` and
> `manifest-generator.ts` were extracted into
> `packages/snapshot-core/src/{assemble-html.ts,manifest.ts}` in
> Task 15; the trace pipeline moved behind a framework-agnostic
> `TraceBackend` interface in Task 15.5.

## Overview

Extend `playwright-snapshot-saver` to extract snapshots from Playwright traces. Two new capabilities:

1. **Reporter** — A Playwright reporter that detects `[snapshot:]` markers in test steps and extracts DOM snapshots from traces post-run into the `.snapshots/` bundle format.
2. **Extractor** — A programmatic API and CLI for extracting snapshots from existing HTML reports, raw trace ZIPs, or hosted report URLs.

Both features reuse Playwright's internal isomorphic trace rendering code (the same pipeline that powers the trace viewer) to produce pixel-perfect HTML snapshots.

## Bundle Format (Updated)

Layout.json is removed from the bundle. The plugin will query element bounds directly from the DOM via JCEF, enabling picking of **any** element (not just pre-selected ones).

```
<page>/<state>/
  index.html       # Full rendered HTML from trace (REQUIRED)
  screenshot.webp  # Screencast frame from trace (optional)
  manifest.json    # Metadata (optional)
```

Directory naming uses `page` and `state` parameters:

```
.snapshots/
  login/
    main/       { index.html, screenshot.webp, manifest.json }
    error/      { index.html, screenshot.webp, manifest.json }
  dashboard/
    main/       { index.html, screenshot.webp, manifest.json }
```

## Snapshot Marker API

```typescript
import { snapshot } from 'playwright-snapshot-saver';

interface SnapshotMarkerOptions {
  page: string;         // Page identifier (e.g., 'login', 'dashboard')
  state?: string;       // State within the page (default: 'main')
}

async function snapshot(options: SnapshotMarkerOptions): Promise<void>;
```

Implementation creates a timestamped `test.step` marker in the trace:

```typescript
/**
 * Marks a snapshot point in the Playwright trace.
 * The reporter extracts the DOM snapshot at this moment after the test finishes.
 *
 * @param options.page - Page identifier, becomes the parent directory
 * @param options.state - State name within the page (default: 'main')
 */
export async function snapshot({ page, state = 'main' }: SnapshotMarkerOptions) {
  await test.step(`[snapshot:${page}/${state}]`, async () => {});
}
```

**Rules:**
- Must be called inside a `test()` body (depends on `@playwright/test`)
- `page` is required, `state` defaults to `'main'`
- `page` and `state` must be filesystem-safe (alphanumeric, hyphens, underscores)
- Duplicate page/state in one test: last marker wins, log warning
- Same page/state across different tests: last test wins, log warning

### Usage

```typescript
import { test } from '@playwright/test';
import { snapshot } from 'playwright-snapshot-saver';

test('login flow', async ({ page }) => {
  await page.goto('/login');

  await snapshot({ page: 'login' });                    // → .snapshots/login/main/

  await page.fill('#username', 'wrong');
  await page.click('button[type="submit"]');

  await snapshot({ page: 'login', state: 'error' });    // → .snapshots/login/error/
});
```

## Reporter

### Configuration

```typescript
// playwright.config.ts
import { defineConfig } from '@playwright/test';

export default defineConfig({
  use: {
    trace: 'on',  // Required — reporter reads trace files
  },
  reporter: [
    ['list'],
    ['playwright-snapshot-saver/reporter', {
      outputDir: '.snapshots',  // default: '.snapshots'
      screenshot: true,         // default: true
      manifest: true,           // default: true
    }],
  ],
});
```

### Reporter Options

```typescript
interface SnapshotReporterOptions {
  outputDir?: string;    // Output directory (default: '.snapshots')
  screenshot?: boolean;  // Generate screenshot from trace screencast frame (default: true)
  manifest?: boolean;    // Generate manifest.json (default: true)
}
```

### Lifecycle

1. **`onBegin`** — Validate that tracing is enabled. Warn if not.
2. **`onTestEnd`** — Scan test steps for `[snapshot:page/state]` markers. Collect each marker's `callId` and the test's trace file path.
3. **`onEnd`** — For each collected marker:
   - Open trace ZIP via `TraceLoader`
   - Find the DOM snapshot matching the marker's `callId`
   - Render full HTML via `SnapshotRenderer`
   - Extract the screencast frame closest to the marker timestamp
   - Write bundle to `outputDir/page/state/`

## Extractor API

### Programmatic API

```typescript
interface ExtractOptions {
  source: string;        // Report dir, trace ZIP path, or URL
  outputDir?: string;    // Default: '.snapshots'
  screenshot?: boolean;  // Default: true
  manifest?: boolean;    // Default: true
  filter?: {
    page?: string;       // Extract only this page
    state?: string;      // Extract only this state
  };
}

interface ExtractResult {
  snapshots: Array<{
    page: string;
    state: string;
    outputDir: string;
    files: {
      html: string;
      screenshot?: string;
      manifest?: string;
    };
  }>;
}

async function extractSnapshots(options: ExtractOptions): Promise<ExtractResult>;
```

### Source Detection

| Input | Detection | Behavior |
|-------|-----------|----------|
| `./playwright-report` | Directory with `index.html` + `data/` | Find trace ZIPs inside report data |
| `./traces/abc.zip` | File ending in `.zip` | Load directly as trace |
| `http://localhost:9323` | Starts with `http://https:` | Fetch report, download trace ZIPs |
| `https://host/report` | Same | Same, with HTTPS |

### URL Source Flow

1. Fetch the report's `index.html`
2. Extract the base64-encoded report data (Playwright embeds it as `#playwrightReportBase64`)
3. Find trace attachment paths from the report data
4. Download trace ZIPs
5. Proceed with normal extraction

### No Markers Behavior

`page` is a required marker parameter. If a trace contains no `[snapshot:]` markers, the extractor logs a warning and suggests correct usage:

```
Warning: No snapshot markers found in trace. To mark snapshots, use:

  import { snapshot } from 'playwright-snapshot-saver';

  await snapshot({ page: 'login', state: 'error' });
```

### CLI

```bash
# From local report directory
npx playwright-snapshot-saver extract --source ./playwright-report

# From raw trace file
npx playwright-snapshot-saver extract --source ./test-results/trace.zip

# From hosted report
npx playwright-snapshot-saver extract --source http://localhost:9323

# With filters
npx playwright-snapshot-saver extract --source ./playwright-report --page login --state error

# Custom output
npx playwright-snapshot-saver extract --source ./playwright-report --output .snapshots
```

## Playwright Adapter

All Playwright internal imports isolated to a single file:

```typescript
// src/trace/playwright-adapter.ts
import { TraceLoader } from 'playwright-core/lib/utils/isomorphic/trace/traceLoader';
import { SnapshotRenderer } from 'playwright-core/lib/utils/isomorphic/trace/snapshotRenderer';
import { SnapshotStorage } from 'playwright-core/lib/utils/isomorphic/trace/snapshotStorage';
```

Exports a clean interface:

```typescript
interface TraceSnapshot {
  callId: string;
  name: string;
  timestamp: number;
  pageId: string;
}

interface RenderedSnapshot {
  html: string;
  viewport: { width: number; height: number };
  screenshot?: Buffer;
}

export async function loadTrace(source: string): Promise<TraceSnapshot[]>;
export async function renderSnapshot(source: string, callId: string): Promise<RenderedSnapshot>;
```

If Playwright changes internal paths between versions, only this file needs updating.

## Compatibility

- **Minimum Playwright version:** Current latest (pinned at implementation time)
- **Trace format:** v6+ (Playwright's `TraceModernizer` handles backward compat for v6-v8)

### GitHub Action (Daily Compatibility Check)

`.github/workflows/playwright-compat.yml` — runs daily at 08:00 UTC:

1. Check latest published `playwright` version on npm
2. Install it in the package
3. Run the test suite
4. On failure: open a GitHub issue titled `Playwright X.Y.Z compatibility broken`
5. On success: no action needed

```yaml
name: Playwright Compatibility Check
on:
  schedule:
    - cron: '0 8 * * *'
  workflow_dispatch:

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
      - run: |
          LATEST=$(npm view playwright version)
          echo "Latest Playwright: $LATEST"
          cd packages/snapshot-saver
          npm install playwright@$LATEST playwright-core@$LATEST
          npm test
```

## Edge Cases

### Trace-related

| Case | Behavior |
|------|----------|
| Trace file corrupted or incomplete | Log error with file path, skip. Don't fail the whole extraction |
| Trace format version too old (< v6) | Log warning: "Trace format vX not supported, minimum v6" |
| Trace format version newer than known | Attempt to load (modernizer handles forward compat). Log warning if it fails |
| Multiple traces per test (retries) | Use the last retry's trace only |
| Parallel tests writing same page/state | Last writer wins. Log warning with test name |

### Marker-related

| Case | Behavior |
|------|----------|
| Duplicate page/state in same test | Last marker wins, log warning |
| Invalid characters in page/state | Reject with error: "page/state must be alphanumeric, hyphens, underscores" |
| Marker in a test with tracing disabled | Skip, log: "Snapshot marker 'login/main' skipped -- tracing not enabled" |
| Marker but test crashes before trace written | Skip, log warning |
| Empty page string | Runtime error: "page is required" |

### URL source-related

| Case | Behavior |
|------|----------|
| URL unreachable | Fail with: "Cannot connect to http://..." |
| URL returns non-Playwright HTML | Fail with: "No Playwright report data found at URL" |
| Report has no trace attachments | Log warning: "Report contains no traces" |
| Authentication required | Not supported in v1. Fail with clear message suggesting local directory |
| Large report (many traces) | Process sequentially to limit memory. Log progress per trace |

### Rendering-related

| Case | Behavior |
|------|----------|
| Missing resources (CSS, images) | Render with what's available. Missing resources become broken links (matches trace viewer behavior) |
| Shadow DOM in snapshot | Reconstruct via Playwright's template mechanism (handled by SnapshotRenderer) |
| No screencast frame near marker timestamp | Skip screenshot file, log info |
| Rendered HTML exceeds 10MB | Log warning, still write |

## Package Structure

```
packages/snapshot-saver/
  src/
    index.ts                    # Public API: saveSnapshot, snapshot, extractSnapshots
    reporter.ts                 # Playwright ReporterV2 implementation
    cli.ts                      # CLI entry point (extract command)
    snapshot-marker.ts          # snapshot() function
    extractor.ts                # extractSnapshots() logic + source detection
    trace/
      playwright-adapter.ts     # Isolated Playwright internal imports
    sources/
      directory-source.ts       # Load from local report directory
      zip-source.ts             # Load from raw trace ZIP
      url-source.ts             # Load from hosted report URL
    html-inliner.ts             # Existing -- live capture
    manifest-generator.ts       # Existing -- live capture
  tests/
    save-snapshot.spec.ts       # Existing tests
    snapshot-marker.spec.ts     # Marker creates correct test.step
    reporter.spec.ts            # Reporter extracts from traces
    extractor.spec.ts           # Extractor with all 3 source types
    fixtures/
      sample-trace.zip          # Real trace for testing
  bin/
    cli.js                      # CLI shebang entry
  package.json                  # Updated: bin, exports
```

### Package Exports

```json
{
  "exports": {
    ".": "./dist/index.js",
    "./reporter": "./dist/reporter.js"
  },
  "bin": {
    "playwright-snapshot-saver": "./bin/cli.js"
  }
}
```

### Import Paths

```typescript
// Marker + extractor
import { saveSnapshot, snapshot, extractSnapshots } from 'playwright-snapshot-saver';

// Reporter (in playwright.config.ts)
reporter: ['playwright-snapshot-saver/reporter', { outputDir: '.snapshots' }]

// CLI
// npx playwright-snapshot-saver extract --source ./playwright-report
```

## Changes to Existing Code

- **Remove** `layout-generator.ts` and all layout.json generation from `saveSnapshot()`
- **Remove** `layout.json` from `SnapshotResult.files`
- **Update** existing tests to not expect layout.json
- **Update** `saveSnapshot()` options: remove `extraSelectors`, `excludeSelectors`, `extraAttributes` (all layout.json-specific)
- **Plugin refactor** (separate task): update `inspect.js` to use `getBoundingClientRect()` instead of layout map, remove layout.json loading from `SnapshotService.kt`
