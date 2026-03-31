/**
 * Post-generation script: copies trace ZIPs from Playwright test-results
 * to the expected fixture file names.
 *
 * Run after: npx playwright test --config tests/fixtures/fixtures.config.ts
 */

import * as fs from 'fs';
import * as path from 'path';

const FIXTURES_DIR = __dirname;
const RESULTS_DIR = path.join(FIXTURES_DIR, '.fixture-results');

interface Mapping {
  /** Substring to match in the test-results directory name */
  dirPattern: string;
  /** Output file name in the fixtures directory */
  outputName: string;
}

const mappings: Mapping[] = [
  { dirPattern: 'with-', outputName: 'sample-trace.zip' },
  { dirPattern: 'no-markers', outputName: 'no-markers-trace.zip' },
];

function findTraceZip(pattern: string): string | undefined {
  if (!fs.existsSync(RESULTS_DIR)) return undefined;
  const entries = fs.readdirSync(RESULTS_DIR, { withFileTypes: true });
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    if (!entry.name.includes(pattern)) continue;
    const traceZip = path.join(RESULTS_DIR, entry.name, 'trace.zip');
    if (fs.existsSync(traceZip)) return traceZip;
  }
  return undefined;
}

let success = true;

for (const { dirPattern, outputName } of mappings) {
  const source = findTraceZip(dirPattern);
  if (!source) {
    console.error(`ERROR: Could not find trace.zip for pattern "${dirPattern}"`);
    success = false;
    continue;
  }
  const dest = path.join(FIXTURES_DIR, outputName);
  fs.copyFileSync(source, dest);
  const size = fs.statSync(dest).size;
  console.log(`Copied ${outputName} (${(size / 1024).toFixed(1)} KB)`);
}

if (success) {
  console.log('\nAll fixture traces generated successfully.');
} else {
  console.error('\nSome fixtures could not be generated.');
  process.exit(1);
}
