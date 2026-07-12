# Trace Extraction & Reporter Implementation Plan

> **Historical — superseded by the Task 15 refactor.** This plan was
> written against a `packages/snapshot-saver/` layout with
> `layout.json`, `html-inliner.ts`, and `manifest-generator.ts`. What
> actually shipped is `packages/playwright-snapshot-saver/` plus a
> separate `packages/snapshot-core/` (Task 15), with trace rendering
> owned by `snapshot-core` behind the `TraceBackend` interface (Task
> 15.5). `manifest.version` is `2` (see `MANIFEST_VERSION` in
> `packages/snapshot-core/src/types.ts`), the `screenshot` default is
> `false` (`ExtractOptions` in
> `packages/playwright-snapshot-saver/src/types.ts`), and none of
> `layout.json`, `html-inliner.ts`, or `manifest-generator.ts` exist
> today. Kept as a design record.
>
> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `playwright-snapshot-saver` to extract snapshots from Playwright traces via a reporter and CLI extractor.

**Architecture:** A Playwright reporter detects `[snapshot:]` markers in test steps and extracts DOM snapshots from trace files post-run. A standalone extractor reads from local report directories, raw trace ZIPs, or hosted report URLs. Both use Playwright's internal isomorphic trace rendering code (isolated behind a single adapter file) to produce pixel-perfect HTML.

**Tech Stack:** TypeScript, Playwright ReporterV2, Playwright internal trace APIs (`playwright-core/lib/utils/isomorphic/trace/*`), Node.js fs/http

**Spec:** `docs/tasks/task-10-trace-extraction.md`

---

## File Structure

```
packages/snapshot-saver/
  src/
    index.ts                    # MODIFY — add snapshot + extractSnapshots exports, remove layout imports
    types.ts                    # MODIFY — remove layout types, add new types
    snapshot-marker.ts          # CREATE — snapshot() function
    reporter.ts                 # CREATE — Playwright ReporterV2
    extractor.ts                # CREATE — extractSnapshots() logic + source detection
    cli.ts                      # CREATE — CLI entry point
    trace/
      playwright-adapter.ts     # CREATE — isolated Playwright internal imports
    sources/
      directory-source.ts       # CREATE — load traces from report directory
      zip-source.ts             # CREATE — load from raw trace ZIP
      url-source.ts             # CREATE — load from hosted report URL
    html-inliner.ts             # KEEP — used by saveSnapshot
    manifest-generator.ts       # KEEP — used by saveSnapshot + extractor
    layout-generator.ts         # DELETE
  tests/
    save-snapshot.spec.ts       # MODIFY — remove layout.json expectations
    snapshot-marker.spec.ts     # CREATE
    reporter.spec.ts            # CREATE
    extractor.spec.ts           # CREATE
  bin/
    cli.js                      # CREATE — shebang entry
  package.json                  # MODIFY — add bin, exports
  tsconfig.json                 # KEEP
  playwright.config.ts          # MODIFY — add trace project for reporter tests
```

---

### Task 1: Remove layout.json from saveSnapshot

Remove layout-generator.ts, layout-related types, and update saveSnapshot to no longer produce layout.json. Update existing tests.

**Files:**
- Delete: `packages/snapshot-saver/src/layout-generator.ts`
- Modify: `packages/snapshot-saver/src/types.ts`
- Modify: `packages/snapshot-saver/src/index.ts`
- Modify: `packages/snapshot-saver/tests/save-snapshot.spec.ts`

- [ ] **Step 1: Update types.ts — remove layout types and layout-related options**

Replace `packages/snapshot-saver/src/types.ts` with:

```typescript
export interface SaveSnapshotOptions {
  /** Base output directory (e.g., path.join(__dirname, '.snapshots')) */
  outputDir: string;

  /** Snapshot name — becomes the subdirectory (e.g., 'initial', 'error-state') */
  name: string;

  /** Optional group name — creates parent dir (e.g., 'login' -> .snapshots/login/initial/) */
  group?: string;

  /** Override viewport dimensions (default: reads from page.viewportSize()) */
  viewport?: { width: number; height: number };

  /** Screenshot options */
  screenshot?: {
    enabled?: boolean;       // default: true
    fullPage?: boolean;      // default: false
    format?: 'png' | 'jpeg'; // default: 'png'
  };

  /** Generate manifest.json (default: true) */
  manifest?: boolean;
}

export interface SnapshotResult {
  /** Absolute path to the snapshot directory */
  outputDir: string;
  /** Paths to all generated files */
  files: {
    html: string;
    screenshot?: string;
    manifest?: string;
  };
}

export interface ManifestJson {
  version: number;
  url: string;
  viewport: { width: number; height: number };
  timestamp: string;
  playwright: string;
  userAgent: string;
}
```

- [ ] **Step 2: Update index.ts — remove layout generation**

Replace `packages/snapshot-saver/src/index.ts` with:

```typescript
import { Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { SaveSnapshotOptions, SnapshotResult } from './types';
import { generateInlinedHtml } from './html-inliner';
import { generateManifest } from './manifest-generator';

export { SaveSnapshotOptions, SnapshotResult, ManifestJson } from './types';

export async function saveSnapshot(
  page: Page,
  options: SaveSnapshotOptions
): Promise<SnapshotResult> {
  const outDir = options.group
    ? path.join(options.outputDir, options.group, options.name)
    : path.join(options.outputDir, options.name);

  fs.mkdirSync(outDir, { recursive: true });

  const screenshotEnabled = options.screenshot?.enabled !== false;
  const screenshotFormat = options.screenshot?.format ?? 'png';
  const screenshotFullPage = options.screenshot?.fullPage ?? false;
  const manifestEnabled = options.manifest !== false;

  const [html, manifest, _screenshot] = await Promise.all([
    generateInlinedHtml(page),
    manifestEnabled ? generateManifest(page) : Promise.resolve(null),
    screenshotEnabled
      ? page.screenshot({
          path: path.join(outDir, `screenshot.${screenshotFormat}`),
          type: screenshotFormat,
          fullPage: screenshotFullPage,
        })
      : Promise.resolve(null),
  ]);

  const htmlPath = path.join(outDir, 'index.html');
  fs.writeFileSync(htmlPath, html, 'utf-8');

  const files: SnapshotResult['files'] = { html: htmlPath };

  if (screenshotEnabled) {
    files.screenshot = path.join(outDir, `screenshot.${screenshotFormat}`);
  }

  if (manifestEnabled && manifest) {
    const manifestPath = path.join(outDir, 'manifest.json');
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
    files.manifest = manifestPath;
  }

  return { outputDir: outDir, files };
}
```

- [ ] **Step 3: Delete layout-generator.ts**

```bash
rm packages/snapshot-saver/src/layout-generator.ts
```

- [ ] **Step 4: Update tests — remove layout.json expectations**

Replace `packages/snapshot-saver/tests/save-snapshot.spec.ts` with:

