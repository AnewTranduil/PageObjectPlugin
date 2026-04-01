import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

import { loadTraceMarkers, renderSnapshotAtMarker } from '../src/trace/playwright-adapter';
import { createBackendFromZip } from '../src/sources/zip-source';
import { extractSnapshots } from '../src/extractor';

// ---------------------------------------------------------------------------
// Paths
// ---------------------------------------------------------------------------

const FIXTURES_DIR = path.join(__dirname, 'fixtures');
const SAMPLE_TRACE = path.join(FIXTURES_DIR, 'sample-trace.zip');
const NO_MARKERS_TRACE = path.join(FIXTURES_DIR, 'no-markers-trace.zip');

/** Skip all tests if fixtures haven't been generated yet. */
function requireFixtures() {
  if (!fs.existsSync(SAMPLE_TRACE) || !fs.existsSync(NO_MARKERS_TRACE)) {
    test.skip();
  }
}

/** Creates a temporary directory, returns its path and a cleanup function. */
function makeTmpDir(): { dir: string; cleanup: () => void } {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-fixture-test-'));
  return { dir, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

// ---------------------------------------------------------------------------
// Adapter-level tests against real trace ZIPs
// ---------------------------------------------------------------------------

test.describe('trace fixtures - adapter', () => {
  test.beforeEach(requireFixtures);

  test('sample-trace.zip contains exactly 2 snapshot markers', async () => {
    const { backend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers } = await loadTraceMarkers(backend);
      expect(markers).toHaveLength(2);
    } finally {
      cleanup();
    }
  });

  test('markers have correct page and state', async () => {
    const { backend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers } = await loadTraceMarkers(backend);

      expect(markers[0]).toMatchObject({
        label: '[snapshot:login/main]',
        page: 'login',
        state: 'main',
      });
      expect(markers[1]).toMatchObject({
        label: '[snapshot:login/error]',
        page: 'login',
        state: 'error',
      });
    } finally {
      cleanup();
    }
  });

  test('markers have afterSnapshot set', async () => {
    const { backend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers } = await loadTraceMarkers(backend);
      for (const marker of markers) {
        expect(marker.afterSnapshot).toBeTruthy();
      }
    } finally {
      cleanup();
    }
  });

  test('markers have valid timestamps and pageId', async () => {
    const { backend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers } = await loadTraceMarkers(backend);
      for (const marker of markers) {
        expect(marker.timestamp).toBeGreaterThan(0);
        expect(marker.pageId).toBeTruthy();
        expect(marker.callId).toBeTruthy();
      }
    } finally {
      cleanup();
    }
  });

  test('can render snapshot HTML at each marker', async () => {
    const { backend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers, loader } = await loadTraceMarkers(backend);
      for (const marker of markers) {
        const rendered = await renderSnapshotAtMarker(loader, marker);
        expect(rendered.html).toBeTruthy();
        expect(rendered.html.length).toBeGreaterThan(100);
        expect(rendered.viewport.width).toBeGreaterThan(0);
        expect(rendered.viewport.height).toBeGreaterThan(0);
      }
    } finally {
      cleanup();
    }
  });

  test('login/main snapshot contains the login form', async () => {
    const { backend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers, loader } = await loadTraceMarkers(backend);
      const mainMarker = markers.find(m => m.state === 'main')!;
      const rendered = await renderSnapshotAtMarker(loader, mainMarker);
      // The login page HTML should contain these elements
      expect(rendered.html).toContain('username');
      expect(rendered.html).toContain('password');
    } finally {
      cleanup();
    }
  });

  test('no-markers-trace.zip has zero snapshot markers', async () => {
    const { backend, cleanup } = createBackendFromZip(NO_MARKERS_TRACE);
    try {
      const { markers } = await loadTraceMarkers(backend);
      expect(markers).toHaveLength(0);
    } finally {
      cleanup();
    }
  });
});

// ---------------------------------------------------------------------------
// Extractor-level tests (end-to-end through the full pipeline)
// ---------------------------------------------------------------------------

