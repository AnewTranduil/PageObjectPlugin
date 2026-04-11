import * as fs from 'fs';
import * as path from 'path';
import { assembleHtml } from './assemble-html';
import { buildManifest } from './manifest';
import {
  CaptureRequest,
  CapturedPage,
  PageAdapter,
  Resource,
  SaveSnapshotOptions,
  SnapshotResult,
  ScreenshotOptions,
} from './types';

const DEFAULT_SCREENSHOT: ScreenshotOptions = { format: 'webp', fullPage: false };

/**
 * Framework-agnostic snapshot save. Takes a driver adapter, asks it to
 * capture a `CapturedPage`, runs the core assembly pipeline, and writes
 * the bundle under `<outputDir>/<group?>/<name>/` in the v2 layout:
 *
 *   index.html             — rewritten to reference resources/
 *   manifest.json          — schema version 2
 *   resources/<sha1>.css   — CSS sidecars
 *   resources/screenshot.* — if the adapter produced one
 *
 * Skip-write-if-unchanged: when the assembled HTML matches an existing
 * `index.html`, no files are rewritten. The function still returns the
 * full set of existing file paths in `SnapshotResult.files` so callers
 * can surface them unconditionally.
 */
export async function saveSnapshot(
  adapter: PageAdapter,
  options: SaveSnapshotOptions,
): Promise<SnapshotResult> {
  const outDir = options.group
    ? path.join(options.outputDir, options.group, options.name)
    : path.join(options.outputDir, options.name);

  fs.mkdirSync(outDir, { recursive: true });

  const screenshotRequest = resolveScreenshot(options.screenshot);

  const captureRequest: CaptureRequest = {
    extraSelectors: options.extraSelectors,
    excludeSelectors: options.excludeSelectors,
    extraAttributes: options.extraAttributes,
    ...(screenshotRequest !== undefined ? { screenshot: screenshotRequest } : {}),
  };

  const captured: CapturedPage = await adapter.capture(captureRequest);

  const { html, cssResources } = assembleHtml(captured);

  // Merge CSS sidecars with adapter-supplied resources (e.g. screenshot).
  // Adapter resources win on filename collision (practically never happens).
  const allResources = mergeResources(captured.resources, cssResources);

  const htmlPath = path.join(outDir, 'index.html');
  const resourcesDir = path.join(outDir, 'resources');
  const manifestPath = path.join(outDir, 'manifest.json');

  const htmlChanged = readIfExists(htmlPath) !== html;
  const writtenResourcePaths: string[] = [];

  if (htmlChanged) {
    fs.writeFileSync(htmlPath, html, 'utf-8');
    if (allResources.length > 0) {
      fs.mkdirSync(resourcesDir, { recursive: true });
      for (const res of allResources) {
        const resPath = path.join(resourcesDir, res.filename);
        fs.writeFileSync(resPath, Buffer.from(res.bytes));
        writtenResourcePaths.push(resPath);
      }
    }
  } else {
    // HTML unchanged — still record the existing resource paths so the
    // caller can reference them. Only list resources that actually exist
    // on disk (a previous run may have been disabled).
    if (fs.existsSync(resourcesDir)) {
      for (const res of allResources) {
        const resPath = path.join(resourcesDir, res.filename);
        if (fs.existsSync(resPath)) {
          writtenResourcePaths.push(resPath);
        }
      }
    }
  }

  const files: SnapshotResult['files'] = {
    html: htmlPath,
    resources: writtenResourcePaths,
  };

  const manifestEnabled = options.manifest !== false;
  if (manifestEnabled) {
    const manifest = buildManifest(captured, options.driver);
    if (htmlChanged) {
      fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
    } else if (!fs.existsSync(manifestPath)) {
      // First run with a disabled manifest, now enabled — write it anyway.
      fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
    }
    files.manifest = manifestPath;
  }

  return {
    outputDir: outDir,
    files,
  };
}

function resolveScreenshot(
  option: SaveSnapshotOptions['screenshot'],
): ScreenshotOptions | undefined {
  if (option === false) return undefined;
  if (option === undefined) return DEFAULT_SCREENSHOT;
  return {
    format: option.format ?? DEFAULT_SCREENSHOT.format,
    fullPage: option.fullPage ?? DEFAULT_SCREENSHOT.fullPage,
  };
}

function mergeResources(primary: Resource[], secondary: Resource[]): Resource[] {
  const seen = new Set<string>();
  const out: Resource[] = [];
  for (const r of primary) {
    if (!seen.has(r.filename)) {
      seen.add(r.filename);
      out.push(r);
    }
  }
  for (const r of secondary) {
    if (!seen.has(r.filename)) {
      seen.add(r.filename);
      out.push(r);
    }
  }
  return out;
}

function readIfExists(p: string): string | undefined {
  try {
    return fs.readFileSync(p, 'utf-8');
  } catch {
    return undefined;
  }
}
