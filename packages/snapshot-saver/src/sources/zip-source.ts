/**
 * Creates a TraceLoaderBackend from a Playwright trace ZIP file.
 *
 * Uses Playwright's internal `ZipTraceLoaderBackend` from:
 *   playwright-core/lib/server/trace/viewer/traceParser
 *
 * NOTE: There is no `DirTraceLoaderBackend` or `extractTrace` in the installed
 * playwright-core. `ZipTraceLoaderBackend` reads ZIP entries directly via the
 * internal `ZipFile` wrapper — no temporary directory extraction is required.
 *
 * The @isomorphic/* alias patch from playwright-adapter.ts must be applied
 * before this module is used (the adapter is imported first by the extractor).
 */

import * as fs from 'fs';
import * as path from 'path';
import type { TraceLoaderBackend } from 'playwright-core/lib/utils/isomorphic/trace/traceLoader';

// ---------------------------------------------------------------------------
// Load ZipTraceLoaderBackend from Playwright internals at require-time.
// ---------------------------------------------------------------------------

const playwrightCorePath = path.dirname(require.resolve('playwright-core/package.json'));

// eslint-disable-next-line @typescript-eslint/no-var-requires
const { ZipTraceLoaderBackend } = require(
  path.join(playwrightCorePath, 'lib', 'server', 'trace', 'viewer', 'traceParser'),
) as { ZipTraceLoaderBackend: new (zipPath: string) => TraceLoaderBackend };

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Creates a `TraceLoaderBackend` that reads directly from a trace ZIP file.
 *
 * Returns a cleanup function for symmetry with future backends that may
 * need to extract to a temp directory, but for the current implementation
 * nothing needs to be cleaned up.
 */
export function createBackendFromZip(zipPath: string): {
  backend: TraceLoaderBackend;
  cleanup: () => void;
} {
  if (!fs.existsSync(zipPath)) {
    throw new Error(`Trace file not found: ${zipPath}`);
  }
  return {
    backend: new ZipTraceLoaderBackend(zipPath),
    cleanup: () => { /* ZipTraceLoaderBackend reads lazily; nothing to clean up */ },
  };
}
