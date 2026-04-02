/**
 * Playwright config for generating test fixture trace ZIPs.
 *
 * Outputs traces to a temporary directory, then copies the relevant
 * trace.zip files to the fixtures directory with the expected names.
 */

import { defineConfig, devices } from '@playwright/test';
import * as path from 'path';

export default defineConfig({
  testDir: '.',
  testMatch: 'generate-fixtures.ts',
  timeout: 30000,
  retries: 0,
  workers: 1,
  reporter: 'list',
  outputDir: path.join(__dirname, '.fixture-results'),
  use: {
    viewport: { width: 1280, height: 720 },
    screenshot: 'off',
    trace: 'on',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    command: 'npx serve ../../../test-project/fixtures -l 8089 --no-clipboard',
    port: 8089,
    reuseExistingServer: true,
  },
});
