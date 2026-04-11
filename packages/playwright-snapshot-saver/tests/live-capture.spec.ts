import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { saveSnapshot } from '../src/index';

/**
 * Live-capture integration test. Exercises the framework-agnostic
 * `@pagemirror/snapshot-core` pipeline end-to-end via the real
 * `saveSnapshot(page, options)` entry point the Playwright adapter
 * provides — i.e. the same path a developer's test would use.
 *
 * Hits a realistic fixture with four external `<link rel="stylesheet">`
 * tags so the assembler emits v2 resources/<sha1>.css sidecars, and
 * asserts the on-disk bundle matches the expected layout.
 */

const tmpDir = path.join(__dirname, '..', '.test-output-live');

test.beforeEach(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.afterAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.describe('live capture via PlaywrightAdapter', () => {
  test('produces v2 bundle with CSS sidecar resources for app.html', async ({ page }) => {
    await page.goto('http://localhost:8089/app.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      group: 'dashboard',
      name: 'initial',
    });

    // --- layout checks --------------------------------------------------
    expect(result.outputDir).toBe(path.join(tmpDir, 'dashboard', 'initial'));
    expect(fs.existsSync(result.files.html)).toBe(true);
    expect(fs.existsSync(result.files.manifest!)).toBe(true);

    const topLevel = fs.readdirSync(result.outputDir).sort();
    expect(topLevel).toContain('index.html');
    expect(topLevel).toContain('manifest.json');
    expect(topLevel).toContain('resources');

    // v2 MUST NOT write screenshot.* at the top level.
    expect(topLevel.some((f) => f.startsWith('screenshot.'))).toBe(false);

    // --- resources dir --------------------------------------------------
    const resourcesDir = path.join(result.outputDir, 'resources');
    const resourceFiles = fs.readdirSync(resourcesDir);

    // PlaywrightAdapter writes screenshot.png by default.
    expect(resourceFiles).toContain('screenshot.png');

    // app.html links four external stylesheets: reset, theme, layout,
    // components. Inline stylesheets collected by the browser collector
    // may add more — but we expect AT LEAST the four linked ones as
    // distinct sidecars, named <sha1-16>.css.
    const cssSidecars = resourceFiles.filter((f) => /^[a-f0-9]{16}\.css$/.test(f));
    expect(cssSidecars.length).toBeGreaterThanOrEqual(4);

    // Every sidecar should have non-empty content.
    for (const name of cssSidecars) {
      const contents = fs.readFileSync(path.join(resourcesDir, name), 'utf-8');
      expect(contents.length).toBeGreaterThan(0);
    }

    // --- index.html rewritten to resources/ paths ----------------------
    const html = fs.readFileSync(result.files.html, 'utf-8');
    expect(html.startsWith('<!DOCTYPE html>')).toBe(true);

    // Every <link rel="stylesheet"> in index.html must now point at
    // resources/<sha1>.css, never the original relative path.
    expect(html).not.toContain('href="styles/reset.css"');
    expect(html).not.toContain('href="styles/theme.css"');
    expect(html).not.toContain('href="styles/layout.css"');
    expect(html).not.toContain('href="styles/components.css"');
    const rewrittenLinks = Array.from(html.matchAll(/href="(resources\/[a-f0-9]{16}\.css)"/g));
    expect(rewrittenLinks.length).toBeGreaterThanOrEqual(4);

    // Sanity: the rewritten sidecars in the HTML actually exist on disk.
    for (const match of rewrittenLinks) {
      const rel = match[1];
      expect(fs.existsSync(path.join(result.outputDir, rel))).toBe(true);
    }

    // --- manifest -------------------------------------------------------
    const manifest = JSON.parse(fs.readFileSync(result.files.manifest!, 'utf-8'));
    expect(manifest.version).toBe(2);
    expect(manifest.url).toContain('app.html');
    expect(manifest.viewport).toEqual({ width: 1280, height: 720 });
    expect(manifest.playwright).toBeTruthy();
    expect(manifest.userAgent).toBeTruthy();
    expect(manifest.timestamp).toBeTruthy();
    // v2 dropped Task 11's per-write counter — version is pinned.
    expect(manifest.version).not.toBe(1);
  });

  test('re-running on unchanged page does not rewrite files', async ({ page }) => {
    await page.goto('http://localhost:8089/app.html');

    const first = await saveSnapshot(page, {
      outputDir: tmpDir,
      group: 'dashboard',
      name: 'cache',
    });
    const htmlMtime1 = fs.statSync(first.files.html).mtimeMs;

    // Give the filesystem enough resolution that a real rewrite would bump mtime.
    await new Promise((r) => setTimeout(r, 50));

    const second = await saveSnapshot(page, {
      outputDir: tmpDir,
      group: 'dashboard',
      name: 'cache',
    });
    const htmlMtime2 = fs.statSync(second.files.html).mtimeMs;

    expect(htmlMtime2).toBe(htmlMtime1);
    expect(second.files.resources.length).toBe(first.files.resources.length);
  });

  test('screenshot: false omits the screenshot resource', async ({ page }) => {
    await page.goto('http://localhost:8089/app.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      group: 'dashboard',
      name: 'no-screenshot',
      screenshot: false,
    });

    const resourcesDir = path.join(result.outputDir, 'resources');
    const resourceFiles = fs.readdirSync(resourcesDir);
    expect(resourceFiles.some((f) => f.startsWith('screenshot.'))).toBe(false);
    // CSS sidecars are still written — the rich fixture has four.
    const cssSidecars = resourceFiles.filter((f) => /^[a-f0-9]{16}\.css$/.test(f));
    expect(cssSidecars.length).toBeGreaterThanOrEqual(4);
  });
});
