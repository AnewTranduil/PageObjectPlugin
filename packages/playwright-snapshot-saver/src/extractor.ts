import {
  extractFromBackend,
  type TraceMarker,
  type ExtractFromBackendResult,
} from '@pagemirror/snapshot-core';
import { ExtractOptions, ExtractResult } from './types';
import { loadTraceMarkers } from './trace/playwright-adapter';
import { PlaywrightTraceBackend } from './trace/playwright-backend';
import { createBackendFromZip } from './sources/zip-source';
import { findTraceZipsInReport, isPlaywrightReportDir } from './sources/directory-source';
import { downloadTracesFromUrl } from './sources/url-source';
import { getPlaywrightVersion } from './playwright-version';

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

/**
 * Extracts snapshots from Playwright traces.
 *
 * Accepts a report directory, a single trace ZIP, or a hosted report URL.
 * Finds all `snapshot()` markers in the traces, renders each to HTML via
 * `@pagemirror/snapshot-core`, and writes v2 bundles to
 * `outputDir/page/state/`.
 */
export async function extractSnapshots(options: ExtractOptions): Promise<ExtractResult> {
  const outputDir = options.outputDir ?? '.snapshots';
  const screenshotEnabled = options.screenshot === true;
  const manifestEnabled = options.manifest !== false;

  const sourceType = detectSourceType(options.source);

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
    return await processTraceZips(zipPaths, {
      outputDir,
      screenshotEnabled,
      manifestEnabled,
      filter: options.filter,
    });
  } finally {
    sourceCleanup?.();
  }
}

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
  const seen = new Map<string, number>();
  const snapshots: ExtractResult['snapshots'] = [];

  const driver = { name: 'playwright', version: getPlaywrightVersion() };

  for (const zipPath of zipPaths) {
    const { backend: zipBackend, cleanup } = createBackendFromZip(zipPath);
    try {
      const { markers, loader } = await loadTraceMarkers(zipBackend);

      if (markers.length === 0) {
        console.warn(
          `No snapshot markers found in ${zipPath}. ` +
          `Make sure your tests call snapshot({ page, state }) from playwright-snapshot-saver.`,
        );
        continue;
      }

      const traceMarkers: TraceMarker[] = [];
      for (const marker of markers) {
        if (!marker.afterSnapshot) {
          console.warn(
            `Marker ${marker.label} (callId: ${marker.callId}) has no afterSnapshot — ` +
            `trace may have been recorded without snapshots enabled. Skipping.`,
          );
          continue;
        }
        traceMarkers.push({
          page: marker.page,
          state: marker.state,
          pageOrFrameId: marker.pageId,
          snapshotName: marker.afterSnapshot,
          timestamp: marker.timestamp,
          wallTime: marker.timestamp,
        });
      }

      const coreBackend = new PlaywrightTraceBackend(loader);
      const result: ExtractFromBackendResult = await extractFromBackend(
        coreBackend,
        traceMarkers,
        {
          outputDir: opts.outputDir,
          screenshot: opts.screenshotEnabled,
          manifest: opts.manifestEnabled,
          driver,
          filter: opts.filter,
          onSnapshot: (info) => {
            const key = `${info.page}/${info.state}`;
            if (seen.has(key)) {
              console.warn(
                `Duplicate snapshot key "${key}" — overwriting previous entry ` +
                `(from a different trace).`,
              );
            }
          },
        },
      );

      for (const info of result.snapshots) {
        const key = `${info.page}/${info.state}`;
        const entry: ExtractResult['snapshots'][number] = {
          page: info.page,
          state: info.state,
          outputDir: info.outputDir,
          files: {
            html: info.files.html,
            ...(info.files.manifest ? { manifest: info.files.manifest } : {}),
            ...(info.files.screenshot ? { screenshot: info.files.screenshot } : {}),
          },
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
