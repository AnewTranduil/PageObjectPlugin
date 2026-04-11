import * as fs from 'fs';

/**
 * Reads the Playwright version from `playwright-core`'s own package.json
 * at runtime. Used by `saveSnapshot`'s manifest driver field and by
 * `extractor.ts`'s trace-extraction path. Returns `"unknown"` when the
 * package isn't resolvable — the manifest still writes the field so
 * downstream tooling can tell the driver was Playwright.
 */
export function getPlaywrightVersion(): string {
  try {
    const pkgPath = require.resolve('playwright-core/package.json');
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
    return pkg.version ?? 'unknown';
  } catch {
    return 'unknown';
  }
}
