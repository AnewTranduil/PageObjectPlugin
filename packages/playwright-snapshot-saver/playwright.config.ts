import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  timeout: 30000,
  expect: { timeout: 10000 },
  retries: 0,
  workers: 1,
  reporter: 'list',
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