```typescript
import { test, expect } from '@playwright/test';
import { saveSnapshot } from '../src/index';
import * as fs from 'fs';
import * as path from 'path';

const tmpDir = path.join(__dirname, '..', '.test-output');

test.beforeEach(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.afterAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.describe('saveSnapshot', () => {
  test('generates html, screenshot, and manifest with default options', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'initial',
    });

    expect(fs.existsSync(result.files.html)).toBe(true);
    expect(result.files.screenshot).toBeDefined();
    expect(fs.existsSync(result.files.screenshot!)).toBe(true);
    expect(result.files.manifest).toBeDefined();
    expect(fs.existsSync(result.files.manifest!)).toBe(true);
    expect(result.outputDir).toBe(path.join(tmpDir, 'initial'));

    // No layout.json should exist
    expect(fs.existsSync(path.join(result.outputDir, 'layout.json'))).toBe(false);
  });

  test('group option creates nested directory', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      group: 'login',
      name: 'initial',
    });

    expect(result.outputDir).toBe(path.join(tmpDir, 'login', 'initial'));
    expect(fs.existsSync(result.files.html)).toBe(true);
  });

  test('screenshot format jpeg produces jpeg file', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'jpeg-test',
      screenshot: { format: 'jpeg' },
    });

    expect(result.files.screenshot).toContain('screenshot.jpeg');
    expect(fs.existsSync(result.files.screenshot!)).toBe(true);
  });

  test('screenshot disabled skips screenshot file', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'no-screenshot',
      screenshot: { enabled: false },
    });

    expect(result.files.screenshot).toBeUndefined();
    const files = fs.readdirSync(result.outputDir);
    expect(files.some(f => f.startsWith('screenshot'))).toBe(false);
  });

  test('manifest disabled skips manifest file', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'no-manifest',
      manifest: false,
    });

    expect(result.files.manifest).toBeUndefined();
    const files = fs.readdirSync(result.outputDir);
    expect(files).not.toContain('manifest.json');
  });

  test('generated HTML is self-contained with inlined CSS', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'html-check',
    });

    const html = fs.readFileSync(result.files.html, 'utf-8');
    expect(html).toContain('<!DOCTYPE html>');
    expect(html).not.toMatch(/<link[^>]*rel="stylesheet"[^>]*>/);
  });

  test('manifest contains expected metadata fields', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'manifest-check',
    });

    const manifest = JSON.parse(fs.readFileSync(result.files.manifest!, 'utf-8'));
    expect(manifest.version).toBe(1);
    expect(manifest.url).toContain('login');
    expect(manifest.viewport).toEqual({ width: 1280, height: 720 });
    expect(manifest.timestamp).toBeTruthy();
    expect(manifest.playwright).toBeTruthy();
    expect(manifest.userAgent).toBeTruthy();
  });
});
```

- [ ] **Step 5: Run tests to verify**

Run: `cd packages/snapshot-saver && npx playwright test`
Expected: All 7 tests pass. The 3 layout-specific tests (extraSelectors, excludeSelectors, selector verification) are removed.

- [ ] **Step 6: Commit**

```bash
git add packages/snapshot-saver/src/types.ts packages/snapshot-saver/src/index.ts packages/snapshot-saver/tests/save-snapshot.spec.ts
git rm packages/snapshot-saver/src/layout-generator.ts
git commit -m "refactor: remove layout.json from snapshot bundle

layout.json limited element picking to pre-selected elements.
Removing it allows the plugin to pick any DOM element via
getBoundingClientRect() in JCEF."
```

---

### Task 2: Snapshot Marker Function

Create the `snapshot()` marker function that writes a `test.step` into the trace timeline.

**Files:**
- Create: `packages/snapshot-saver/src/snapshot-marker.ts`
- Modify: `packages/snapshot-saver/src/types.ts`
- Modify: `packages/snapshot-saver/src/index.ts`
- Create: `packages/snapshot-saver/tests/snapshot-marker.spec.ts`

- [ ] **Step 1: Add marker types to types.ts**

Append to `packages/snapshot-saver/src/types.ts`:

```typescript

export interface SnapshotMarkerOptions {
  /** Page identifier — becomes the parent directory (e.g., 'login', 'dashboard') */
  page: string;
  /** State within the page (default: 'main') */
  state?: string;
}
```

- [ ] **Step 2: Write the failing test**

Create `packages/snapshot-saver/tests/snapshot-marker.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import { snapshot } from '../src/snapshot-marker';

test.describe('snapshot marker', () => {
  test('creates a test step with correct label using default state', async () => {
    const steps: string[] = [];
    const originalStep = test.step;

    // Capture step names by running snapshot inside a test
    await snapshot({ page: 'login' });

    // Verify the step was created by checking test info
    const stepTitles = test.info().steps.map(s => s.title);
    expect(stepTitles).toContain('[snapshot:login/main]');
  });

  test('creates a test step with custom state', async () => {
    await snapshot({ page: 'login', state: 'error' });

    const stepTitles = test.info().steps.map(s => s.title);
    expect(stepTitles).toContain('[snapshot:login/error]');
  });

  test('rejects empty page string', async () => {
    await expect(snapshot({ page: '' })).rejects.toThrow('page is required');
  });

  test('rejects invalid characters in page', async () => {
    await expect(snapshot({ page: 'my page/bad' })).rejects.toThrow(
      'page must contain only alphanumeric characters, hyphens, and underscores'
    );
  });

  test('rejects invalid characters in state', async () => {
    await expect(snapshot({ page: 'login', state: 'bad state!' })).rejects.toThrow(
      'state must contain only alphanumeric characters, hyphens, and underscores'
    );
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd packages/snapshot-saver && npx playwright test snapshot-marker`
Expected: FAIL — `snapshot` module does not exist

- [ ] **Step 4: Implement snapshot-marker.ts**

Create `packages/snapshot-saver/src/snapshot-marker.ts`:

```typescript
import { test } from '@playwright/test';
import { SnapshotMarkerOptions } from './types';

const VALID_NAME = /^[a-zA-Z0-9_-]+$/;

/**
 * Marks a snapshot point in the Playwright trace.
 * The reporter extracts the DOM snapshot at this moment after the test finishes.
 *
 * @param options.page - Page identifier, becomes the parent directory
 * @param options.state - State name within the page (default: 'main')
 */
export async function snapshot({ page, state = 'main' }: SnapshotMarkerOptions): Promise<void> {
  if (!page) {
    throw new Error('page is required');
  }
  if (!VALID_NAME.test(page)) {
    throw new Error('page must contain only alphanumeric characters, hyphens, and underscores');
  }
  if (!VALID_NAME.test(state)) {
    throw new Error('state must contain only alphanumeric characters, hyphens, and underscores');
  }
  await test.step(`[snapshot:${page}/${state}]`, async () => {});
}
```

- [ ] **Step 5: Export from index.ts**

Add to the top of `packages/snapshot-saver/src/index.ts`:

```typescript
export { snapshot } from './snapshot-marker';
export { SnapshotMarkerOptions } from './types';
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd packages/snapshot-saver && npx playwright test snapshot-marker`
Expected: All 5 tests pass

- [ ] **Step 7: Commit**

```bash
git add packages/snapshot-saver/src/snapshot-marker.ts packages/snapshot-saver/src/types.ts packages/snapshot-saver/src/index.ts packages/snapshot-saver/tests/snapshot-marker.spec.ts
git commit -m "feat: add snapshot() marker function for trace-based extraction"
```

---

### Task 3: Playwright Trace Adapter

Create the adapter that isolates all Playwright internal imports and provides a clean interface for loading traces and rendering snapshots.

