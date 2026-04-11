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

/**
 * Prints a diagnostic view of a snapshot directory to stdout, so that
 * any assertion failure in CI has enough context to debug without
 * downloading the Playwright HTML report. Playwright's json reporter
 * captures stdout alongside each test.
 */
function dumpBundle(outDir: string, label: string): void {
  if (!fs.existsSync(outDir)) {
    console.log(`[${label}] outputDir does not exist: ${outDir}`);
    return;
  }
  const topLevel = fs.readdirSync(outDir).sort();
  console.log(`[${label}] outputDir=${outDir}`);
  console.log(`[${label}] topLevel=${JSON.stringify(topLevel)}`);
  const resourcesDir = path.join(outDir, 'resources');
  if (fs.existsSync(resourcesDir)) {
    const resourceFiles = fs.readdirSync(resourcesDir).sort();
    const sizes = resourceFiles.map((f) => `${f}(${fs.statSync(path.join(resourcesDir, f)).size}b)`);
    console.log(`[${label}] resources/=${JSON.stringify(sizes)}`);
  } else {
    console.log(`[${label}] resources/ missing`);
  }
  const htmlPath = path.join(outDir, 'index.html');
  if (fs.existsSync(htmlPath)) {
    const html = fs.readFileSync(htmlPath, 'utf-8');
    const linkMatches = Array.from(html.matchAll(/<link[^>]*rel=["']stylesheet["'][^>]*>/g)).map((m) => m[0]);
    console.log(`[${label}] <link rel=stylesheet> count=${linkMatches.length}`);
    for (const lm of linkMatches) console.log(`[${label}]   ${lm}`);
  }
  const manifestPath = path.join(outDir, 'manifest.json');
  if (fs.existsSync(manifestPath)) {
    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    console.log(`[${label}] manifest.version=${manifest.version} url=${manifest.url}`);
  }
}

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

    dumpBundle(result.outputDir, 'initial');

    // --- layout checks --------------------------------------------------
    expect(result.outputDir, 'outputDir should match group/name path').toBe(
      path.join(tmpDir, 'dashboard', 'initial'),
    );
    expect(fs.existsSync(result.files.html), 'index.html must exist on disk').toBe(true);
    expect(fs.existsSync(result.files.manifest!), 'manifest.json must exist on disk').toBe(true);

    const topLevel = fs.readdirSync(result.outputDir).sort();
    expect(topLevel, 'topLevel must contain index.html').toContain('index.html');
    expect(topLevel, 'topLevel must contain manifest.json').toContain('manifest.json');
    expect(topLevel, 'topLevel must contain resources/').toContain('resources');

    // v2 MUST NOT write screenshot.* at the top level.
    expect(
      topLevel.some((f) => f.startsWith('screenshot.')),
      'v2 forbids top-level screenshot.*',
    ).toBe(false);

    // --- resources dir --------------------------------------------------
    const resourcesDir = path.join(result.outputDir, 'resources');
    const resourceFiles = fs.readdirSync(resourcesDir);

    // PlaywrightAdapter writes screenshot.png by default.
    expect(resourceFiles, 'resources/ must contain screenshot.png').toContain('screenshot.png');

    // app.html links four external stylesheets: reset, theme, layout,
    // components. Inline stylesheets collected by the browser collector
    // may add more — but we expect AT LEAST the four linked ones as
    // distinct sidecars, named <sha1-16>.css.
    const cssSidecars = resourceFiles.filter((f) => /^[a-f0-9]{16}\.css$/.test(f));
    expect(
      cssSidecars.length,
      `expected >=4 CSS sidecars, got ${cssSidecars.length}: ${JSON.stringify(resourceFiles)}`,
    ).toBeGreaterThanOrEqual(4);

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
    // `npx serve` strips the .html extension via its default cleanUrls
    // rewrite, so page.url() returns "http://localhost:8089/app" after
    // the navigation. Assert the host + path prefix instead of a literal
    // 'app.html' suffix.
    expect(manifest.url, `manifest.url=${manifest.url}`).toContain('localhost:8089');
    expect(manifest.url).toMatch(/\/app(\.html)?$/);
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
    const htmlFirst = fs.readFileSync(first.files.html, 'utf-8');
    const htmlMtime1 = fs.statSync(first.files.html).mtimeMs;

    // Give the filesystem enough resolution that a real rewrite would bump mtime.
    await new Promise((r) => setTimeout(r, 50));

    const second = await saveSnapshot(page, {
      outputDir: tmpDir,
      group: 'dashboard',
      name: 'cache',
    });
    const htmlSecond = fs.readFileSync(second.files.html, 'utf-8');
    const htmlMtime2 = fs.statSync(second.files.html).mtimeMs;

    if (htmlMtime2 !== htmlMtime1 || htmlFirst !== htmlSecond) {
      // Non-deterministic assembled HTML — find the first differing char so
      // the CI log surfaces which part of the serialization isn't stable.
      let firstDiff = -1;
      const min = Math.min(htmlFirst.length, htmlSecond.length);
      for (let i = 0; i < min; i++) {
        if (htmlFirst.charCodeAt(i) !== htmlSecond.charCodeAt(i)) {
          firstDiff = i;
          break;
        }
      }
      console.log(`htmlFirst.length=${htmlFirst.length} htmlSecond.length=${htmlSecond.length} firstDiff=${firstDiff}`);
      if (firstDiff >= 0) {
        const around = (s: string) => s.slice(Math.max(0, firstDiff - 60), firstDiff + 60);
        console.log(`first : ${JSON.stringify(around(htmlFirst))}`);
        console.log(`second: ${JSON.stringify(around(htmlSecond))}`);
      }
    }

    expect(htmlSecond, 'assembled HTML should be byte-identical between runs').toBe(htmlFirst);
    expect(htmlMtime2, 'mtime should be preserved when content is unchanged').toBe(htmlMtime1);
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