test.describe('trace fixtures - extractor', () => {
  test.beforeEach(requireFixtures);

  test('extractSnapshots produces output from sample-trace.zip', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      const result = await extractSnapshots({
        source: SAMPLE_TRACE,
        outputDir: dir,
      });

      expect(result.snapshots).toHaveLength(2);

      // Check login/main
      const main = result.snapshots.find(s => s.page === 'login' && s.state === 'main');
      expect(main).toBeDefined();
      expect(fs.existsSync(main!.files.html)).toBe(true);

      const mainHtml = fs.readFileSync(main!.files.html, 'utf-8');
      expect(mainHtml).toContain('username');

      // Check login/error
      const error = result.snapshots.find(s => s.page === 'login' && s.state === 'error');
      expect(error).toBeDefined();
      expect(fs.existsSync(error!.files.html)).toBe(true);
    } finally {
      cleanup();
    }
  });

  test('extractSnapshots writes manifest.json by default', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      const result = await extractSnapshots({
        source: SAMPLE_TRACE,
        outputDir: dir,
      });

      for (const snap of result.snapshots) {
        expect(snap.files.manifest).toBeDefined();
        expect(fs.existsSync(snap.files.manifest!)).toBe(true);

        const manifest = JSON.parse(fs.readFileSync(snap.files.manifest!, 'utf-8'));
        expect(manifest.version).toBe(1);
        expect(manifest.viewport).toBeDefined();
        expect(manifest.viewport.width).toBeGreaterThan(0);
      }
    } finally {
      cleanup();
    }
  });

  test('extractSnapshots with filter returns only matching snapshots', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      const result = await extractSnapshots({
        source: SAMPLE_TRACE,
        outputDir: dir,
        filter: { state: 'error' },
      });

      expect(result.snapshots).toHaveLength(1);
      expect(result.snapshots[0].state).toBe('error');
    } finally {
      cleanup();
    }
  });

  test('extractSnapshots with no-markers trace returns empty snapshots', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      const result = await extractSnapshots({
        source: NO_MARKERS_TRACE,
        outputDir: dir,
      });

      expect(result.snapshots).toHaveLength(0);
    } finally {
      cleanup();
    }
  });

  test('re-extraction with unchanged content does not overwrite files', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      // First extraction
      await extractSnapshots({ source: SAMPLE_TRACE, outputDir: dir });

      // Record modification times
      const mainManifest = path.join(dir, 'login', 'main', 'manifest.json');
      const mainHtml = path.join(dir, 'login', 'main', 'index.html');
      const mtime1Manifest = fs.statSync(mainManifest).mtimeMs;
      const mtime1Html = fs.statSync(mainHtml).mtimeMs;

      // Small delay to ensure mtime would differ if files were rewritten
      await new Promise(r => setTimeout(r, 100));

      // Second extraction — same trace, same content
      await extractSnapshots({ source: SAMPLE_TRACE, outputDir: dir });

      const mtime2Manifest = fs.statSync(mainManifest).mtimeMs;
      const mtime2Html = fs.statSync(mainHtml).mtimeMs;

      expect(mtime2Html).toBe(mtime1Html);
      expect(mtime2Manifest).toBe(mtime1Manifest);
    } finally {
      cleanup();
    }
  });

  test('manifest version increments when HTML content changes', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      // First extraction
      await extractSnapshots({ source: SAMPLE_TRACE, outputDir: dir });

      const manifestPath = path.join(dir, 'login', 'main', 'manifest.json');
      const manifest1 = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
      expect(manifest1.version).toBe(1);

      // Tamper with the HTML to simulate a content change
      const htmlPath = path.join(dir, 'login', 'main', 'index.html');
      fs.writeFileSync(htmlPath, '<html>modified</html>', 'utf-8');

      // Re-extract — content differs from what extractor produces, so it should write
      await extractSnapshots({ source: SAMPLE_TRACE, outputDir: dir });

      const manifest2 = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
      expect(manifest2.version).toBe(2);
    } finally {
      cleanup();
    }
  });

  test('manifest timestamp is a valid date (not 1970)', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      await extractSnapshots({ source: SAMPLE_TRACE, outputDir: dir });

      const manifestPath = path.join(dir, 'login', 'main', 'manifest.json');
      const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
      const date = new Date(manifest.timestamp);

      // Should be a real date, not epoch-adjacent
      expect(date.getFullYear()).toBeGreaterThan(2020);
    } finally {
      cleanup();
    }
  });

  test('extracted HTML files form valid snapshot bundle directories', async () => {
    const { dir, cleanup } = makeTmpDir();
    try {
      const result = await extractSnapshots({
        source: SAMPLE_TRACE,
        outputDir: dir,
      });

      for (const snap of result.snapshots) {
        // Each snapshot should have its own directory: outputDir/page/state/
        const expectedDir = path.join(dir, snap.page, snap.state);
        expect(fs.existsSync(expectedDir)).toBe(true);

        // index.html must exist
        expect(fs.existsSync(path.join(expectedDir, 'index.html'))).toBe(true);

        // manifest.json must exist (default config)
        expect(fs.existsSync(path.join(expectedDir, 'manifest.json'))).toBe(true);
      }
    } finally {
      cleanup();
    }
  });
});
