/**
 * All Playwright internal imports are isolated in this file.
 * If Playwright changes internal paths between versions, only this file needs updating.
 *
 * Type declarations are in playwright-internals.d.ts.
 *
 * IMPORTANT: Playwright's internal modules use build-time path aliases
 * (e.g., `@isomorphic/traceUtils`) that are not resolved at runtime.
 * We patch Node's module resolver to handle these aliases before loading.
 *
 * Verified against playwright-core internal modules:
 *  - lib/utils/isomorphic/trace/traceLoader.js (TraceLoader)
 *  - lib/utils/isomorphic/trace/snapshotRenderer.js (SnapshotRenderer)
 *  - lib/utils/isomorphic/trace/snapshotStorage.js (SnapshotStorage)
 *  - lib/server/trace/viewer/traceParser.js (ZipTraceLoaderBackend)
 */

import Module from 'module';
import * as path from 'path';
import type {
  TraceLoaderBackend,
  TraceLoader as TraceLoaderType,
  ActionEntry,
} from 'playwright-core/lib/utils/isomorphic/trace/traceLoader';

// ---------------------------------------------------------------------------
// Patch Node's module resolver to handle Playwright's build-time aliases.
// @isomorphic/* -> playwright-core/lib/utils/isomorphic/*
// ---------------------------------------------------------------------------

const playwrightCorePath = path.dirname(require.resolve('playwright-core/package.json'));
const isomorphicDir = path.join(playwrightCorePath, 'lib', 'utils', 'isomorphic');

const originalResolveFilename = (Module as any)._resolveFilename;
(Module as any)._resolveFilename = function (
  request: string,
  parent: any,
  isMain: boolean,
  options: any,
) {
  if (request.startsWith('@isomorphic/')) {
    const moduleName = request.slice('@isomorphic/'.length);
    request = path.join(isomorphicDir, moduleName);
  }
  return originalResolveFilename.call(this, request, parent, isMain, options);
};

// ---------------------------------------------------------------------------
// Now we can safely require the internal TraceLoader.
// ---------------------------------------------------------------------------

// eslint-disable-next-line @typescript-eslint/no-var-requires
const { TraceLoader } = require(
  path.join(playwrightCorePath, 'lib', 'utils', 'isomorphic', 'trace', 'traceLoader'),
) as { TraceLoader: new () => TraceLoaderType };

// -- Public types -------------------------------------------------------------

export interface TraceSnapshotMarker {
  /** Internal call ID for this action in the trace */
  callId: string;
  /** Full label, e.g., '[snapshot:login/main]' */
  label: string;
  /** Parsed page name, e.g., 'login' */
  page: string;
  /** Parsed state name, e.g., 'main' */
  state: string;
  /** Monotonic timestamp of the action (seconds from trace start) */
  timestamp: number;
  /** Page ID in the trace (needed for snapshot lookup) */
  pageId: string;
  /**
   * The snapshot name resolved for this marker.
   * In older Playwright: action.afterSnapshot directly.
   * In newer Playwright: resolved from the closest `after@` snapshot by timestamp.
   */
  afterSnapshot: string | undefined;
}

// -- Internal types for raw snapshot data ------------------------------------

interface RawSnapshot {
  callId: string;
  snapshotName: string;
  pageId: string;
  frameId: string;
  timestamp: number;
  wallTime: number;
}

// -- Constants ----------------------------------------------------------------

const MARKER_REGEX = /^\[snapshot:([a-zA-Z0-9_-]+)\/([a-zA-Z0-9_-]+)\]$/;

// -- Functions ----------------------------------------------------------------

/**
 * Returns the title/label from an action entry.
 * In trace format v8+, `apiName` was renamed to `title`.
 * We check both for backwards compatibility.
 */
function actionTitle(action: ActionEntry): string {
  return action.title ?? action.apiName ?? '';
}

/**
 * Extract the page-level key from the storage's _frameSnapshots map.
 * In current Playwright, snapshots are indexed by frame/page IDs like
 * "page@abc123" and "frame@def456". We prefer the page@ key.
 */
