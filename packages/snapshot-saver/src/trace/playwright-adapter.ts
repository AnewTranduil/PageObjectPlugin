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
  /** Wall-clock timestamp of the action */
  timestamp: number;
  /** Page ID in the trace (needed for snapshot lookup) */
  pageId: string;
  /** The snapshot name stored in afterSnapshot (used for snapshotByName lookup) */
  afterSnapshot: string | undefined;
}

export interface RenderedSnapshot {
  html: string;
  viewport: { width: number; height: number };
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
 * Load a trace from a backend and find all snapshot markers.
 *
 * Snapshot markers are created by `snapshot({ page, state })` which calls
 * `test.step('[snapshot:page/state]', ...)`. This produces a trace action
 * whose title matches the `[snapshot:...]` pattern.
 */
export async function loadTraceMarkers(backend: TraceLoaderBackend): Promise<{
  markers: TraceSnapshotMarker[];
  loader: TraceLoaderType;
}> {
  const loader = new TraceLoader();
  await loader.load(backend, () => undefined);

  const markers: TraceSnapshotMarker[] = [];

  for (const context of loader.contextEntries) {
    for (const action of context.actions) {
      const title = actionTitle(action);
      const match = MARKER_REGEX.exec(title);
      if (match) {
        markers.push({
          callId: action.callId,
          label: title,
          page: match[1],
          state: match[2],
          timestamp: action.wallTime ?? action.startTime,
          pageId: action.pageId,
          afterSnapshot: action.afterSnapshot,
        });
      }
    }
  }

  return { markers, loader };
}

/**
 * Render a snapshot at the given marker to full HTML.
 *
 * Uses the `afterSnapshot` name from the trace action to look up the
 * snapshot renderer via `SnapshotStorage.snapshotByName()`.
 */
export async function renderSnapshotAtMarker(
  loader: TraceLoaderType,
  marker: TraceSnapshotMarker,
): Promise<RenderedSnapshot> {
  if (!marker.afterSnapshot) {
    throw new Error(
      `Marker ${marker.label} (callId: ${marker.callId}) has no afterSnapshot — ` +
      `trace may have been recorded without snapshots enabled`,
    );
  }

  const storage = loader.storage();
  const renderer = storage.snapshotByName(marker.pageId, marker.afterSnapshot);
  if (!renderer) {
    throw new Error(
      `No snapshot found for marker ${marker.label} ` +
      `(pageId: ${marker.pageId}, snapshotName: ${marker.afterSnapshot})`,
    );
  }

  const rendered = renderer.render();
  return {
    html: rendered.html,
    viewport: renderer.viewport(),
  };
}

/**
 * Find the closest screencast frame to a marker's timestamp.
 * Returns the image data as a Buffer, or undefined if no frames exist.
 */
export async function findScreencastFrame(
  loader: TraceLoaderType,
  marker: TraceSnapshotMarker,
): Promise<Buffer | undefined> {
  for (const context of loader.contextEntries) {
    for (const page of context.pages) {
      if (page.pageId !== marker.pageId) continue;

      const frames = page.screencastFrames;
      if (!frames || frames.length === 0) continue;

      // Find the frame closest in time to the marker
      let closest = frames[0];
      for (const frame of frames) {
        const useWallTime = frame.frameSwapWallTime !== undefined;
        const frameTime = useWallTime ? frame.frameSwapWallTime! : frame.timestamp;
        const closestTime = useWallTime ? (closest.frameSwapWallTime ?? closest.timestamp) : closest.timestamp;

        if (Math.abs(frameTime - marker.timestamp) < Math.abs(closestTime - marker.timestamp)) {
          closest = frame;
        }
      }

      const blob = await loader.resourceForSha1(closest.sha1);
      if (blob) {
        const arrayBuffer = await blob.arrayBuffer();
        return Buffer.from(arrayBuffer);
      }
    }
  }
  return undefined;
}

// -- Re-exports ---------------------------------------------------------------

export { TraceLoader };
export type { TraceLoaderBackend, TraceLoaderType };