**Files:**
- Create: `packages/snapshot-saver/src/trace/playwright-adapter.ts`

- [ ] **Step 1: Verify Playwright internal paths exist**

Run: `node -e "require.resolve('playwright-core/lib/utils/isomorphic/trace/traceLoader')" 2>&1`

If this fails, check the actual path:
```bash
find node_modules/playwright-core/lib -name "traceLoader*" -type f 2>/dev/null
```

Note the actual path — it may differ between Playwright versions.

- [ ] **Step 2: Create playwright-adapter.ts**

Create `packages/snapshot-saver/src/trace/playwright-adapter.ts`:

```typescript
// All Playwright internal imports are isolated in this file.
// If Playwright changes internal paths between versions, only this file needs updating.

import type { TraceLoaderBackend } from 'playwright-core/lib/utils/isomorphic/trace/traceLoader';
import { TraceLoader } from 'playwright-core/lib/utils/isomorphic/trace/traceLoader';
import { SnapshotRenderer } from 'playwright-core/lib/utils/isomorphic/trace/snapshotRenderer';

export interface TraceSnapshotMarker {
  callId: string;
  label: string;       // e.g., '[snapshot:login/main]'
  page: string;        // parsed 'login'
  state: string;       // parsed 'main'
  timestamp: number;
  pageId: string;
}

export interface RenderedSnapshot {
  html: string;
  viewport: { width: number; height: number };
}

const MARKER_REGEX = /^\[snapshot:([a-zA-Z0-9_-]+)\/([a-zA-Z0-9_-]+)\]$/;

/**
 * Load a trace from a backend and find all snapshot markers.
 */
export async function loadTraceMarkers(backend: TraceLoaderBackend): Promise<{
  markers: TraceSnapshotMarker[];
  loader: TraceLoader;
}> {
  const loader = new TraceLoader();
  await loader.load(backend, () => undefined);

  const markers: TraceSnapshotMarker[] = [];

  for (const context of loader.contextEntries) {
    for (const action of context.actions) {
      const match = action.method === 'step' && MARKER_REGEX.exec(action.params?.title ?? '');
      if (match) {
        markers.push({
          callId: action.callId,
          label: action.params.title,
          page: match[1],
          state: match[2],
          timestamp: action.wallTime ?? action.startTime,
          pageId: action.pageId,
        });
      }
    }
  }

  return { markers, loader };
}

/**
 * Render a snapshot at the given callId to full HTML.
 */
export async function renderSnapshotAtMarker(
  loader: TraceLoader,
  marker: TraceSnapshotMarker
): Promise<RenderedSnapshot> {
  const storage = loader.storage();
  const renderer = storage.snapshotByName(marker.pageId, `after@${marker.callId}`);
  if (!renderer) {
    throw new Error(`No snapshot found for marker ${marker.label} (callId: ${marker.callId})`);
  }
  const rendered = renderer.render();
  return {
    html: rendered.html,
    viewport: renderer.viewport(),
  };
}

/**
 * Find the closest screencast frame to a timestamp.
 * Returns the PNG buffer or undefined if no frames exist.
 */
export async function findScreencastFrame(
  loader: TraceLoader,
  marker: TraceSnapshotMarker
): Promise<Buffer | undefined> {
  for (const context of loader.contextEntries) {
    const frames = context.screencastFrames ?? [];
    if (frames.length === 0) continue;

    // Find closest frame by timestamp
    let closest = frames[0];
    for (const frame of frames) {
      if (Math.abs(frame.timestamp - marker.timestamp) < Math.abs(closest.timestamp - marker.timestamp)) {
        closest = frame;
      }
    }

    const blob = await loader.resourceForSha1(closest.sha1);
    if (blob) {
      const arrayBuffer = await blob.arrayBuffer();
      return Buffer.from(arrayBuffer);
    }
  }
  return undefined;
}

export { TraceLoaderBackend };
```

- [ ] **Step 3: Verify it compiles**

