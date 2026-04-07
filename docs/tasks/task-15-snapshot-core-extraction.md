# Task 15: Extract Framework-Agnostic `snapshot-core`

> **Goal:** Split `playwright-snapshot-saver` into a framework-agnostic `snapshot-core` package plus a thin Playwright adapter, enabling future Selenium / Cypress / Appium adapters (Tasks 17, 20) without duplicating HTML inlining and manifest logic.
> **Depends on:** nothing (but best after Task 14 so refactor regressions are caught by the aggregator)
> **Output:** New `packages/snapshot-core/`, refactored `packages/playwright-snapshot-saver/` depending on it, stable public API.

## Motivation

Today, HTML inlining, manifest generation, and bundle layout logic all live inside `packages/playwright-snapshot-saver/src/`. Adding a Selenium or Cypress adapter would require copy-pasting these. A clean `PageAdapter` abstraction lets the core handle the universal work and each driver-specific package provide only a ~50-line adapter.

## Key Files

- `packages/playwright-snapshot-saver/src/index.ts` — public `saveSnapshot` entry point.
- `packages/playwright-snapshot-saver/src/html-inliner.ts` — move to `snapshot-core`.
- `packages/playwright-snapshot-saver/src/manifest-generator.ts` — move to `snapshot-core`.
- `packages/playwright-snapshot-saver/src/reporter.ts` — stays in Playwright package (framework-specific).
- `packages/playwright-snapshot-saver/tests/` — move inliner/manifest tests to `snapshot-core/tests/`.
- New: `packages/snapshot-core/` (new npm package, private workspace, published as `@pagemirror/snapshot-core`).

## `PageAdapter` Interface

```ts
export interface PageAdapter {
  getHTML(): Promise<string>;
  getCSS(): Promise<StylesheetData[]>;   // list of { href?: string, source: string }
  getURL(): Promise<string>;
  getViewport(): Promise<{ width: number; height: number }>;
  screenshot(options?: { format?: "webp" | "png" }): Promise<Buffer>;
  getUserAgent(): Promise<string | undefined>;
}

export interface SaveSnapshotOptions {
  outputDir: string;
  name: string;
  group?: string;
  screenshot?: "webp" | "png" | false;
  manifest?: boolean;
  extraSelectors?: string[];
  excludeSelectors?: string[];
  extraAttributes?: string[];
  driver?: { name: "playwright" | "selenium" | "cypress" | "appium"; version: string };
}

export async function saveSnapshot(adapter: PageAdapter, options: SaveSnapshotOptions): Promise<string>;
```

## Steps

1. Scaffold `packages/snapshot-core/` with `package.json`, `tsconfig.json`, `src/`, `tests/`, and add it to the root workspaces list.
2. Move `html-inliner.ts` and `manifest-generator.ts` verbatim into `packages/snapshot-core/src/`. Adjust imports.
3. Add `src/page-adapter.ts` defining `PageAdapter` and `SaveSnapshotOptions`.
4. Add `src/save-snapshot.ts` implementing the orchestration (call adapter → inline → write manifest → write screenshot → produce bundle per `docs/snapshot-bundle-spec.md`).
5. Move the subset of `playwright-snapshot-saver/tests/` that tests inliner and manifest into `snapshot-core/tests/`. Tests that exercise the full Playwright integration stay where they are.
6. In `packages/playwright-snapshot-saver/`, replace the moved files with a thin `PlaywrightAdapter` implementing `PageAdapter` against `@playwright/test`'s `Page` and re-export `saveSnapshot` from `snapshot-core` so the public API is unchanged.
7. Update `packages/playwright-snapshot-saver/package.json` to depend on `@pagemirror/snapshot-core` via workspace protocol.
8. Update `test-project/` if its `file:` dependency path changes.

## Verification

- `npm test` from the repo root runs both `snapshot-core` and `playwright-snapshot-saver` test suites green.
- `npm run build` in both packages succeeds.
- `test-project/` can still produce snapshots via Playwright runs identical to the pre-refactor output (byte-for-byte for a fixed fixture page).
- Public `import { saveSnapshot } from 'playwright-snapshot-saver'` still works.

## Out of Scope

- Selenium / Cypress adapters (→ Task 17).
- Appium / mobile (→ Task 20).
- Changing the bundle spec — if needed, bump `snapshot-bundle-spec.md` version first.
