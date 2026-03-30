import { test, expect } from '@playwright/test';
import { saveSnapshot, LayoutJson } from '../src/index';
import * as fs from 'fs';
import * as path from 'path';

const tmpDir = path.join(__dirname, '..', '.test-output');

test.beforeEach(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.afterAll(() => {
  fs.rmSync(tmpDir, { recursive: true, force: true });
});

test.describe('saveSnapshot', () => {
  test('generates all 4 files with default options', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'initial',
    });

    expect(fs.existsSync(result.files.html)).toBe(true);
    expect(fs.existsSync(result.files.layout)).toBe(true);
    expect(result.files.screenshot).toBeDefined();
    expect(fs.existsSync(result.files.screenshot!)).toBe(true);
    expect(result.files.manifest).toBeDefined();
    expect(fs.existsSync(result.files.manifest!)).toBe(true);
    expect(result.elementCount).toBeGreaterThan(0);
    expect(result.outputDir).toBe(path.join(tmpDir, 'initial'));
  });

  test('group option creates nested directory', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      group: 'login',
      name: 'initial',
    });

    expect(result.outputDir).toBe(path.join(tmpDir, 'login', 'initial'));
    expect(fs.existsSync(result.files.html)).toBe(true);
  });

  test('screenshot format jpeg produces jpeg file', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'jpeg-test',
      screenshot: { format: 'jpeg' },
    });

    expect(result.files.screenshot).toContain('screenshot.jpeg');
    expect(fs.existsSync(result.files.screenshot!)).toBe(true);
  });

  test('screenshot disabled skips screenshot file', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'no-screenshot',
      screenshot: { enabled: false },
    });

    expect(result.files.screenshot).toBeUndefined();
    // Verify no screenshot file exists in the output dir
    const files = fs.readdirSync(result.outputDir);
    expect(files.some(f => f.startsWith('screenshot'))).toBe(false);
  });

  test('manifest disabled skips manifest file', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'no-manifest',
      manifest: false,
    });

    expect(result.files.manifest).toBeUndefined();
    const files = fs.readdirSync(result.outputDir);
    expect(files).not.toContain('manifest.json');
  });

  test('extraSelectors adds custom elements to layout', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const resultDefault = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'default',
    });

    const resultExtra = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'extra',
      extraSelectors: ['em', 'h2', 'h4'],
    });

    expect(resultExtra.elementCount).toBeGreaterThan(resultDefault.elementCount);
  });

  test('excludeSelectors filters elements from layout', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const resultDefault = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'default2',
    });

    const resultExclude = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'excluded',
      excludeSelectors: ['[data-testid="login-button"]'],
    });

    expect(resultExclude.elementCount).toBeLessThan(resultDefault.elementCount);
  });

  test('every selector in layout.json resolves in index.html', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'selector-check',
    });

    const layout: LayoutJson = JSON.parse(fs.readFileSync(result.files.layout, 'utf-8'));
    const html = fs.readFileSync(result.files.html, 'utf-8');

    // Load the saved HTML in a new page context to verify selectors
    const verifyPage = await page.context().newPage();
    await verifyPage.setContent(html);

    for (const el of layout.elements) {
      const matches = await verifyPage.locator(el.selector).count();
      expect(matches, `Selector "${el.selector}" should resolve in saved HTML`).toBeGreaterThan(0);
    }

    await verifyPage.close();
  });

  test('generated HTML is self-contained with inlined CSS', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'html-check',
    });

    const html = fs.readFileSync(result.files.html, 'utf-8');
    expect(html).toContain('<!DOCTYPE html>');
    // Should not contain external stylesheet links
    expect(html).not.toMatch(/<link[^>]*rel="stylesheet"[^>]*>/);
  });

  test('manifest contains expected metadata fields', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'manifest-check',
    });

    const manifest = JSON.parse(fs.readFileSync(result.files.manifest!, 'utf-8'));
    expect(manifest.version).toBe(1);
    expect(manifest.url).toContain('login');
    expect(manifest.viewport).toEqual({ width: 1280, height: 720 });
    expect(manifest.timestamp).toBeTruthy();
    expect(manifest.playwright).toBeTruthy();
    expect(manifest.userAgent).toBeTruthy();
  });
});
