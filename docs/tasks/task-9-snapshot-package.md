# Task 9: Snapshot Saver npm Package

> **Goal:** Extract snapshot-saving logic from `test-project/utils/save-state.ts` into a standalone, publishable npm package with a configurable API.
> **Depends on:** Task 0
> **Output:** `packages/snapshot-saver/` — a self-contained npm package consumable by any Playwright project

## Motivation

The snapshot capture logic (HTML inlining, layout generation, manifest creation) is currently embedded in the test project. To let other projects use Page Mirror, this must be a standalone package that any Playwright test suite can install and configure.

---

## Package Structure

```
packages/
  snapshot-saver/
    src/
      index.ts              # Public API: saveSnapshot()
      html-inliner.ts       # CSS inlining + DOM serialization
      layout-generator.ts   # Element discovery, selector generation, bounds capture
      manifest-generator.ts # Metadata (URL, viewport, timestamp, versions)
      types.ts              # Shared TypeScript interfaces
    tests/
      save-snapshot.spec.ts # Integration tests with Playwright
      layout-generator.spec.ts
    package.json
    tsconfig.json
    README.md
```

## Public API

### `saveSnapshot(page, options)`

```typescript
import { Page } from '@playwright/test';

interface SaveSnapshotOptions {
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
    format?: 'png' | 'webp'; // default: 'png'
  };

  /** Generate manifest.json */
  manifest?: boolean;        // default: true

  /** Additional CSS selectors to include in layout.json beyond the defaults */
  extraSelectors?: string[];

  /** Selectors to exclude from layout.json */
  excludeSelectors?: string[];

  /** Additional attribute keys to capture per element (beyond the defaults) */
  extraAttributes?: string[];
}

export async function saveSnapshot(
  page: Page,
  options: SaveSnapshotOptions
): Promise<SnapshotResult>;

interface SnapshotResult {
  /** Absolute path to the snapshot directory */
  outputDir: string;
  /** Number of elements captured in layout.json */
  elementCount: number;
  /** Paths to all generated files */
  files: {
    html: string;
    layout: string;
    screenshot?: string;
    manifest?: string;
  };
}
```

### Usage example

```typescript
import { test } from '@playwright/test';
import { saveSnapshot } from 'playwright-snapshot-saver';
import path from 'path';

test('login page', async ({ page }) => {
  await page.goto('/login');

  await saveSnapshot(page, {
    outputDir: path.join(__dirname, '..', '.snapshots'),
    group: 'login',
    name: 'initial',
  });

  // Trigger error state
  await page.fill('#username', 'wrong');
  await page.click('[data-testid="login-button"]');

  await saveSnapshot(page, {
    outputDir: path.join(__dirname, '..', '.snapshots'),
    group: 'login',
    name: 'error-state',
  });
});
```

Output:
```
.snapshots/login/
  initial/      { index.html, layout.json, screenshot.png, manifest.json }
  error-state/  { index.html, layout.json, screenshot.png, manifest.json }
```

---

## Module Responsibilities

### `html-inliner.ts`

Extracted from `generateInlinedHtml()` in current `save-state.ts`.

```typescript
export async function generateInlinedHtml(page: Page): Promise<string>
```

- Fetches all linked stylesheets and inlines them as `<style>` tags
- Removes `<link rel="stylesheet">` tags to prevent external dependencies
- Handles CORS failures gracefully (skip unresolvable stylesheets)
- Returns serialized `<!DOCTYPE html>` + `document.documentElement.outerHTML`

### `layout-generator.ts`

Extracted from `generateLayout()` in current `save-state.ts`.

```typescript
export async function generateLayout(
  page: Page,
  options?: {
    extraSelectors?: string[];
    excludeSelectors?: string[];
    extraAttributes?: string[];
  }
): Promise<LayoutJson>
```

- Default element selectors: `button, input, select, textarea, a, [role="button"], [role="link"], [tabindex], [id], [data-testid]`
- `extraSelectors` appended to the query (e.g., `[data-widget-id]`)
- `excludeSelectors` filtered out after query (e.g., `.debug-panel`)
- Default attribute keys: `type, name, placeholder, href, data-testid, id, role, aria-label, value`
- `extraAttributes` appended to attribute capture list
- `bestSelector()` algorithm unchanged: data-testid -> #id -> [name] -> parent-relative
- `inferRole()` algorithm unchanged: explicit role -> semantic tag mapping

### `manifest-generator.ts`

Extracted from `generateManifest()` in current `save-state.ts`.

```typescript
export async function generateManifest(page: Page): Promise<ManifestJson>
```

- Captures: URL, viewport, timestamp, Playwright version, user agent
- Playwright version detected from `@playwright/test/package.json`

### `types.ts`