function findPageId(storage: any): string | undefined {
  const frameSnapshots = storage._frameSnapshots;
  if (!(frameSnapshots instanceof Map)) return undefined;
  const keys = [...frameSnapshots.keys()] as string[];
  return keys.find(k => k.startsWith('page@')) ?? keys[0];
}

/**
 * Get all raw snapshots from storage for a given pageId.
 */
function getRawSnapshots(storage: any, pageId: string): RawSnapshot[] {
  const frameSnapshots = storage._frameSnapshots;
  if (!(frameSnapshots instanceof Map)) return [];
  const frames = frameSnapshots.get(pageId);
  if (!frames) return [];
  return (frames.raw ?? []) as RawSnapshot[];
}

/**
 * Find the closest `after@` snapshot to a given timestamp.
 * Returns the snapshotName or undefined if none found.
 *
 * Strategy: find the `after@` snapshot whose timestamp is closest to
 * (and preferably >= ) the marker timestamp.
 */
function findClosestAfterSnapshot(
  rawSnapshots: RawSnapshot[],
  markerTimestamp: number,
): string | undefined {
  const afterSnaps = rawSnapshots.filter(s => s.snapshotName.startsWith('after@'));
  if (afterSnaps.length === 0) return undefined;

  let closest = afterSnaps[0];
  let closestDist = Math.abs(closest.timestamp - markerTimestamp);

  for (const snap of afterSnaps) {
    const dist = Math.abs(snap.timestamp - markerTimestamp);
    if (dist < closestDist) {
      closest = snap;
      closestDist = dist;
    }
  }

  return closest.snapshotName;
}

/**
 * Load a trace from a backend and find all snapshot markers.
 *
 * Snapshot markers are created by `snapshot({ page, state })` which calls
 * `test.step('[snapshot:page/state]', ...)`. This produces a trace action
 * whose title matches the `[snapshot:...]` pattern.
 *
 * Since `test.step` actions don't carry `afterSnapshot` or `pageId` in
 * current Playwright versions, we resolve these by:
 * 1. Looking up the page ID from the storage's internal _frameSnapshots map
 * 2. Finding the closest `after@` snapshot by timestamp
 */
export async function loadTraceMarkers(backend: TraceLoaderBackend): Promise<{
  markers: TraceSnapshotMarker[];
  loader: TraceLoaderType;
}> {
  const loader = new TraceLoader();
  await loader.load(backend, () => undefined);

  const storage = loader.storage();
  const resolvedPageId = findPageId(storage);
  const rawSnapshots = resolvedPageId ? getRawSnapshots(storage, resolvedPageId) : [];

  const markers: TraceSnapshotMarker[] = [];

  for (const context of loader.contextEntries) {
    for (const action of context.actions) {
      const title = actionTitle(action);
      const match = MARKER_REGEX.exec(title);
      if (!match) continue;

      // action.wallTime is often 0/undefined for test.step actions.
      // Compute real wall-clock time from the context's timing offset.
      const markerTimestamp = (action.wallTime && action.wallTime > 1e10)
        ? action.wallTime
        : context.wallTime + (action.startTime - context.startTime);

      // Try action's own fields first (older Playwright versions)
      let afterSnapshot = action.afterSnapshot;
      let pageId = action.pageId;

      // Resolve from storage if not available on the action
      if (!pageId) {
        pageId = resolvedPageId ?? '';
      }
      if (!afterSnapshot && rawSnapshots.length > 0) {
        afterSnapshot = findClosestAfterSnapshot(rawSnapshots, markerTimestamp);
      }

      markers.push({
        callId: action.callId,
        label: title,
        page: match[1],
        state: match[2],
        timestamp: markerTimestamp,
        pageId: pageId!,
        afterSnapshot,
      });
    }
  }

  return { markers, loader };
}

// -- Re-exports ---------------------------------------------------------------

export { TraceLoader };
export type { TraceLoaderBackend, TraceLoaderType };
