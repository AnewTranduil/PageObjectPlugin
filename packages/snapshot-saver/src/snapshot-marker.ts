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
