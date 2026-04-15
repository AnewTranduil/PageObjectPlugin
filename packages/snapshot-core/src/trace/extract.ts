/**
 * Orchestrator that turns a `TraceBackend` + a list of snapshot markers
 * into on-disk bundles in the v2 layout:
 *
 *     <outDir>/<page>/<state>/
 *       index.html
 *       manifest.json
 *       resources/<sha1>.<ext>
 *       resources/screenshot.webp   (if enabled + backend has screencast frames)
 *
 * No Playwright imports — only the `TraceBackend` shape. Adapters for
 * Selenium/Cypress/Appium produce the same shape and reuse this entire
 * pipeline verbatim.
 */

import * as fs from 'fs';
import * as path from 'path';
import { renderSnapshot } from './renderer';
import { inlineResources } from './inline';
import { TraceBackend, ScreencastFrame } from './types';
import { MANIFEST_VERSION, ManifestJson } from '../types';

export interface TraceMarker {
  /** Filesystem group name — becomes the parent directory. */
  page: string;
  /** Filesystem leaf name — becomes the snapshot directory. */
  state: string;
  /** Page-or-frame id to look up snapshots with on the backend. */
  pageOrFrameId: string;
  /** Snapshot name to render (e.g. `after@<callId>`). */
  snapshotName: string;
  /** Monotonic seconds — used for screencast-frame matching. */
  timestamp: number;
  /** Wall-clock ms — preferred for screencast matching and manifest timestamp. */
  wallTime?: number;
}

export interface ExtractFromBackendOptions {
  outputDir: string;
  /** If true, and the backend provides `getScreencastFrames`, writes `resources/screenshot.webp`. */
  screenshot?: boolean;
  /** If false, skip `manifest.json`. Default true. */
  manifest?: boolean;
  /** Written verbatim into the manifest (e.g. `{ name: 'playwright', version: '1.58.2' }`). */
  driver?: { name: string; version: string };
  filter?: { page?: string; state?: string };
  /**
   * Called after each snapshot is processed. Used by the Playwright
   * extractor to emit progress warnings on stderr. Defaults to no-op.
   */
  onSnapshot?: (info: ExtractedSnapshotInfo) => void;
}

export interface ExtractedSnapshotInfo {
  page: string;
  state: string;
  outputDir: string;
  files: {
    html: string;
    manifest?: string;
    screenshot?: string;
    resources: string[];
  };
}

export interface ExtractFromBackendResult {
  snapshots: ExtractedSnapshotInfo[];
}

