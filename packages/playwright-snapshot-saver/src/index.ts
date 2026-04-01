import { Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { SaveSnapshotOptions, SnapshotResult } from './types';
import { generateInlinedHtml } from './html-inliner';
import { generateManifest } from './manifest-generator';

export { SaveSnapshotOptions, SnapshotResult, ManifestJson, SnapshotMarkerOptions, ExtractOptions, ExtractResult } from './types';
export { snapshot } from './snapshot-marker';
export { extractSnapshots } from './extractor';

export async function saveSnapshot(
  page: Page,
  options: SaveSnapshotOptions
): Promise<SnapshotResult> {
  const outDir = options.group
    ? path.join(options.outputDir, options.group, options.name)
    : path.join(options.outputDir, options.name);

  fs.mkdirSync(outDir, { recursive: true });

  const screenshotEnabled = options.screenshot?.enabled !== false;
  const screenshotFormat = options.screenshot?.format ?? 'png';
  const screenshotFullPage = options.screenshot?.fullPage ?? false;
  const manifestEnabled = options.manifest !== false;

  const htmlPath = path.join(outDir, 'index.html');
  const screenshotPath = path.join(outDir, `screenshot.${screenshotFormat}`);
  const manifestPath = path.join(outDir, 'manifest.json');

  // Read existing manifest version before generating new content
  let previousVersion: number | undefined;
  try {
    const existing = JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
    if (typeof existing.version === 'number') previousVersion = existing.version;
  } catch { /* no existing manifest */ }

  // Run all generators in parallel
  const [html, manifest, _screenshot] = await Promise.all([
    generateInlinedHtml(page),
    manifestEnabled ? generateManifest(page, previousVersion) : Promise.resolve(null),
    screenshotEnabled
      ? page.screenshot({
          path: screenshotPath,
          type: screenshotFormat,
          fullPage: screenshotFullPage,
        })
      : Promise.resolve(null),
  ]);

  // Check if HTML content actually changed
  let htmlChanged = true;
  try {
    htmlChanged = fs.readFileSync(htmlPath, 'utf-8') !== html;
  } catch { /* file doesn't exist */ }

  const files: SnapshotResult['files'] = {
    html: htmlPath,
  };

  if (htmlChanged) {
    fs.writeFileSync(htmlPath, html, 'utf-8');

    if (screenshotEnabled) {
      // Screenshot was already written by page.screenshot({ path })
      files.screenshot = screenshotPath;
    }

    if (manifestEnabled && manifest) {
      fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
      files.manifest = manifestPath;
    }
  } else {
    // Content unchanged — preserve existing files
    if (screenshotEnabled && fs.existsSync(screenshotPath)) {
      files.screenshot = screenshotPath;
    }
    if (manifestEnabled && fs.existsSync(manifestPath)) {
      files.manifest = manifestPath;
    }
  }

  return {
    outputDir: outDir,
    files,
  };
}
