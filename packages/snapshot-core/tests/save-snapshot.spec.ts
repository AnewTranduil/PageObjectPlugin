import { describe, it, expect, beforeEach, afterAll } from 'vitest';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import { saveSnapshot } from '../src/save-snapshot';
import { CaptureRequest, CapturedPage, PageAdapter } from '../src/types';

const tmpRoot = path.join(os.tmpdir(), `snapshot-core-${process.pid}`);

function cleanTmp() {
  fs.rmSync(tmpRoot, { recursive: true, force: true });
}

beforeEach(cleanTmp);
afterAll(cleanTmp);

/**
 * FakePageAdapter returns a canned CapturedPage. Tests can register
 * expectations against `captureCalls` to assert the core passed the
 * right CaptureRequest.
 */
class FakePageAdapter implements PageAdapter {
  public captureCalls: CaptureRequest[] = [];

  constructor(private readonly page: CapturedPage) {}

  async capture(request: CaptureRequest): Promise<CapturedPage> {
    this.captureCalls.push(request);
    const cloned: CapturedPage = {
      ...this.page,
      stylesheets: [...this.page.stylesheets],
      resources: this.page.resources.map((r) => ({
        filename: r.filename,
        bytes: new Uint8Array(r.bytes),
      })),
    };
    if (request.screenshot) {
      cloned.resources.push({
        filename: `screenshot.${request.screenshot.format}`,
        bytes: new Uint8Array([0x00, 0x01, 0x02]),
      });
    }
    return cloned;
  }
}

function loginPage(): CapturedPage {
  return {
    html: '<html><head><link rel="stylesheet" href="app.css"></head><body><h1>Login</h1></body></html>',
    stylesheets: [{ href: 'app.css', source: 'body { color: red; }' }],
    resources: [],
    url: 'http://localhost/login',
    viewport: { width: 1280, height: 720 },
    userAgent: 'Mozilla/5.0 (FakePage)',
  };
}

describe('saveSnapshot', () => {
  it('writes index.html, manifest.json, CSS sidecar, and screenshot by default', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'initial',
    });

    expect(result.outputDir).toBe(path.join(tmpRoot, 'initial'));
    expect(fs.existsSync(result.files.html)).toBe(true);
    expect(fs.existsSync(result.files.manifest!)).toBe(true);
    // Two resources: 1 CSS sidecar + 1 screenshot.
    expect(result.files.resources).toHaveLength(2);
    expect(result.files.resources.every((p) => fs.existsSync(p))).toBe(true);

    const names = result.files.resources.map((p) => path.basename(p)).sort();
    expect(names).toEqual(expect.arrayContaining(['screenshot.webp']));
    expect(names.some((n) => /^[a-f0-9]{16}\.css$/.test(n))).toBe(true);
  });

  it('group option creates a nested directory', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      group: 'login',
      name: 'initial',
    });
    expect(result.outputDir).toBe(path.join(tmpRoot, 'login', 'initial'));
    expect(fs.existsSync(result.files.html)).toBe(true);
  });

  it('screenshot.format png produces a png sidecar', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'png',
      screenshot: { format: 'png' },
    });
    const screenshot = result.files.resources.find((p) => p.endsWith('screenshot.png'));
    expect(screenshot).toBeDefined();
    expect(fs.existsSync(screenshot!)).toBe(true);
  });

  it('screenshot: false skips the screenshot entirely', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'noshot',
      screenshot: false,
    });
    expect(adapter.captureCalls[0].screenshot).toBeUndefined();
    const screenshotFiles = result.files.resources.filter((p) =>
      path.basename(p).startsWith('screenshot'),
    );
    expect(screenshotFiles).toHaveLength(0);
  });

  it('manifest: false skips the manifest file', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'nomanifest',
      manifest: false,
    });
    expect(result.files.manifest).toBeUndefined();
    expect(fs.existsSync(path.join(result.outputDir, 'manifest.json'))).toBe(false);
  });

  it('rewrites <link rel=stylesheet> to a resources/ sidecar', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'rewrite',
    });
    const html = fs.readFileSync(result.files.html, 'utf-8');
    expect(html).toContain('<!DOCTYPE html>');
    // Original href replaced with resources/<sha1>.css
    expect(html).not.toContain('href="app.css"');
    expect(html).toMatch(/href="resources\/[a-f0-9]{16}\.css"/);
  });

  it('manifest has version 2 and the expected metadata fields', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'meta',
      driver: { name: 'playwright', version: '1.58.2' },
    });
    const manifest = JSON.parse(fs.readFileSync(result.files.manifest!, 'utf-8'));
    expect(manifest.version).toBe(2);
    expect(manifest.url).toBe('http://localhost/login');
    expect(manifest.viewport).toEqual({ width: 1280, height: 720 });
    expect(manifest.playwright).toBe('1.58.2');
    expect(manifest.userAgent).toBe('Mozilla/5.0 (FakePage)');
    expect(typeof manifest.timestamp).toBe('string');
  });

  it('forwards collector options (extra/exclude selectors) to the adapter', async () => {
    const adapter = new FakePageAdapter(loginPage());
    await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'options',
      extraSelectors: ['.foo'],
      excludeSelectors: ['.bar'],
      extraAttributes: ['data-baz'],
    });
    expect(adapter.captureCalls).toHaveLength(1);
    expect(adapter.captureCalls[0].extraSelectors).toEqual(['.foo']);
    expect(adapter.captureCalls[0].excludeSelectors).toEqual(['.bar']);
    expect(adapter.captureCalls[0].extraAttributes).toEqual(['data-baz']);
  });

  it('skips rewriting index.html when content is unchanged', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const first = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'cache',
    });
    const mtime1 = fs.statSync(first.files.html).mtimeMs;

    // Force a delay so mtime would differ on any real write
    await new Promise((r) => setTimeout(r, 20));

    const second = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'cache',
    });
    const mtime2 = fs.statSync(second.files.html).mtimeMs;
    expect(mtime2).toBe(mtime1);
    // resources still discoverable on the unchanged path
    expect(second.files.resources.length).toBe(first.files.resources.length);
  });

  it('v2: no top-level screenshot file is ever written', async () => {
    const adapter = new FakePageAdapter(loginPage());
    const result = await saveSnapshot(adapter, {
      outputDir: tmpRoot,
      name: 'layout',
    });
    const topLevelFiles = fs.readdirSync(result.outputDir);
    expect(topLevelFiles.some((f) => f.startsWith('screenshot.'))).toBe(false);
    expect(topLevelFiles).toContain('resources');
  });
});