export async function extractFromBackend(
  backend: TraceBackend,
  markers: TraceMarker[],
  options: ExtractFromBackendOptions,
): Promise<ExtractFromBackendResult> {
  const manifestEnabled = options.manifest !== false;
  const snapshots: ExtractedSnapshotInfo[] = [];
  const seen = new Map<string, number>();

  for (const marker of markers) {
    if (!matchesFilter(marker, options.filter)) continue;

    const snapshotDir = path.join(options.outputDir, marker.page, marker.state);
    fs.mkdirSync(snapshotDir, { recursive: true });

    const rendered = renderSnapshot(backend, marker.pageOrFrameId, marker.snapshotName);
    const inlined = await inlineResources(rendered, backend);

    const htmlPath = path.join(snapshotDir, 'index.html');
    const resourcesDir = path.join(snapshotDir, 'resources');
    const manifestPath = path.join(snapshotDir, 'manifest.json');
    const screenshotPath = path.join(resourcesDir, 'screenshot.webp');

    const existingHtml = readIfExists(htmlPath);
    const htmlChanged = existingHtml !== inlined.html;
    const resourcePaths: string[] = [];

    if (htmlChanged) {
      fs.writeFileSync(htmlPath, inlined.html, 'utf-8');
    }

    const screencastSha1 = options.screenshot
      ? pickClosestScreencastSha1(backend, marker)
      : undefined;

    // Write inlined resources. Even when HTML is unchanged we verify the
    // files are on disk — a partial previous run may have left gaps.
    if (inlined.resources.length > 0 || screencastSha1) {
      fs.mkdirSync(resourcesDir, { recursive: true });
    }
    for (const res of inlined.resources) {
      const p = path.join(resourcesDir, res.filename);
      if (htmlChanged || !fs.existsSync(p)) {
        fs.writeFileSync(p, Buffer.from(res.bytes));
      }
      resourcePaths.push(p);
    }

    let screenshotOut: string | undefined;
    if (screencastSha1) {
      // Skip writing if the screencast sha1 was already emitted as a
      // regular inlined resource (deduplicates double-writes).
      const alreadyInlined = inlined.resources.some((r) =>
        r.filename.startsWith(screencastSha1 + '.'),
      );
      if (!alreadyInlined) {
        if (htmlChanged || !fs.existsSync(screenshotPath)) {
          const bytes = await backend.readResource(screencastSha1);
          if (bytes) {
            fs.writeFileSync(screenshotPath, Buffer.from(bytes));
          }
        }
        if (fs.existsSync(screenshotPath)) {
          screenshotOut = screenshotPath;
          resourcePaths.push(screenshotPath);
        }
      }
    }

    const files: ExtractedSnapshotInfo['files'] = {
      html: htmlPath,
      resources: resourcePaths,
    };
    if (screenshotOut) files.screenshot = screenshotOut;

    if (manifestEnabled) {
      const manifest = buildTraceManifest(rendered.snapshot.url, rendered.viewport, marker, options.driver);
      const serialized = JSON.stringify(manifest, null, 2);
      if (readIfExists(manifestPath) !== serialized) {
        fs.writeFileSync(manifestPath, serialized, 'utf-8');
      }
      files.manifest = manifestPath;
    }

    const info: ExtractedSnapshotInfo = {
      page: marker.page,
      state: marker.state,
      outputDir: snapshotDir,
      files,
    };

    const key = `${marker.page}/${marker.state}`;
    const existingIdx = seen.get(key);
    if (existingIdx !== undefined) {
      snapshots[existingIdx] = info;
    } else {
      seen.set(key, snapshots.length);
      snapshots.push(info);
    }

    options.onSnapshot?.(info);
  }

  return { snapshots };
}

function matchesFilter(
  marker: TraceMarker,
  filter?: { page?: string; state?: string },
): boolean {
  if (!filter) return true;
  if (filter.page && marker.page !== filter.page) return false;
  if (filter.state && marker.state !== filter.state) return false;
  return true;
}

function pickClosestScreencastSha1(
  backend: TraceBackend,
  marker: TraceMarker,
): string | undefined {
  if (!backend.getScreencastFrames) return undefined;
  const frames = backend.getScreencastFrames(marker.pageOrFrameId);
  if (!frames || frames.length === 0) return undefined;
  return closestByTime(frames, marker).sha1;
}

function closestByTime(frames: ScreencastFrame[], marker: TraceMarker): ScreencastFrame {
  let best = frames[0];
  const useWallTime = marker.wallTime !== undefined && frames.some((f) => f.frameSwapWallTime !== undefined);
  const target = useWallTime ? (marker.wallTime as number) : marker.timestamp;
  let bestDist = distance(best, target, useWallTime);
  for (const f of frames) {
    const d = distance(f, target, useWallTime);
    if (d < bestDist) {
      best = f;
      bestDist = d;
    }
  }
  return best;
}

function distance(f: ScreencastFrame, target: number, useWallTime: boolean): number {
  const value = useWallTime ? (f.frameSwapWallTime ?? f.timestamp) : f.timestamp;
  return Math.abs(value - target);
}

function buildTraceManifest(
  url: string,
  viewport: { width: number; height: number },
  marker: TraceMarker,
  driver: { name: string; version: string } | undefined,
): ManifestJson {
  const timestamp =
    marker.wallTime !== undefined
      ? new Date(marker.wallTime).toISOString()
      : new Date().toISOString();
  const manifest: ManifestJson = {
    version: MANIFEST_VERSION,
    url,
    viewport,
    timestamp,
  };
  if (driver) manifest[driver.name] = driver.version;
  return manifest;
}

function readIfExists(p: string): string | undefined {
  try {
    return fs.readFileSync(p, 'utf-8');
  } catch {
    return undefined;
  }
}
