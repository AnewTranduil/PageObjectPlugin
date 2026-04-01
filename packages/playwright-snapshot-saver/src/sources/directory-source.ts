import * as fs from 'fs';
import * as path from 'path';

/**
 * Finds all trace ZIP files inside a Playwright HTML report directory.
 * Report structure: `playwright-report/data/<hash>.zip`
 */
export function findTraceZipsInReport(reportDir: string): string[] {
  const dataDir = path.join(reportDir, 'data');
  if (!fs.existsSync(dataDir)) {
    return [];
  }
  return fs.readdirSync(dataDir)
    .filter(f => f.endsWith('.zip'))
    .map(f => path.join(dataDir, f));
}

/**
 * Returns true if the given directory looks like a Playwright HTML report.
 * Checks for the presence of `index.html` and a `data/` subdirectory.
 */
export function isPlaywrightReportDir(dir: string): boolean {
  return (
    fs.existsSync(path.join(dir, 'index.html')) &&
    fs.existsSync(path.join(dir, 'data'))
  );
}
