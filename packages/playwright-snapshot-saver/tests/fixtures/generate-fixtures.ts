/**
 * Fixture generator script.
 *
 * Generates trace ZIP files for use in extractor and adapter tests.
 * Uses Playwright's test runner API (test.step) to create real snapshot
 * markers identical to what snapshot() produces.
 *
 * Run manually:
 *   npx playwright test tests/fixtures/generate-fixtures.ts --config tests/fixtures/fixtures.config.ts
 *
 * Or via npm script:
 *   npm run generate-fixtures
 *
 * Output:
 *   tests/fixtures/sample-trace.zip    — trace with two [snapshot:...] markers
 *   tests/fixtures/no-markers-trace.zip — trace with no markers (just page interactions)
 */

import { test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const FIXTURES_DIR = __dirname;

// ---------------------------------------------------------------------------
// Fixture 1: Trace with snapshot markers
// ---------------------------------------------------------------------------

test.describe('with-markers', () => {
  test('login page with snapshot markers', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    // First snapshot marker — initial state.
    // The page.evaluate() inside the step forces Playwright to capture
    // a DOM snapshot as a child action, which provides the afterSnapshot
    // field needed by the adapter to render the snapshot HTML.
    await test.step('[snapshot:login/main]', async () => {
      await page.evaluate(() => {});
    });

    // Interact with the page
    await page.fill('input[name="username"]', 'testuser');
    await page.fill('input[name="password"]', 'wrongpassword');
    await page.click('button[type="submit"]');

    // Wait for error flash to appear
    await page.waitForSelector('#flash.error');

    // Second snapshot marker — error state
    await test.step('[snapshot:login/error]', async () => {
      await page.evaluate(() => {});
    });
  });
});

// ---------------------------------------------------------------------------
// Fixture 2: Trace without markers
// ---------------------------------------------------------------------------

test.describe('no-markers', () => {
  test('login page without markers', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');
    await page.fill('input[name="username"]', 'someuser');
    // Just interact, no snapshot markers
  });
});
