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

  // Run all generators in parallel
  const [html, manifest, _screenshot] = await Promise.all([
    generateInlinedHtml(page),
    manifestEnabled ? generateManifest(page) : Promise.resolve(null),
    screenshotEnabled
      ? page.screenshot({
          path: path.join(outDir, `screenshot.${screenshotFormat}`),
          type: screenshotFormat,
          fullPage: screenshotFullPage,
        })
      : Promise.resolve(null),
  ]);

  const htmlPath = path.join(outDir, 'index.html');
  fs.writeFileSync(htmlPath, html, 'utf-8');

  const files: SnapshotResult['files'] = {
    html: htmlPath,
  };

  if (screenshotEnabled) {
    files.screenshot = path.join(outDir, `screenshot.${screenshotFormat}`);
  }

  if (manifestEnabled && manifest) {
    const manifestPath = path.join(outDir, 'manifest.json');
    fs.writeFileSync(manifestPath, JSON.stringify(manifest, null, 2), 'utf-8');
    files.manifest = manifestPath;
  }

  return {
    outputDir: outDir,
    files,
  };
}
