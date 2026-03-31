import { test, expect } from '@playwright/test';
import { saveSnapshot } from '../src/index';
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
  test('generates all 3 files with default options', async ({ page }) => {
    await page.goto('http://localhost:8089/login.html');

    const result = await saveSnapshot(page, {
      outputDir: tmpDir,
      name: 'initial',
    });

    expect(fs.existsSync(result.files.html)).toBe(true);
    expect(result.files.screenshot).toBeDefined();
    expect(fs.existsSync(result.files.screenshot!)).toBe(true);
    expect(result.files.manifest).toBeDefined();
    expect(fs.existsSync(result.files.manifest!)).toBe(true);
    expect(result.outputDir).toBe(path.join(tmpDir, 'initial'));
    // layout.json should not exist
    expect(fs.existsSync(path.join(result.outputDir, 'layout.json'))).toBe(false);
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