Run: `cd packages/snapshot-saver && npx tsc --noEmit`
Expected: No errors (or only errors related to Playwright internal type resolution — we'll handle those)

If there are type errors from Playwright internals, add a `declare module` shim at the top of the adapter:

```typescript
// If Playwright doesn't export types properly, add:
declare module 'playwright-core/lib/utils/isomorphic/trace/traceLoader' {
  export interface TraceLoaderBackend {
    entryNames(): Promise<string[]>;
    hasEntry(entryName: string): Promise<boolean>;
    readText(entryName: string): Promise<string | undefined>;
    readBlob(entryName: string): Promise<Blob | undefined>;
    isLive(): boolean;
  }
  export class TraceLoader {
    load(backend: TraceLoaderBackend, progressFn: (done: number, total: number) => void): Promise<void>;
    contextEntries: any[];
    storage(): any;
    resourceForSha1(sha1: string): Promise<Blob | undefined>;
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add packages/snapshot-saver/src/trace/playwright-adapter.ts
git commit -m "feat: add Playwright trace adapter with isolated internal imports"
```

---

### Task 4: Source Loaders (Directory, ZIP, URL)

Create the three source loader backends that resolve different input types into trace data.

**Files:**
- Create: `packages/snapshot-saver/src/sources/directory-source.ts`
- Create: `packages/snapshot-saver/src/sources/zip-source.ts`
- Create: `packages/snapshot-saver/src/sources/url-source.ts`

- [ ] **Step 1: Create directory-source.ts**

This loads traces from a Playwright HTML report directory (`playwright-report/`).

Create `packages/snapshot-saver/src/sources/directory-source.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';

/**
 * Find all trace ZIP files inside a Playwright HTML report directory.
 * Report structure: playwright-report/data/<hash>.zip
 */
export function findTraceZipsInReport(reportDir: string): string[] {
  const dataDir = path.join(reportDir, 'data');
  if (!fs.existsSync(dataDir)) {
    return [];
  }

  return fs.readdirSync(dataDir)
    .filter(f => f.endsWith('.zip'))
    .map(f => path.join(dataDir, f));
}

/**
 * Validate that a directory looks like a Playwright HTML report.
 */
export function isPlaywrightReportDir(dir: string): boolean {
  return fs.existsSync(path.join(dir, 'index.html')) && fs.existsSync(path.join(dir, 'data'));
}
```

- [ ] **Step 2: Create zip-source.ts**

This creates a `TraceLoaderBackend` from a ZIP file path, using Playwright's `DirTraceLoaderBackend` after extraction.

Create `packages/snapshot-saver/src/sources/zip-source.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

// Import Playwright's trace extraction utility
let extractTrace: (traceFile: string, outDir: string) => Promise<void>;
let DirTraceLoaderBackend: new (dir: string) => any;

try {
  const traceParser = require('playwright-core/lib/tools/trace/traceParser');
  extractTrace = traceParser.extractTrace;
  DirTraceLoaderBackend = traceParser.DirTraceLoaderBackend;
} catch {
  // Will throw at runtime if used
}

/**
 * Extract a trace ZIP to a temp directory and create a loader backend.
 */
export async function createBackendFromZip(zipPath: string): Promise<{
  backend: any;
  cleanup: () => void;
}> {
  if (!extractTrace || !DirTraceLoaderBackend) {
    throw new Error('Could not import Playwright trace parser. Ensure playwright-core is installed.');
  }

  if (!fs.existsSync(zipPath)) {
    throw new Error(`Trace file not found: ${zipPath}`);
  }

  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-snapshot-'));
  await extractTrace(zipPath, tmpDir);

  return {
    backend: new DirTraceLoaderBackend(tmpDir),
    cleanup: () => fs.rmSync(tmpDir, { recursive: true, force: true }),
  };
}
```

- [ ] **Step 3: Create url-source.ts**

This fetches a hosted Playwright report, extracts report data, and downloads trace ZIPs.

Create `packages/snapshot-saver/src/sources/url-source.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

/**
 * Download trace ZIPs from a hosted Playwright HTML report.
 *
 * Playwright HTML reports embed report data as base64 in a script tag
 * with id="playwrightReportBase64" or load it from data/ directory.
 */
export async function downloadTracesFromUrl(reportUrl: string): Promise<{
  zipPaths: string[];
  cleanup: () => void;
}> {
  const baseUrl = reportUrl.endsWith('/') ? reportUrl.slice(0, -1) : reportUrl;
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-snapshot-url-'));
  const zipPaths: string[] = [];

  try {
    // Try fetching the report index to find trace attachments
    const indexResponse = await fetch(`${baseUrl}/index.html`);
    if (!indexResponse.ok) {
      throw new Error(`Cannot connect to ${baseUrl}: ${indexResponse.status} ${indexResponse.statusText}`);
    }

    const indexHtml = await indexResponse.text();

    // Check for embedded report data (base64)
    const base64Match = indexHtml.match(/id="playwrightReportBase64"[^>]*>([^<]+)</);
    if (base64Match) {
      const reportData = JSON.parse(Buffer.from(base64Match[1], 'base64').toString('utf-8'));
      zipPaths.push(...(await downloadTraceAttachments(reportData, baseUrl, tmpDir)));
    } else {
      // Try data/ directory approach — list and download ZIPs
      zipPaths.push(...(await downloadFromDataDir(baseUrl, tmpDir)));
    }
  } catch (err) {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    throw err;
  }

  if (zipPaths.length === 0) {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    throw new Error(`No Playwright report data found at ${baseUrl}`);
  }

  return {
    zipPaths,
    cleanup: () => fs.rmSync(tmpDir, { recursive: true, force: true }),
  };
}

async function downloadTraceAttachments(
  reportData: any,
  baseUrl: string,
  tmpDir: string
): Promise<string[]> {
  const zipPaths: string[] = [];

  // Navigate report data to find trace attachments
  const files = reportData?.files ?? [];
  for (const file of files) {
    const tests = file?.tests ?? [];
    for (const t of tests) {
      const results = t?.results ?? [];
      for (const result of results) {
        const attachments = result?.attachments ?? [];
        for (const att of attachments) {
          if (att.name === 'trace' && att.path) {
            const traceUrl = `${baseUrl}/data/${att.path}`;
            const zipPath = path.join(tmpDir, path.basename(att.path));
            await downloadFile(traceUrl, zipPath);
            zipPaths.push(zipPath);
          }
        }
      }
    }
  }

  return zipPaths;
}

async function downloadFromDataDir(baseUrl: string, tmpDir: string): Promise<string[]> {
  // Attempt to fetch trace files from data/ directory
  // This is a fallback — the report may serve a directory listing or known paths
  const zipPaths: string[] = [];

  try {
    const dataResponse = await fetch(`${baseUrl}/data/`);
    if (dataResponse.ok) {
      const html = await dataResponse.text();
      // Parse directory listing for .zip files
      const zipLinks = html.match(/href="([^"]*\.zip)"/g) ?? [];
      for (const link of zipLinks) {
        const fileName = link.match(/href="([^"]*\.zip)"/)?.[1];
        if (fileName) {
          const zipPath = path.join(tmpDir, path.basename(fileName));
          await downloadFile(`${baseUrl}/data/${fileName}`, zipPath);
          zipPaths.push(zipPath);
        }
      }
    }
  } catch {
    // Directory listing not available
  }

  return zipPaths;
}

async function downloadFile(url: string, dest: string): Promise<void> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to download ${url}: ${response.status}`);
  }
  const buffer = Buffer.from(await response.arrayBuffer());
  fs.writeFileSync(dest, buffer);
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd packages/snapshot-saver && npx tsc --noEmit`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add packages/snapshot-saver/src/sources/
git commit -m "feat: add source loaders for report directory, ZIP, and URL"
```

---

### Task 5: Extractor

Create the `extractSnapshots()` function that ties together source detection, trace loading, and snapshot rendering.

**Files:**
- Create: `packages/snapshot-saver/src/extractor.ts`
- Modify: `packages/snapshot-saver/src/types.ts`
- Modify: `packages/snapshot-saver/src/index.ts`
- Create: `packages/snapshot-saver/tests/extractor.spec.ts`

- [ ] **Step 1: Add extractor types to types.ts**

Append to `packages/snapshot-saver/src/types.ts`:

```typescript

export interface ExtractOptions {
  /** Report directory, trace ZIP path, or URL */
  source: string;
  /** Output directory (default: '.snapshots') */
  outputDir?: string;
  /** Generate screenshot from trace screencast frame (default: true) */
  screenshot?: boolean;
  /** Generate manifest.json (default: true) */
  manifest?: boolean;
  /** Filter to extract only specific page/state */
  filter?: {
    page?: string;
    state?: string;
  };
}

export interface ExtractResult {
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
```

- [ ] **Step 2: Write the failing test**

Create `packages/snapshot-saver/tests/extractor.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import { extractSnapshots } from '../src/extractor';
import * as fs from 'fs';
import * as path from 'path';

const tmpDir = path.join(__dirname, '..', '.test-output-extractor');
const fixturesDir = path.join(__dirname, 'fixtures');

test.beforeEach(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.afterAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.describe('extractSnapshots', () => {
  test('extracts snapshots from a trace ZIP with markers', async () => {
    const traceZip = path.join(fixturesDir, 'sample-trace.zip');
    test.skip(!fs.existsSync(traceZip), 'sample-trace.zip fixture not available');

    const result = await extractSnapshots({
      source: traceZip,
      outputDir: tmpDir,
    });

    expect(result.snapshots.length).toBeGreaterThan(0);
    for (const snap of result.snapshots) {
      expect(fs.existsSync(snap.files.html)).toBe(true);
      const html = fs.readFileSync(snap.files.html, 'utf-8');
      expect(html.length).toBeGreaterThan(0);
    }
  });

  test('detects source type from path', async () => {
    // ZIP detection
    await expect(extractSnapshots({
      source: '/nonexistent/file.zip',
      outputDir: tmpDir,
    })).rejects.toThrow('Trace file not found');
  });

  test('rejects unreachable URLs', async () => {
    await expect(extractSnapshots({
      source: 'http://localhost:19999',
      outputDir: tmpDir,
    })).rejects.toThrow();
  });

  test('logs warning when no markers found', async () => {
    const traceZip = path.join(fixturesDir, 'no-markers-trace.zip');
    test.skip(!fs.existsSync(traceZip), 'no-markers-trace.zip fixture not available');

    const result = await extractSnapshots({
      source: traceZip,
      outputDir: tmpDir,
    });

    expect(result.snapshots).toHaveLength(0);
  });

  test('filter by page extracts only matching snapshots', async () => {
    const traceZip = path.join(fixturesDir, 'sample-trace.zip');
    test.skip(!fs.existsSync(traceZip), 'sample-trace.zip fixture not available');

    const result = await extractSnapshots({
      source: traceZip,
      outputDir: tmpDir,
      filter: { page: 'login' },
    });

    for (const snap of result.snapshots) {
      expect(snap.page).toBe('login');
    }
  });
});
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd packages/snapshot-saver && npx playwright test extractor`
Expected: FAIL — `extractor` module does not exist

