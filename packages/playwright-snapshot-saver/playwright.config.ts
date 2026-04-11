import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 30000,
  expect: { timeout: 10000 },
  retries: 0,
  workers: 1,
  // Three reporters in parallel:
  //   list   — human-readable console output (CI logs + local dev)
  //   html   — `playwright-report/index.html`, uploaded to the per-suite
  //            dashboard slot
  //   json   — `test-results/results.json`, consumed by buildSrc's
  //            ClaudeSummaryGenerator (Task 14)
  reporter: [
    ['list'],
    ['html', { open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  use: {
    viewport: { width: 1280, height: 720 },
    screenshot: 'off',
    trace: 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command: 'npx serve ../test-project/fixtures -l 8089 --no-clipboard',
    port: 8089,
    reuseExistingServer: true,
  },
});
