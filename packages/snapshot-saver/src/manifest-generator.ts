import { Page } from '@playwright/test';
import * as fs from 'fs';
import { ManifestJson } from './types';

export async function generateManifest(page: Page): Promise<ManifestJson> {
  const viewportSize = page.viewportSize() ?? { width: 1280, height: 720 };
  const userAgent = await page.evaluate(() => navigator.userAgent);

  let playwrightVersion = 'unknown';
  try {
    const pkgPath = require.resolve('@playwright/test/package.json');
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
    playwrightVersion = pkg.version;
  } catch {
    // fallback
  }

  return {
    version: 1,
    url: page.url(),
    viewport: { width: viewportSize.width, height: viewportSize.height },
    timestamp: new Date().toISOString(),
    playwright: playwrightVersion,
    userAgent,
  };
}
