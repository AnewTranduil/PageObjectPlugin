import { test, expect } from '@playwright/test';
import type { TraceLoaderBackend } from 'playwright-core/lib/utils/isomorphic/trace/traceLoader';
import {
  loadTraceMarkers,
  renderSnapshotAtMarker,
  findScreencastFrame,
  TraceLoader,
} from '../src/trace/playwright-adapter';
import type { TraceSnapshotMarker } from '../src/trace/playwright-adapter';

/**
 * These tests verify the playwright-adapter module compiles, imports correctly,
 * and that the core logic (marker regex parsing, error handling) works.
 *
 * Tests use mock TraceLoaderBackend implementations with synthetic trace data
 * to exercise the adapter without needing real trace files.
 */

/**
 * Creates a mock TraceLoaderBackend from trace event lines.
 * Trace files in Playwright are named like "0.trace", "0.network", "0.stacks".
 * The backend exposes "0.trace" with the provided content.
 */
function mockBackend(traceContent: string): TraceLoaderBackend {
  const files: Record<string, string> = {
    '0.trace': traceContent,
  };
  return {
    entryNames: async () => Object.keys(files),
    hasEntry: async (name: string) => name in files,
    readText: async (name: string) => files[name],
    readBlob: async () => undefined,
    isLive: () => false,
  };
}

/** Minimal context-options event required by TraceLoader */
function contextOptionsEvent(overrides: Record<string, unknown> = {}) {
  return JSON.stringify({
    type: 'context-options',
    origin: 'testRunner',
    version: 8,
    browserName: 'chromium',
    options: { viewport: { width: 1280, height: 720 } },
    platform: 'win32',
    wallTime: 1000,
    monotonicTime: 0,
    sdkLanguage: 'javascript',
    contextId: 'ctx-1',
    ...overrides,
  });
}

