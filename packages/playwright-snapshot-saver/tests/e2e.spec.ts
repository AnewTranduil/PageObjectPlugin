import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as childProcess from 'child_process';

const tmpDir = path.join(__dirname, '..', '.test-output-e2e');
const snapshotsDir = path.join(tmpDir, 'snapshots');

test.beforeAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
  // Build the package first
  childProcess.execSync('npm run build', {
    cwd: path.resolve(__dirname, '..'),
    stdio: 'pipe',
  });
});

test.afterAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.describe('end-to-end', () => {
  test('reporter extracts snapshots from test with markers', async () => {
    const testDir = path.join(tmpDir, 'project');
    const testsDir = path.join(testDir, 'tests');
    fs.mkdirSync(testsDir, { recursive: true });

    // Use backslash-safe paths for Windows
    const reporterPath = path.resolve(__dirname, '..', 'dist', 'reporter.js').replace(/\\/g, '/');
    const markerPath = path.resolve(__dirname, '..', 'dist', 'snapshot-marker.js').replace(/\\/g, '/');
    const fixturesPath = path.resolve(__dirname, '..', '..', '..', 'test-project', 'fixtures').replace(/\\/g, '/');

    // Write config
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

    // Write test with snapshot markers
    fs.writeFileSync(path.join(testsDir, 'login.spec.ts'), `
import { test, expect } from '@playwright/test';
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

    // Run playwright test in the temp project
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

    expect(
      fs.existsSync(loginMainHtml),
      `Expected login/main/index.html to exist.\nstdout: ${result.stdout?.slice(-500)}\nstderr: ${result.stderr?.slice(-500)}`
    ).toBe(true);
    expect(
      fs.existsSync(loginErrorHtml),
      `Expected login/error/index.html to exist.\nstdout: ${result.stdout?.slice(-500)}\nstderr: ${result.stderr?.slice(-500)}`
    ).toBe(true);

    // Verify HTML is real content (Playwright trace snapshots use uppercase tags)
    const mainHtml = fs.readFileSync(loginMainHtml, 'utf-8');
    expect(mainHtml.length).toBeGreaterThan(100);
    expect(mainHtml.toLowerCase()).toContain('<html');
    expect(mainHtml.toLowerCase()).toContain('login');

    const errorHtml = fs.readFileSync(loginErrorHtml, 'utf-8');
    expect(errorHtml.length).toBeGreaterThan(100);
    expect(errorHtml.toLowerCase()).toContain('<html');
    expect(errorHtml.toLowerCase()).toContain('login');
  });
});
