import { Page } from '@playwright/test';
import {
  saveSnapshot as coreSaveSnapshot,
  SaveSnapshotOptions,
  SnapshotResult,
} from '@pagemirror/snapshot-core';
import { PlaywrightAdapter } from './playwright-adapter';
import { getPlaywrightVersion } from './playwright-version';

// Re-export the trace-extraction pipeline (unchanged).
export { snapshot } from './snapshot-marker';
export { extractSnapshots } from './extractor';

// Re-export core types so TypeScript consumers don't need a second import.
export type {
  SaveSnapshotOptions,
  SnapshotResult,
  ManifestJson,
} from '@pagemirror/snapshot-core';
export type { SnapshotMarkerOptions, ExtractOptions, ExtractResult } from './types';

/**
 * Ergonomic live-capture entry point for Playwright users. Takes a
 * `Page` (the natural argument for a Playwright call site) and forwards
 * to `@pagemirror/snapshot-core`'s framework-agnostic `saveSnapshot`
 * via the local `PlaywrightAdapter`. Driver identity is auto-filled
 * from `@playwright/test`'s package.json so every manifest records
 * which Playwright version produced the bundle.
 */
export async function saveSnapshot(
  page: Page,
  options: SaveSnapshotOptions,
): Promise<SnapshotResult> {
  const withDriver: SaveSnapshotOptions = {
    ...options,
    driver: options.driver ?? { name: 'playwright', version: getPlaywrightVersion() },
  };
  return coreSaveSnapshot(new PlaywrightAdapter(page), withDriver);
}

// Re-export the adapter for users who want to call `coreSaveSnapshot`
// directly (e.g. test fixtures that stub Page).
export { PlaywrightAdapter };
