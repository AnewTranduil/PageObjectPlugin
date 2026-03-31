import * as fs from 'fs';
import * as path from 'path';
import { ExtractOptions, ExtractResult } from './types';
import {
  loadTraceMarkers,
  renderSnapshotAtMarker,
  findScreencastFrame,
  TraceSnapshotMarker,
} from './trace/playwright-adapter';
import { createBackendFromZip } from './sources/zip-source';
import { findTraceZipsInReport, isPlaywrightReportDir } from './sources/directory-source';
import { downloadTracesFromUrl } from './sources/url-source';

// ---------------------------------------------------------------------------
// Source type detection
// ---------------------------------------------------------------------------

type SourceType = 'url' | 'zip' | 'directory';

function detectSourceType(source: string): SourceType {
  if (source.startsWith('http://') || source.startsWith('https://')) {
    return 'url';
  }
  if (source.endsWith('.zip')) {
    return 'zip';
  }
  return 'directory';
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Extracts snapshots from Playwright traces.
 *
 * Accepts a report directory, a single trace ZIP, or a hosted report URL.
 * Finds all `snapshot()` markers in the traces, renders each to HTML,
 * and writes the output to `outputDir/page/state/`.
 */
export async function extractSnapshots(options: ExtractOptions): Promise<ExtractResult> {
  const outputDir = options.outputDir ?? '.snapshots';
  const screenshotEnabled = options.screenshot !== false;
  const manifestEnabled = options.manifest !== false;

  const sourceType = detectSourceType(options.source);

  // Collect trace ZIP paths and an optional cleanup function (for URL sources).
  let zipPaths: string[];
  let sourceCleanup: (() => void) | undefined;

  switch (sourceType) {
    case 'url': {
      const result = await downloadTracesFromUrl(options.source);
      zipPaths = result.zipPaths;
      sourceCleanup = result.cleanup;
      break;
    }
    case 'zip': {
      zipPaths = [options.source];
      break;
    }
    case 'directory': {
      if (!isPlaywrightReportDir(options.source)) {
        throw new Error(
          `Directory does not look like a Playwright HTML report: ${options.source}\n` +
          `Expected to find index.html and data/ subdirectory.`,
        );
      }
      zipPaths = findTraceZipsInReport(options.source);
      if (zipPaths.length === 0) {
        throw new Error(
          `No trace ZIP files found in ${options.source}/data/`,
        );
      }
      break;
    }
  }

  try {
    const result = await processTraceZips(zipPaths, {
      outputDir,
      screenshotEnabled,
      manifestEnabled,
      filter: options.filter,
    });
    return result;
  } finally {
    sourceCleanup?.();
  }
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

interface ProcessOptions {
  outputDir: string;
  screenshotEnabled: boolean;
  manifestEnabled: boolean;
  filter?: { page?: string; state?: string };
}

async function processTraceZips(
  zipPaths: string[],
  opts: ProcessOptions,
): Promise<ExtractResult> {
  const seen = new Map<string, number>(); // "page/state" -> index in snapshots
  const snapshots: ExtractResult['snapshots'] = [];

  for (const zipPath of zipPaths) {
    const { backend, cleanup } = createBackendFromZip(zipPath);
    try {
      const { markers, loader } = await loadTraceMarkers(backend);

      if (markers.length === 0) {
        console.warn(
          `No snapshot markers found in ${zipPath}. ` +
          `Make sure your tests call snapshot({ page, state }) from playwright-snapshot-saver.`,
        );
        continue;
      }

      for (const marker of markers) {
        if (!matchesFilter(marker, opts.filter)) continue;

        const key = `${marker.page}/${marker.state}`;
        if (seen.has(key)) {
          console.warn(
            `Duplicate snapshot key "${key}" — overwriting previous entry ` +
            `(from a different trace).`,
          );
        }

        const snapshotDir = path.join(opts.outputDir, marker.page, marker.state);
        fs.mkdirSync(snapshotDir, { recursive: true });

        const rendered = await renderSnapshotAtMarker(loader, marker);

        const htmlPath = path.join(snapshotDir, 'index.html');
        fs.writeFileSync(htmlPath, rendered.html, 'utf-8');

        const files: ExtractResult['snapshots'][number]['files'] = { html: htmlPath };

        if (opts.screenshotEnabled) {
          const frame = await findScreencastFrame(loader, marker);
          if (frame) {
            const screenshotPath = path.join(snapshotDir, 'screenshot.webp');
            fs.writeFileSync(screenshotPath, frame);
            files.screenshot = screenshotPath;
          }
        }

        if (opts.manifestEnabled) {
          const manifest = {
            version: 1,
            url: '',
            viewport: rendered.viewport,
            timestamp: new Date(marker.timestamp).toISOString(),
            playwright: getPlaywrightVersion(),
          };
          const manifestPath = path.join(snapshotDir, 'manifest.json');
          fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
          files.manifest = manifestPath;
        }

        const entry = {
          page: marker.page,
          state: marker.state,
          outputDir: snapshotDir,
          files,
        };

        const existingIdx = seen.get(key);
        if (existingIdx !== undefined) {
          snapshots[existingIdx] = entry;
        } else {
          seen.set(key, snapshots.length);
          snapshots.push(entry);
        }
      }
    } finally {
      cleanup();
    }
  }

  return { snapshots };
}

function matchesFilter(
  marker: TraceSnapshotMarker,
  filter?: { page?: string; state?: string },
): boolean {
  if (!filter) return true;
  if (filter.page && marker.page !== filter.page) return false;
  if (filter.state && marker.state !== filter.state) return false;
  return true;
}

function getPlaywrightVersion(): string {
  try {
    const pkgPath = require.resolve('playwright-core/package.json');
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
    return pkg.version ?? 'unknown';
  } catch {
    return 'unknown';
  }
}
