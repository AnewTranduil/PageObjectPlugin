import { Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import { SaveSnapshotOptions, SnapshotResult } from './types';
import { generateInlinedHtml } from './html-inliner';
import { generateLayout } from './layout-generator';
import { generateManifest } from './manifest-generator';

export { SaveSnapshotOptions, SnapshotResult, LayoutJson, LayoutElement, ManifestJson } from './types';

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
  const [html, layout, manifest, _screenshot] = await Promise.all([
    generateInlinedHtml(page),
    generateLayout(page, {
      extraSelectors: options.extraSelectors,
      excludeSelectors: options.excludeSelectors,
      extraAttributes: options.extraAttributes,
    }),
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
  const layoutPath = path.join(outDir, 'layout.json');
  fs.writeFileSync(htmlPath, html, 'utf-8');
  fs.writeFileSync(layoutPath, JSON.stringify(layout, null, 2), 'utf-8');

  const files: SnapshotResult['files'] = {
    html: htmlPath,
    layout: layoutPath,
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
    elementCount: layout.elements.length,
    files,
  };
}