- [ ] **Step 4: Implement extractor.ts**

Create `packages/snapshot-saver/src/extractor.ts`:

```typescript
import * as fs from 'fs';
import * as path from 'path';
import { ExtractOptions, ExtractResult } from './types';
import { loadTraceMarkers, renderSnapshotAtMarker, findScreencastFrame, TraceSnapshotMarker } from './trace/playwright-adapter';
import { createBackendFromZip } from './sources/zip-source';
import { findTraceZipsInReport, isPlaywrightReportDir } from './sources/directory-source';
import { downloadTracesFromUrl } from './sources/url-source';

type SourceType = 'zip' | 'directory' | 'url';

function detectSourceType(source: string): SourceType {
  if (source.startsWith('http://') || source.startsWith('https://')) {
    return 'url';
  }
  if (source.endsWith('.zip')) {
    return 'zip';
  }
  return 'directory';
}

export async function extractSnapshots(options: ExtractOptions): Promise<ExtractResult> {
  const outputDir = options.outputDir ?? '.snapshots';
  const sourceType = detectSourceType(options.source);

  let zipPaths: string[];
  let urlCleanup: (() => void) | undefined;

  switch (sourceType) {
    case 'zip':
      zipPaths = [options.source];
      break;
    case 'directory':
      if (!isPlaywrightReportDir(options.source)) {
        throw new Error(`Not a Playwright report directory: ${options.source}`);
      }
      zipPaths = findTraceZipsInReport(options.source);
      if (zipPaths.length === 0) {
        console.warn('Report contains no traces');
        return { snapshots: [] };
      }
      break;
    case 'url': {
      const result = await downloadTracesFromUrl(options.source);
      zipPaths = result.zipPaths;
      urlCleanup = result.cleanup;
      break;
    }
  }

  const snapshots: ExtractResult['snapshots'] = [];
  const seen = new Map<string, string>(); // "page/state" -> test name for duplicate detection

  try {
    for (const zipPath of zipPaths) {
      const { backend, cleanup: zipCleanup } = await createBackendFromZip(zipPath);

      try {
        const { markers, loader } = await loadTraceMarkers(backend);

        if (markers.length === 0) {
          console.warn(
            `No snapshot markers found in trace ${path.basename(zipPath)}. To mark snapshots, use:\n\n` +
            `  import { snapshot } from 'playwright-snapshot-saver';\n\n` +
            `  await snapshot({ page: 'login', state: 'error' });\n`
          );
          continue;
        }

        for (const marker of markers) {
          // Apply filter
          if (options.filter?.page && marker.page !== options.filter.page) continue;
          if (options.filter?.state && marker.state !== options.filter.state) continue;

          // Duplicate detection
          const key = `${marker.page}/${marker.state}`;
          if (seen.has(key)) {
            console.warn(`Snapshot ${key} written by multiple traces, overwriting`);
          }
          seen.set(key, zipPath);

          const snapshotDir = path.join(outputDir, marker.page, marker.state);
          fs.mkdirSync(snapshotDir, { recursive: true });

          // Render HTML
          const rendered = await renderSnapshotAtMarker(loader, marker);
          const htmlPath = path.join(snapshotDir, 'index.html');
          fs.writeFileSync(htmlPath, rendered.html, 'utf-8');

          const files: ExtractResult['snapshots'][0]['files'] = { html: htmlPath };

          // Screenshot from screencast frame
          if (options.screenshot !== false) {
            const frame = await findScreencastFrame(loader, marker);
            if (frame) {
              const screenshotPath = path.join(snapshotDir, 'screenshot.webp');
              fs.writeFileSync(screenshotPath, frame);
              files.screenshot = screenshotPath;
            }
          }

          // Manifest
          if (options.manifest !== false) {
            const manifest = {
              version: 1,
              url: '', // Not available from trace without additional parsing
              viewport: rendered.viewport,
              timestamp: new Date(marker.timestamp).toISOString(),
              playwright: 'unknown', // Could parse from trace metadata
              userAgent: '',
            };
            const manifestPath = path.join(snapshotDir, 'manifest.json');
            fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
            files.manifest = manifestPath;
          }

          snapshots.push({
            page: marker.page,
            state: marker.state,
            outputDir: snapshotDir,
            files,
          });
        }
      } finally {
        zipCleanup();
      }
    }
  } finally {
    urlCleanup?.();
  }

  return { snapshots };
}
```

- [ ] **Step 5: Export from index.ts**

Add to `packages/snapshot-saver/src/index.ts`:

```typescript
export { extractSnapshots } from './extractor';
export { ExtractOptions, ExtractResult } from './types';
```

- [ ] **Step 6: Run tests to verify**

Run: `cd packages/snapshot-saver && npx playwright test extractor`
Expected: Tests that have fixtures pass, tests without fixtures are skipped, error tests pass.

- [ ] **Step 7: Commit**

```bash
git add packages/snapshot-saver/src/extractor.ts packages/snapshot-saver/src/types.ts packages/snapshot-saver/src/index.ts packages/snapshot-saver/tests/extractor.spec.ts
git commit -m "feat: add extractSnapshots() with directory, ZIP, and URL sources"
```

---

### Task 6: Reporter

Create the Playwright ReporterV2 that detects snapshot markers in test steps and extracts snapshots from traces.

**Files:**
- Create: `packages/snapshot-saver/src/reporter.ts`
- Create: `packages/snapshot-saver/tests/reporter.spec.ts`
- Modify: `packages/snapshot-saver/playwright.config.ts`

- [ ] **Step 1: Write the failing test**

Create `packages/snapshot-saver/tests/reporter.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as childProcess from 'child_process';

const tmpDir = path.join(__dirname, '..', '.test-output-reporter');
const fixturesDir = path.join(__dirname, 'fixtures');

test.beforeEach(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.afterAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.describe('snapshot reporter', () => {
  test('extracts snapshots from trace after test with markers', async () => {
    // Create a minimal test file that uses snapshot markers
    const testDir = path.join(tmpDir, 'test-project');
    fs.mkdirSync(testDir, { recursive: true });

    const configContent = `
import { defineConfig } from '@playwright/test';
export default defineConfig({
  use: { trace: 'on' },
  reporter: [
    ['${path.resolve(__dirname, '..', 'src', 'reporter.ts').replace(/\\/g, '/')}', {
      outputDir: '${path.join(tmpDir, 'snapshots').replace(/\\/g, '/')}',
    }],
  ],
  webServer: {
    command: 'npx serve ${path.resolve(__dirname, '..', '..', '..', 'test-project', 'fixtures').replace(/\\/g, '/')} -l 8099 --no-clipboard',
    port: 8099,
    reuseExistingServer: true,
  },
});
`;
    fs.writeFileSync(path.join(testDir, 'playwright.config.ts'), configContent);

    const testContent = `
