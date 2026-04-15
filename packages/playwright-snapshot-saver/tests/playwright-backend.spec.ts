/**
 * Contract tests for `PlaywrightTraceBackend` — exercises every
 * `TraceBackend` method against a real trace fixture and asserts the
 * returned shapes structurally match `@pagemirror/snapshot-core`'s
 * interface (not just at the type level — the raw Playwright data goes
 * through `reshape*` helpers and we check fields are actually present).
 */
import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { loadTraceMarkers } from '../src/trace/playwright-adapter';
import { PlaywrightTraceBackend } from '../src/trace/playwright-backend';
import { createBackendFromZip } from '../src/sources/zip-source';

const SAMPLE_TRACE = path.join(__dirname, 'fixtures', 'sample-trace.zip');

function requireFixture() {
  if (!fs.existsSync(SAMPLE_TRACE)) test.skip();
}

test.describe('PlaywrightTraceBackend contract', () => {
  test.beforeEach(requireFixture);

  test('getFrameSnapshots returns core-shaped FrameSnapshot objects', async () => {
    const { backend: zipBackend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers, loader } = await loadTraceMarkers(zipBackend);
      const coreBackend = new PlaywrightTraceBackend(loader);
      const snaps = coreBackend.getFrameSnapshots(markers[0].pageId);
      expect(snaps.length).toBeGreaterThan(0);
      const s = snaps[0];
      expect(typeof s.callId).toBe('string');
      expect(typeof s.snapshotName).toBe('string');
      expect(typeof s.pageId).toBe('string');
      expect(typeof s.frameId).toBe('string');
      expect(typeof s.timestamp).toBe('number');
      expect(s.viewport).toMatchObject({ width: expect.any(Number), height: expect.any(Number) });
      // `url` is set by Playwright whenever the navigation has happened;
      // it may be absent on the very first frame snapshot. When present
      // it must be a string.
      if (s.url !== undefined) expect(typeof s.url).toBe('string');
      expect(Array.isArray(s.resourceOverrides)).toBe(true);
    } finally {
      cleanup();
    }
  });

  test('getFrameSnapshots returns [] for unknown pageId', async () => {
    const { backend: zipBackend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { loader } = await loadTraceMarkers(zipBackend);
      const coreBackend = new PlaywrightTraceBackend(loader);
      expect(coreBackend.getFrameSnapshots('page@no-such-id')).toEqual([]);
    } finally {
      cleanup();
    }
  });

  test('getResources flattens all resources with core-shaped fields', async () => {
    const { backend: zipBackend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { loader } = await loadTraceMarkers(zipBackend);
      const coreBackend = new PlaywrightTraceBackend(loader);
      const resources = coreBackend.getResources();
      expect(resources.length).toBeGreaterThan(0);
      const r = resources[0];
      expect(r.request).toMatchObject({
        url: expect.any(String),
        method: expect.any(String),
      });
      expect(r.response).toMatchObject({
        status: expect.any(Number),
        content: expect.objectContaining({ sha1: expect.any(String) }),
      });
      // No underscore-prefixed Playwright internals should leak through.
      expect(r).not.toHaveProperty('_monotonicTime');
      expect(r).not.toHaveProperty('_frameref');
    } finally {
      cleanup();
    }
  });

  test('readResource returns Uint8Array bytes for a known sha1', async () => {
    const { backend: zipBackend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { loader } = await loadTraceMarkers(zipBackend);
      const coreBackend = new PlaywrightTraceBackend(loader);
      const resources = coreBackend.getResources();
      const withSha = resources.find((r) => r.response.content.sha1);
      expect(withSha).toBeDefined();
      const bytes = await coreBackend.readResource(withSha!.response.content.sha1);
      expect(bytes).toBeInstanceOf(Uint8Array);
      expect(bytes!.byteLength).toBeGreaterThan(0);
    } finally {
      cleanup();
    }
  });

  test('readResource returns undefined for an unknown sha1', async () => {
    const { backend: zipBackend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { loader } = await loadTraceMarkers(zipBackend);
      const coreBackend = new PlaywrightTraceBackend(loader);
      const bytes = await coreBackend.readResource('deadbeefnotreal');
      expect(bytes).toBeUndefined();
    } finally {
      cleanup();
    }
  });

  test('getScreencastFrames returns core-shaped ScreencastFrame entries (possibly empty)', async () => {
    const { backend: zipBackend, cleanup } = createBackendFromZip(SAMPLE_TRACE);
    try {
      const { markers, loader } = await loadTraceMarkers(zipBackend);
      const coreBackend = new PlaywrightTraceBackend(loader);
      const frames = coreBackend.getScreencastFrames!(markers[0].pageId);
      expect(Array.isArray(frames)).toBe(true);
      for (const f of frames) {
        expect(typeof f.sha1).toBe('string');
        expect(typeof f.timestamp).toBe('number');
      }
    } finally {
      cleanup();
    }
  });
});
