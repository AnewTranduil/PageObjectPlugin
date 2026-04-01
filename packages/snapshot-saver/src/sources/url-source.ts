import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

/**
 * Downloads trace ZIP files from a hosted Playwright HTML report.
 *
 * Strategy:
 *  1. Fetch index.html and extract the embedded base64 report zip
 *     from the `<template id="playwrightReportBase64">` element.
 *  2. Read `report.json` from inside that zip to discover trace
 *     attachment paths.
 *  3. Download each trace zip from `${baseUrl}/${attachmentPath}`.
 *
 * Returns the paths to the downloaded ZIPs and a cleanup function to remove
 * the temporary directory when the caller is done with the files.
 *
 * @internal Exported for testing: {@link readReportJsonFromHtml} extracts
 * the report.json from the embedded base64 zip in an HTML report page.
 */
export async function downloadTracesFromUrl(reportUrl: string): Promise<{
  zipPaths: string[];
  cleanup: () => void;
}> {
  const baseUrl = reportUrl.endsWith('/') ? reportUrl.slice(0, -1) : reportUrl;
  const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-snapshot-url-'));
  const zipPaths: string[] = [];

  try {
    const indexResponse = await fetch(`${baseUrl}/index.html`);
    if (!indexResponse.ok) {
      throw new Error(
        `Cannot connect to ${baseUrl}: ${indexResponse.status} ${indexResponse.statusText}`,
      );
    }
    const indexHtml = await indexResponse.text();

    const tracePaths = await extractTracePathsFromHtml(indexHtml);

    // Download each trace zip. Paths are relative to the report root
    // (typically "data/<sha1>.zip").
    for (const tracePath of tracePaths) {
      const traceUrl = `${baseUrl}/${tracePath}`;
      const zipPath = path.join(tmpDir, path.basename(tracePath));
      await downloadFile(traceUrl, zipPath);
      zipPaths.push(zipPath);
    }
  } catch (err) {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    throw err;
  }

  if (zipPaths.length === 0) {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    throw new Error(`No trace attachments found in the report at ${baseUrl}`);
  }

  return {
    zipPaths,
    cleanup: () => fs.rmSync(tmpDir, { recursive: true, force: true }),
  };
}

// ---------------------------------------------------------------------------
// Exported helpers (used by tests)
// ---------------------------------------------------------------------------

/**
 * Extracts trace attachment paths from the base64-embedded report zip in HTML.
 * Returns the relative paths (e.g. "data/<sha1>.zip") for each trace attachment.
 *
 * Supports Playwright report formats across versions:
 *  - ~1.49: JS assignment   `playwrightReportBase64 = "data:application/zip;base64,..."`
 *  - ~1.58: <script> tag    `<script id="playwrightReportBase64" type="application/zip">data:...`
 *  - 1.60+: <template> tag  `<template id="playwrightReportBase64">data:...`
 */
export async function extractTracePathsFromHtml(indexHtml: string): Promise<string[]> {
  // Format 1 (Playwright ~1.49): JS variable assignment with base64 data URI in quotes
  const jsMatch = indexHtml.match(
    /playwrightReportBase64\s*=\s*"data:application\/zip;base64,([^"]+)"/,
  );
  // Format 2 (Playwright ~1.58+): <script> or <template> tag with id="playwrightReportBase64"
  const tagMatch = !jsMatch
    ? indexHtml.match(
        /id="playwrightReportBase64"[^>]*>data:application\/zip;base64,([^<]+)</,
      )
    : null;

  const base64Match = jsMatch || tagMatch;
  if (!base64Match) {
    throw new Error('No embedded report data found in HTML');
  }

  const zipBuffer = Buffer.from(base64Match[1], 'base64');
  const reportJson = await readEntryFromZipBuffer(zipBuffer, 'report.json');
  const report = JSON.parse(reportJson) as ReportJson;

  const tracePaths: string[] = [];
  for (const file of report?.files ?? []) {
    for (const test of file?.tests ?? []) {
      for (const result of test?.results ?? []) {
        for (const att of result?.attachments ?? []) {
          if (att.name === 'trace' && att.path) {
            if (!tracePaths.includes(att.path)) {
              tracePaths.push(att.path);
            }
          }
        }
      }
    }
  }
  return tracePaths;
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

type ReportJson = {
  files?: Array<{
    tests?: Array<{
      results?: Array<{
        attachments?: Array<{ name: string; contentType: string; path?: string }>;
      }>;
    }>;
  }>;
};

/**
 * Reads a single entry from a zip buffer using yauzl from playwright-core.
 */
async function readEntryFromZipBuffer(buffer: Buffer, entryName: string): Promise<string> {
  const playwrightCorePath = path.dirname(require.resolve('playwright-core/package.json'));
  // eslint-disable-next-line @typescript-eslint/no-var-requires
  const { yauzl } = require(path.join(playwrightCorePath, 'lib', 'zipBundle'));

  return new Promise<string>((resolve, reject) => {
    yauzl.fromBuffer(buffer, { lazyEntries: true }, (err: Error | null, zipFile: any) => {
      if (err) return reject(err);

      let found = false;
      zipFile.on('entry', (entry: any) => {
        if (entry.fileName === entryName) {
          found = true;
          zipFile.openReadStream(entry, (err2: Error | null, stream: any) => {
            if (err2) return reject(err2);
            const chunks: Buffer[] = [];
            stream.on('data', (chunk: Buffer) => chunks.push(chunk));
            stream.on('end', () => resolve(Buffer.concat(chunks).toString('utf-8')));
            stream.on('error', reject);
          });
        } else {
          zipFile.readEntry();
        }
      });

      zipFile.on('end', () => {
        if (!found) reject(new Error(`Entry "${entryName}" not found in zip`));
      });
      zipFile.on('error', reject);
      zipFile.readEntry();
    });
  });
}

/**
 * Downloads a single file from `url` and writes it to `dest`.
 */
async function downloadFile(url: string, dest: string): Promise<void> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to download ${url}: ${response.status} ${response.statusText}`);
  }
  const buffer = Buffer.from(await response.arrayBuffer());
  fs.writeFileSync(dest, buffer);
}