import { test } from '@playwright/test';
import { snapshot } from '${path.resolve(__dirname, '..', 'src', 'snapshot-marker.ts').replace(/\\/g, '/')}';

test('login page', async ({ page }) => {
  await page.goto('http://localhost:8099/login.html');
  await snapshot({ page: 'login' });
  await snapshot({ page: 'login', state: 'loaded' });
});
`;
    fs.mkdirSync(path.join(testDir, 'tests'), { recursive: true });
    fs.writeFileSync(path.join(testDir, 'tests', 'example.spec.ts'), testContent);

    // Run playwright test
    const result = childProcess.spawnSync('npx', ['playwright', 'test'], {
      cwd: testDir,
      stdio: 'pipe',
      encoding: 'utf-8',
      timeout: 60000,
      shell: true,
    });

    // Check reporter output
    const snapshotsDir = path.join(tmpDir, 'snapshots');
    expect(fs.existsSync(path.join(snapshotsDir, 'login', 'main', 'index.html'))).toBe(true);
    expect(fs.existsSync(path.join(snapshotsDir, 'login', 'loaded', 'index.html'))).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd packages/snapshot-saver && npx playwright test reporter`
Expected: FAIL — `reporter.ts` does not exist

- [ ] **Step 3: Implement reporter.ts**

Create `packages/snapshot-saver/src/reporter.ts`:

```typescript
import type {
  FullConfig,
  FullResult,
  Reporter,
  Suite,
  TestCase,
  TestResult,
  TestStep,
} from '@playwright/test/reporter';
import * as path from 'path';
import { extractSnapshots } from './extractor';

interface SnapshotReporterOptions {
  outputDir?: string;
  screenshot?: boolean;
  manifest?: boolean;
}

interface CollectedMarker {
  page: string;
  state: string;
  testTitle: string;
  tracePath?: string;
}

const MARKER_REGEX = /^\[snapshot:([a-zA-Z0-9_-]+)\/([a-zA-Z0-9_-]+)\]$/;

class SnapshotReporter implements Reporter {
  private options: SnapshotReporterOptions;
  private markers: CollectedMarker[] = [];
  private tracingEnabled = false;

  constructor(options: SnapshotReporterOptions = {}) {
    this.options = options;
  }

  onBegin(config: FullConfig, suite: Suite): void {
    // Check if tracing is enabled in any project
    for (const project of config.projects) {
      const trace = project.use?.trace;
      if (trace && trace !== 'off') {
        this.tracingEnabled = true;
        break;
      }
    }

    if (!this.tracingEnabled) {
      console.warn(
        '[snapshot-reporter] Warning: Tracing is not enabled. ' +
        'Set trace: "on" in playwright.config.ts to enable snapshot extraction.'
      );
    }
  }

  onTestEnd(test: TestCase, result: TestResult): void {
    if (!this.tracingEnabled) return;

    // Find trace attachment
    const traceAttachment = result.attachments.find(a => a.name === 'trace');
    const tracePath = traceAttachment?.path;

    // Scan steps for snapshot markers
    const scanSteps = (steps: TestStep[]) => {
      for (const step of steps) {
        const match = MARKER_REGEX.exec(step.title);
        if (match) {
          this.markers.push({
            page: match[1],
            state: match[2],
            testTitle: test.title,
            tracePath,
          });
        }
        if (step.steps) {
          scanSteps(step.steps);
        }
      }
    };

    scanSteps(result.steps);
  }

  async onEnd(result: FullResult): Promise<void> {
    if (this.markers.length === 0) return;

    const outputDir = this.options.outputDir ?? '.snapshots';

    // Group markers by trace file
    const byTrace = new Map<string, CollectedMarker[]>();
    for (const marker of this.markers) {
      if (!marker.tracePath) {
        console.warn(
          `[snapshot-reporter] Snapshot marker '${marker.page}/${marker.state}' ` +
          `skipped — no trace file for test "${marker.testTitle}"`
        );
        continue;
      }
      const existing = byTrace.get(marker.tracePath) ?? [];
      existing.push(marker);
      byTrace.set(marker.tracePath, existing);
    }

    // Extract snapshots from each trace
    for (const [tracePath] of byTrace) {
      try {
        await extractSnapshots({
          source: tracePath,
          outputDir,
          screenshot: this.options.screenshot,
          manifest: this.options.manifest,
        });
      } catch (err) {
        console.error(`[snapshot-reporter] Failed to extract from ${tracePath}:`, err);
      }
    }

    console.log(`[snapshot-reporter] Extracted ${this.markers.length} snapshot(s) to ${outputDir}`);
  }
}

export default SnapshotReporter;
```

- [ ] **Step 4: Run tests to verify**

Run: `cd packages/snapshot-saver && npx playwright test reporter`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add packages/snapshot-saver/src/reporter.ts packages/snapshot-saver/tests/reporter.spec.ts
git commit -m "feat: add Playwright reporter for trace-based snapshot extraction"
```

---

### Task 7: CLI

Create the CLI entry point for `npx playwright-snapshot-saver extract`.

**Files:**
- Create: `packages/snapshot-saver/src/cli.ts`
- Create: `packages/snapshot-saver/bin/cli.js`
- Modify: `packages/snapshot-saver/package.json`

- [ ] **Step 1: Create cli.ts**

Create `packages/snapshot-saver/src/cli.ts`:

```typescript
import { extractSnapshots } from './extractor';

async function main() {
  const args = process.argv.slice(2);

  if (args[0] !== 'extract') {
    console.error('Usage: playwright-snapshot-saver extract --source <path-or-url> [options]');
    console.error('');
    console.error('Commands:');
    console.error('  extract    Extract snapshots from Playwright traces');
    process.exit(1);
  }

  const parsed = parseArgs(args.slice(1));

  if (!parsed.source) {
    console.error('Error: --source is required');
    console.error('');
    console.error('Usage: playwright-snapshot-saver extract --source <path-or-url> [options]');
    console.error('');
    console.error('Options:');
    console.error('  --source <path|url>   Report directory, trace ZIP, or hosted report URL');
    console.error('  --output <dir>        Output directory (default: .snapshots)');
    console.error('  --page <name>         Filter by page name');
    console.error('  --state <name>        Filter by state name');
    console.error('  --no-screenshot       Skip screenshot generation');
    console.error('  --no-manifest         Skip manifest.json generation');
    process.exit(1);
  }

  try {
    const result = await extractSnapshots({
      source: parsed.source,
      outputDir: parsed.output ?? '.snapshots',
      screenshot: parsed.screenshot,
      manifest: parsed.manifest,
      filter: {
        page: parsed.page,
        state: parsed.state,
      },
    });

    if (result.snapshots.length === 0) {
      console.log('No snapshots extracted.');
    } else {
      console.log(`Extracted ${result.snapshots.length} snapshot(s):`);
      for (const snap of result.snapshots) {
        console.log(`  ${snap.page}/${snap.state} → ${snap.outputDir}`);
      }
    }
  } catch (err: any) {
    console.error(`Error: ${err.message}`);
    process.exit(1);
  }
}