```typescript
export interface LayoutJson {
  version: number;
  viewport: { width: number; height: number };
  elements: LayoutElement[];
}

export interface LayoutElement {
  selector: string;
  role: string | null;
  text: string;
  tag: string;
  bounds: { x: number; y: number; w: number; h: number };
  interactive: boolean;
  attributes: Record<string, string>;
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

---

## Contract with Kotlin Plugin

The Kotlin plugin discovers and consumes snapshots via `SnapshotBundle.fromDirectory()`. The npm package must produce files that satisfy this contract:

| File | Required | Plugin usage |
|------|----------|--------------|
| `index.html` | Yes | Loaded into JCEF iframe via `srcdoc`; parsed with Jsoup for gutter validation |
| `layout.json` | Yes | Parsed for element picker (`_layoutElements`); viewport dimensions for scaling |
| `screenshot.png` or `screenshot.webp` | No | Optional visual reference |
| `manifest.json` | No | Logged for diagnostics |

**Critical invariants:**
- Every `selector` in layout.json must resolve to an element in index.html via `document.querySelectorAll(selector)`
- `bounds` must be viewport-relative pixel coordinates from `getBoundingClientRect()`
- `viewport` dimensions in layout.json must match the actual page viewport at capture time
- `index.html` must be self-contained (all CSS inlined, no external dependencies)

---

## Package Configuration

### `package.json`

```json
{
  "name": "playwright-snapshot-saver",
  "version": "0.1.0",
  "description": "Capture Playwright page snapshots for Page Mirror IntelliJ plugin",
  "main": "dist/index.js",
  "types": "dist/index.d.ts",
  "files": ["dist/"],
  "scripts": {
    "build": "tsc",
    "test": "playwright test"
  },
  "peerDependencies": {
    "@playwright/test": ">=1.40.0"
  },
  "devDependencies": {
    "@playwright/test": "^1.49.0",
    "typescript": "^5.3.0"
  },
  "keywords": ["playwright", "snapshot", "page-mirror", "intellij"],
  "license": "MIT"
}
```

### `tsconfig.json`

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "commonjs",
    "lib": ["ES2020", "DOM"],
    "declaration": true,
    "outDir": "dist",
    "rootDir": "src",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true
  },
  "include": ["src/**/*.ts"],
  "exclude": ["tests", "dist"]
}
```

---

## Migration: Update test-project

After the package is created, update `test-project/` to consume it:

### Before (current)

```typescript
// test-project/tests/login.spec.ts
import { saveState } from '../utils/save-state';
await saveState(page, 'initial', snapshotsDir);
```

### After

```typescript
// test-project/tests/login.spec.ts
import { saveSnapshot } from 'playwright-snapshot-saver';
await saveSnapshot(page, {
  outputDir: snapshotsDir,
  group: 'login',
  name: 'initial',
});
```

- `test-project/utils/save-state.ts` is deleted
- `test-project/package.json` adds `playwright-snapshot-saver` as a dev dependency (local path: `"file:../packages/snapshot-saver"`)

---

## Monorepo Configuration

The project becomes a lightweight monorepo:

```
PageObjectPlugin/
  packages/
    snapshot-saver/        # npm package (TypeScript)
  test-project/            # Playwright tests (consumes snapshot-saver)
  src/                     # IntelliJ plugin (Kotlin)
  build.gradle.kts         # Kotlin plugin build (unchanged)
```

No monorepo tooling (nx, turborepo) needed. The test-project references the package via `file:` dependency. The Gradle build ignores the `packages/` directory entirely.

---

## Files to create/modify

| File | Change |
|------|--------|
| `packages/snapshot-saver/src/index.ts` | **New** — public API, `saveSnapshot()` |
| `packages/snapshot-saver/src/html-inliner.ts` | **New** — extracted from `save-state.ts` |
| `packages/snapshot-saver/src/layout-generator.ts` | **New** — extracted from `save-state.ts` |
| `packages/snapshot-saver/src/manifest-generator.ts` | **New** — extracted from `save-state.ts` |
| `packages/snapshot-saver/src/types.ts` | **New** — shared interfaces |
| `packages/snapshot-saver/package.json` | **New** |
| `packages/snapshot-saver/tsconfig.json` | **New** |
| `packages/snapshot-saver/tests/save-snapshot.spec.ts` | **New** — integration tests |
| `test-project/tests/login.spec.ts` | Update imports to use new package |
| `test-project/package.json` | Add `file:` dependency, remove `save-state` utils |
| `test-project/utils/save-state.ts` | **Delete** |

## Acceptance Criteria

- [x] `packages/snapshot-saver/` builds with `npm run build` and produces `dist/`
- [x] `saveSnapshot()` generates all 4 snapshot files (html, layout, screenshot, manifest)
- [x] Output satisfies `SnapshotBundle.fromDirectory()` contract (index.html + layout.json present)
- [x] Every `selector` in generated layout.json resolves in the generated index.html
- [x] `extraSelectors` option adds custom elements to layout.json
- [x] `excludeSelectors` option filters elements from layout.json
- [x] Screenshot format option works (png and jpeg)
- [x] `test-project/` uses the package via `file:` dependency and all tests pass
- [x] `test-project/utils/save-state.ts` is deleted
- [x] Kotlin plugin still discovers and loads snapshots from updated test-project
- [x] `./gradlew test` passes (plugin tests unaffected)
- [x] Package has its own integration tests with Playwright (10 tests)