test.describe('playwright-adapter', () => {
  test('module exports are accessible', () => {
    expect(typeof loadTraceMarkers).toBe('function');
    expect(typeof renderSnapshotAtMarker).toBe('function');
    expect(typeof findScreencastFrame).toBe('function');
    expect(TraceLoader).toBeDefined();
  });

  test('TraceLoader can be instantiated', () => {
    const loader = new TraceLoader();
    expect(loader).toBeDefined();
    expect(loader.contextEntries).toEqual([]);
  });

  test('loadTraceMarkers rejects when backend has no .trace files', async () => {
    const emptyBackend: TraceLoaderBackend = {
      entryNames: async () => ['some-file.txt'],
      hasEntry: async () => false,
      readText: async () => undefined,
      readBlob: async () => undefined,
      isLive: () => false,
    };

    await expect(loadTraceMarkers(emptyBackend)).rejects.toThrow('Cannot find .trace file');
  });

  test('loadTraceMarkers returns empty markers for trace with no snapshot steps', async () => {
    const traceLines = [
      contextOptionsEvent(),
      JSON.stringify({
        type: 'before',
        callId: 'call-1',
        startTime: 100,
        endTime: 0,
        title: 'page.goto',
        class: 'Page',
        method: 'goto',
        params: { url: 'http://localhost' },
        wallTime: 1000,
        pageId: 'page-1',
        snapshots: [],
      }),
      JSON.stringify({
        type: 'after',
        callId: 'call-1',
        endTime: 200,
      }),
    ].join('\n');

    const { markers, loader } = await loadTraceMarkers(mockBackend(traceLines));
    expect(markers).toEqual([]);
    expect(loader.contextEntries.length).toBe(1);
  });

  test('loadTraceMarkers finds snapshot markers in trace actions', async () => {
    const traceLines = [
      contextOptionsEvent(),
      // Regular action — should be ignored
      JSON.stringify({
        type: 'before',
        callId: 'call-1',
        startTime: 100,
        endTime: 0,
        title: 'page.goto',
        class: 'Page',
        method: 'goto',
        params: { url: 'http://localhost' },
        wallTime: 1000,
        pageId: 'page-1',
        snapshots: [],
      }),
      JSON.stringify({
        type: 'after',
        callId: 'call-1',
        endTime: 200,
      }),
      // Snapshot marker step
      JSON.stringify({
        type: 'before',
        callId: 'call-2',
        startTime: 300,
        endTime: 0,
        title: '[snapshot:login/main]',
        class: 'Test',
        method: 'step',
        params: {},
        wallTime: 2000,
        pageId: 'page-1',
        snapshots: [],
      }),
      JSON.stringify({
        type: 'after',
        callId: 'call-2',
        endTime: 400,
        afterSnapshot: 'after@call-2',
      }),
      // Another snapshot marker
      JSON.stringify({
        type: 'before',
        callId: 'call-3',
        startTime: 500,
        endTime: 0,
        title: '[snapshot:dashboard/error-state]',
        class: 'Test',
        method: 'step',
        params: {},
        wallTime: 3000,
        pageId: 'page-1',
        snapshots: [],
      }),
      JSON.stringify({
        type: 'after',
        callId: 'call-3',
        endTime: 600,
        afterSnapshot: 'after@call-3',
      }),
    ].join('\n');

    const { markers } = await loadTraceMarkers(mockBackend(traceLines));
    expect(markers).toHaveLength(2);

    expect(markers[0]).toMatchObject({
      callId: 'call-2',
      label: '[snapshot:login/main]',
      page: 'login',
      state: 'main',
      timestamp: 2000,
      pageId: 'page-1',
      afterSnapshot: 'after@call-2',
    });

    expect(markers[1]).toMatchObject({
      callId: 'call-3',
      label: '[snapshot:dashboard/error-state]',
      page: 'dashboard',
      state: 'error-state',
      timestamp: 3000,
      pageId: 'page-1',
      afterSnapshot: 'after@call-3',
    });
  });

  test('loadTraceMarkers ignores non-matching titles', async () => {
    const traceLines = [
      contextOptionsEvent(),
      // Titles that look similar but don't match the snapshot pattern
      JSON.stringify({
        type: 'before',
        callId: 'call-1',
        startTime: 100,
        endTime: 0,
        title: '[snapshot:login]', // missing /state
        class: 'Test',
        method: 'step',
        params: {},
        wallTime: 1000,
        pageId: 'page-1',
        snapshots: [],
      }),
      JSON.stringify({ type: 'after', callId: 'call-1', endTime: 200 }),
      JSON.stringify({
        type: 'before',
        callId: 'call-2',
        startTime: 300,
        endTime: 0,
        title: 'snapshot:login/main', // missing brackets
        class: 'Test',
        method: 'step',
        params: {},
        wallTime: 2000,
        pageId: 'page-1',
        snapshots: [],
      }),
      JSON.stringify({ type: 'after', callId: 'call-2', endTime: 400 }),
      JSON.stringify({
        type: 'before',
        callId: 'call-3',
        startTime: 500,
        endTime: 0,
        title: '[snapshot:bad page/state]', // space in page name
        class: 'Test',
        method: 'step',
        params: {},
        wallTime: 3000,
        pageId: 'page-1',
        snapshots: [],
      }),
      JSON.stringify({ type: 'after', callId: 'call-3', endTime: 600 }),
    ].join('\n');

    const { markers } = await loadTraceMarkers(mockBackend(traceLines));
    expect(markers).toHaveLength(0);
  });

  test('renderSnapshotAtMarker throws when afterSnapshot is missing', async () => {
    const loader = new TraceLoader();
    const marker: TraceSnapshotMarker = {
      callId: 'call-1',
      label: '[snapshot:login/main]',
      page: 'login',
      state: 'main',
      timestamp: 1000,
      pageId: 'page-1',
      afterSnapshot: undefined,
    };

    await expect(renderSnapshotAtMarker(loader, marker)).rejects.toThrow(
      'has no afterSnapshot',
    );
  });

  test('renderSnapshotAtMarker throws when snapshot not found in storage', async () => {
    const traceLines = [contextOptionsEvent()].join('\n');
    const { loader } = await loadTraceMarkers(mockBackend(traceLines));

    const marker: TraceSnapshotMarker = {
      callId: 'call-1',
      label: '[snapshot:login/main]',
      page: 'login',
      state: 'main',
      timestamp: 1000,
      pageId: 'page-1',
      afterSnapshot: 'after@call-1',
    };

    await expect(renderSnapshotAtMarker(loader, marker)).rejects.toThrow(
      'No snapshot found for marker',
    );
  });

  test('findScreencastFrame returns undefined when no pages match', async () => {
    const traceLines = [contextOptionsEvent()].join('\n');
    const { loader } = await loadTraceMarkers(mockBackend(traceLines));

    const marker: TraceSnapshotMarker = {
      callId: 'call-1',
      label: '[snapshot:login/main]',
      page: 'login',
      state: 'main',
      timestamp: 1000,
      pageId: 'page-1',
      afterSnapshot: 'after@call-1',
    };

    const result = await findScreencastFrame(loader, marker);
    expect(result).toBeUndefined();
  });
});