function parseArgs(args: string[]): {
  source?: string;
  output?: string;
  page?: string;
  state?: string;
  screenshot: boolean;
  manifest: boolean;
} {
  const result: any = { screenshot: true, manifest: true };

  for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
      case '--source':
        result.source = args[++i];
        break;
      case '--output':
        result.output = args[++i];
        break;
      case '--page':
        result.page = args[++i];
        break;
      case '--state':
        result.state = args[++i];
        break;
      case '--no-screenshot':
        result.screenshot = false;
        break;
      case '--no-manifest':
        result.manifest = false;
        break;
      default:
        console.error(`Unknown option: ${args[i]}`);
        process.exit(1);
    }
  }

  return result;
}

main();
```

- [ ] **Step 2: Create bin/cli.js**

```bash
mkdir -p packages/snapshot-saver/bin
```

Create `packages/snapshot-saver/bin/cli.js`:

```javascript
#!/usr/bin/env node
require('../dist/cli.js');
```

- [ ] **Step 3: Update package.json**

Update `packages/snapshot-saver/package.json` — add `bin`, `exports`, and ensure `playwright-core` is a peer dependency:

```json
{
  "name": "playwright-snapshot-saver",
  "version": "0.2.0",
  "description": "Capture and extract Playwright page snapshots for Page Mirror IntelliJ plugin",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "exports": {
    ".": "./dist/index.js",
    "./reporter": "./dist/reporter.js"
  },
  "bin": {
    "playwright-snapshot-saver": "./bin/cli.js"
  },
  "files": [
    "dist/",
    "bin/"
  ],
  "scripts": {
    "build": "tsc",
    "test": "playwright test"
  },
  "peerDependencies": {
    "@playwright/test": ">=1.40.0",
    "playwright-core": ">=1.40.0"
  },
  "devDependencies": {
    "@playwright/test": "^1.49.0",
    "@types/node": "^25.5.0",
    "playwright-core": "^1.49.0",
    "serve": "^14.2.0",
    "typescript": "^5.3.0"
  },
  "keywords": [
    "playwright",
    "snapshot",
    "page-mirror",
    "intellij",
    "trace"
  ],
  "license": "MIT"
}
```

- [ ] **Step 4: Build and verify CLI**

Run:
```bash
cd packages/snapshot-saver && npm run build && node bin/cli.js
```
Expected: Prints usage message with `extract` command help

Run:
```bash
node bin/cli.js extract
```
Expected: Prints error "Error: --source is required" with option help

- [ ] **Step 5: Commit**

```bash
git add packages/snapshot-saver/src/cli.ts packages/snapshot-saver/bin/cli.js packages/snapshot-saver/package.json
git commit -m "feat: add CLI for snapshot extraction"
```

---

### Task 8: Generate Test Fixtures

Create real trace ZIP fixtures by running Playwright tests with tracing and snapshot markers. These fixtures are used by extractor and reporter tests.

**Files:**
- Create: `packages/snapshot-saver/tests/fixtures/generate-fixtures.ts`
- Create: `packages/snapshot-saver/tests/fixtures/sample-trace.zip` (generated)
- Create: `packages/snapshot-saver/tests/fixtures/no-markers-trace.zip` (generated)

- [ ] **Step 1: Create fixture generation script**

Create `packages/snapshot-saver/tests/fixtures/generate-fixtures.ts`:

```typescript
import { chromium } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Generate trace ZIP fixtures for testing.
 * Run with: npx tsx tests/fixtures/generate-fixtures.ts
 * Requires the test webserver to be running on port 8089.
 */
async function main() {
  const fixturesDir = __dirname;

  const browser = await chromium.launch();

  // Fixture 1: Trace with snapshot markers
  {
    const context = await browser.newContext({
      viewport: { width: 1280, height: 720 },
    });
    await context.tracing.start({ snapshots: true, screenshots: true });

    const page = await context.newPage();
    await page.goto('http://localhost:8089/login.html');

    // Simulate snapshot markers using test.step-like pattern
    await page.evaluate(() => {
      // This simulates what test.step does in the trace
    });

    // Create steps that look like snapshot markers
    // Since we can't use test.step outside of a test, we'll use tracing API directly
    await context.tracing.startChunk({ title: '[snapshot:login/main]' });
    await context.tracing.stopChunk();

    await page.fill('input[name="username"]', 'testuser');
    await page.fill('input[name="password"]', 'wrong');

    await context.tracing.startChunk({ title: '[snapshot:login/error]' });
    await context.tracing.stopChunk();

    await context.tracing.stop({
      path: path.join(fixturesDir, 'sample-trace.zip'),
    });
    await context.close();
  }

  // Fixture 2: Trace without markers
  {
    const context = await browser.newContext({
      viewport: { width: 1280, height: 720 },
    });
    await context.tracing.start({ snapshots: true, screenshots: true });

    const page = await context.newPage();
    await page.goto('http://localhost:8089/login.html');
    await page.fill('input[name="username"]', 'user');

    await context.tracing.stop({
      path: path.join(fixturesDir, 'no-markers-trace.zip'),
    });
    await context.close();
  }

  await browser.close();
  console.log('Fixtures generated successfully');
}

main().catch(console.error);
```

- [ ] **Step 2: Run the fixture generator**

First ensure the test server is available:
```bash
cd packages/snapshot-saver && npx serve ../../test-project/fixtures -l 8089 --no-clipboard &
```

Then generate:
```bash
cd packages/snapshot-saver && npx tsx tests/fixtures/generate-fixtures.ts
```

Expected: Two files created:
- `tests/fixtures/sample-trace.zip`
- `tests/fixtures/no-markers-trace.zip`

- [ ] **Step 3: Verify fixture files exist**

```bash
ls -la packages/snapshot-saver/tests/fixtures/*.zip
```

Expected: Both ZIP files present and non-empty

- [ ] **Step 4: Run the full test suite**

Run: `cd packages/snapshot-saver && npx playwright test`
Expected: All tests pass (extractor tests no longer skip)

- [ ] **Step 5: Commit**

```bash
git add packages/snapshot-saver/tests/fixtures/
git commit -m "test: add trace ZIP fixtures for extractor tests"
```

---

### Task 9: GitHub Action for Playwright Compatibility

Create the daily CI workflow that checks compatibility with the latest Playwright version.

**Files:**
- Create: `.github/workflows/playwright-compat.yml`

- [ ] **Step 1: Create the workflow file**

```bash
mkdir -p .github/workflows
```

Create `.github/workflows/playwright-compat.yml`:

```yaml
name: Playwright Compatibility Check

on:
  schedule:
    - cron: '0 8 * * *'  # Daily at 08:00 UTC
  workflow_dispatch:       # Manual trigger

jobs:
  check-compatibility:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: Get latest Playwright version
        id: version
        run: |
          LATEST=$(npm view playwright version)
          echo "latest=$LATEST" >> "$GITHUB_OUTPUT"
          echo "Latest Playwright version: $LATEST"

      - name: Install dependencies
        working-directory: packages/snapshot-saver
        run: |
          npm install
          npm install playwright@${{ steps.version.outputs.latest }} playwright-core@${{ steps.version.outputs.latest }} @playwright/test@${{ steps.version.outputs.latest }}
          npx playwright install chromium

      - name: Run tests
        working-directory: packages/snapshot-saver
        run: npx playwright test

      - name: Create issue on failure
        if: failure()
        uses: actions/github-script@v7
        with:
          script: |
            const version = '${{ steps.version.outputs.latest }}';
            const title = `Playwright ${version} compatibility broken`;

            // Check if issue already exists
            const issues = await github.rest.issues.listForRepo({
              owner: context.repo.owner,
              repo: context.repo.repo,
              state: 'open',
              labels: 'playwright-compat',
            });

            const existing = issues.data.find(i => i.title === title);
            if (existing) {
              console.log(`Issue already exists: #${existing.number}`);
              return;
            }

            await github.rest.issues.create({
              owner: context.repo.owner,
              repo: context.repo.repo,
              title,
              body: `The snapshot-saver package tests failed with Playwright ${version}.\n\nPlease check the [workflow run](${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}) for details.\n\nThe Playwright adapter (\`src/trace/playwright-adapter.ts\`) may need updating for internal API changes.`,
              labels: ['playwright-compat', 'bug'],
            });
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/playwright-compat.yml
git commit -m "ci: add daily Playwright compatibility check"
```

---

### Task 10: Integration Test — End-to-End

Create an end-to-end test that runs a real Playwright test with the reporter, then verifies the extracted snapshot bundles.

**Files:**
- Create: `packages/snapshot-saver/tests/e2e.spec.ts`

- [ ] **Step 1: Write the end-to-end test**

Create `packages/snapshot-saver/tests/e2e.spec.ts`:

```typescript
import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as childProcess from 'child_process';

const tmpDir = path.join(__dirname, '..', '.test-output-e2e');
const snapshotsDir = path.join(tmpDir, 'snapshots');

test.beforeAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.afterAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.describe('end-to-end', () => {
  test('reporter extracts snapshots from test with markers', async () => {
    const testDir = path.join(tmpDir, 'project');
    const testsDir = path.join(testDir, 'tests');
    fs.mkdirSync(testsDir, { recursive: true });

    // Write playwright config with reporter
    const reporterPath = path.resolve(__dirname, '..', 'dist', 'reporter.js').replace(/\\/g, '/');
    const fixturesPath = path.resolve(__dirname, '..', '..', '..', 'test-project', 'fixtures').replace(/\\/g, '/');

    fs.writeFileSync(path.join(testDir, 'playwright.config.ts'), `
import { defineConfig } from '@playwright/test';
export default defineConfig({
  use: { trace: 'on' },
  reporter: [['${reporterPath}', { outputDir: '${snapshotsDir.replace(/\\/g, '/')}' }]],
  webServer: {
    command: 'npx serve ${fixturesPath} -l 8098 --no-clipboard',
    port: 8098,
    reuseExistingServer: true,
  },
});
`);

    // Write test file with snapshot markers
    const markerPath = path.resolve(__dirname, '..', 'dist', 'snapshot-marker.js').replace(/\\/g, '/');

    fs.writeFileSync(path.join(testsDir, 'login.spec.ts'), `
import { test, expect } from '@playwright/test';

// Import snapshot marker
const { snapshot } = require('${markerPath}');

test('login page snapshots', async ({ page }) => {
  await page.goto('http://localhost:8098/login.html');
  await snapshot({ page: 'login' });

  await page.fill('input[name="username"]', 'wrong');
  await page.fill('input[name="password"]', 'bad');
  await page.click('button[type="submit"]');

  await snapshot({ page: 'login', state: 'error' });
});
`);

    // Build first
    childProcess.execSync('npm run build', {
      cwd: path.resolve(__dirname, '..'),
      stdio: 'pipe',
    });

    // Run playwright test
    const result = childProcess.spawnSync('npx', ['playwright', 'test'], {
      cwd: testDir,
      stdio: 'pipe',
      encoding: 'utf-8',
      timeout: 60000,
      shell: true,
    });

    // Verify snapshots were extracted
    const loginMainHtml = path.join(snapshotsDir, 'login', 'main', 'index.html');
    const loginErrorHtml = path.join(snapshotsDir, 'login', 'error', 'index.html');

    expect(fs.existsSync(loginMainHtml), `Expected ${loginMainHtml} to exist. Stdout: ${result.stdout}. Stderr: ${result.stderr}`).toBe(true);
    expect(fs.existsSync(loginErrorHtml), `Expected ${loginErrorHtml} to exist`).toBe(true);

    // Verify HTML content is non-trivial
    const mainHtml = fs.readFileSync(loginMainHtml, 'utf-8');
    expect(mainHtml.length).toBeGreaterThan(100);
    expect(mainHtml).toContain('<html');

    const errorHtml = fs.readFileSync(loginErrorHtml, 'utf-8');
    expect(errorHtml.length).toBeGreaterThan(100);
  });
});
```

- [ ] **Step 2: Build the package**

Run: `cd packages/snapshot-saver && npm run build`
Expected: Compiles successfully to `dist/`

- [ ] **Step 3: Run the e2e test**

Run: `cd packages/snapshot-saver && npx playwright test e2e`
Expected: PASS — snapshots extracted, HTML files exist and contain real content

- [ ] **Step 4: Run the full test suite**

Run: `cd packages/snapshot-saver && npx playwright test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add packages/snapshot-saver/tests/e2e.spec.ts
git commit -m "test: add end-to-end test for reporter + extractor pipeline"
```

---

### Task 11: Update package exports and documentation

Final cleanup: ensure all exports are correct, tsconfig includes new files, and the package builds cleanly.

**Files:**
- Modify: `packages/snapshot-saver/src/index.ts`
- Modify: `packages/snapshot-saver/tsconfig.json`

- [ ] **Step 1: Verify final index.ts exports**

Ensure `packages/snapshot-saver/src/index.ts` has all exports:

```typescript
import { Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { SaveSnapshotOptions, SnapshotResult } from './types';
import { generateInlinedHtml } from './html-inliner';
import { generateManifest } from './manifest-generator';

export { SaveSnapshotOptions, SnapshotResult, ManifestJson, SnapshotMarkerOptions, ExtractOptions, ExtractResult } from './types';
export { snapshot } from './snapshot-marker';
export { extractSnapshots } from './extractor';

export async function saveSnapshot(
  page: Page,
  options: SaveSnapshotOptions
): Promise<SnapshotResult> {
  // ... (unchanged from Task 1)
}
```

- [ ] **Step 2: Clean build**

Run:
```bash
cd packages/snapshot-saver
rm -rf dist
npm run build
```
Expected: Builds with no errors. `dist/` contains all compiled files including `reporter.js`, `cli.js`, `trace/`, `sources/`

- [ ] **Step 3: Verify package exports work**

Run:
```bash
node -e "const pkg = require('./dist/index.js'); console.log(Object.keys(pkg))"
```
Expected: Shows `saveSnapshot`, `snapshot`, `extractSnapshots`, plus type exports

Run:
```bash
node -e "const r = require('./dist/reporter.js'); console.log(typeof r.default)"
```
Expected: Shows `function`

- [ ] **Step 4: Run full test suite one final time**

Run: `cd packages/snapshot-saver && npx playwright test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add packages/snapshot-saver/
git commit -m "chore: finalize package exports and clean build"
```
