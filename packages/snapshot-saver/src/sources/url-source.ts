import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

/**
 * Downloads trace ZIP files from a hosted Playwright HTML report.
 *
 * Supports two report formats:
 *  1. Reports with an embedded base64 `playwrightReportBase64` blob in the HTML
 *     (produced by newer Playwright versions / self-contained reports).
 *  2. Reports served from a directory where `data/*.zip` files are listed
 *     via an HTML directory index.
 *
 * Returns the paths to the downloaded ZIPs and a cleanup function to remove
 * the temporary directory when the caller is done with the files.
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

    // Some Playwright report versions embed all report data as base64 JSON
    // inside an element with id="playwrightReportBase64".
    const base64Match = indexHtml.match(/id="playwrightReportBase64"[^>]*>([^<]+)/);
    if (base64Match) {
      const reportData = JSON.parse(
        Buffer.from(base64Match[1], 'base64').toString('utf-8'),
      );
      zipPaths.push(...(await downloadTraceAttachments(reportData, baseUrl, tmpDir)));
    } else {
      zipPaths.push(...(await downloadFromDataDir(baseUrl, tmpDir)));
    }
  } catch (err) {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    throw err;
  }

  if (zipPaths.length === 0) {
    fs.rmSync(tmpDir, { recursive: true, force: true });
    throw new Error(`No Playwright report data found at ${baseUrl}`);
  }

  return {
    zipPaths,
    cleanup: () => fs.rmSync(tmpDir, { recursive: true, force: true }),
  };
}

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Walks the parsed report JSON and downloads any trace attachments.
 * Report data structure mirrors the Playwright HTML report bundle format.
 */
async function downloadTraceAttachments(
  reportData: unknown,
  baseUrl: string,
  tmpDir: string,
): Promise<string[]> {
  const zipPaths: string[] = [];
  const data = reportData as {
    files?: Array<{
      tests?: Array<{
        results?: Array<{
          attachments?: Array<{ name: string; path?: string }>;
        }>;
      }>;
    }>;
  };
  const files = data?.files ?? [];
  for (const file of files) {
    for (const t of file?.tests ?? []) {
      for (const result of t?.results ?? []) {
        for (const att of result?.attachments ?? []) {
          if (att.name === 'trace' && att.path) {
            const traceUrl = `${baseUrl}/data/${att.path}`;
            const zipPath = path.join(tmpDir, path.basename(att.path));
            await downloadFile(traceUrl, zipPath);
            zipPaths.push(zipPath);
          }
        }
      }
    }
  }
  return zipPaths;
}

/**
 * Attempts to list `<baseUrl>/data/` as an HTML directory and download any
 * linked `.zip` files. Falls back silently when directory listing is disabled.
 */
async function downloadFromDataDir(baseUrl: string, tmpDir: string): Promise<string[]> {
  const zipPaths: string[] = [];
  try {
    const dataResponse = await fetch(`${baseUrl}/data/`);
    if (dataResponse.ok) {
      const html = await dataResponse.text();
      const zipLinks = html.match(/href="([^"]*\.zip)"/g) ?? [];
      for (const link of zipLinks) {
        const fileName = link.match(/href="([^"]*\.zip)"/)?.[1];
        if (fileName) {
          const zipPath = path.join(tmpDir, path.basename(fileName));
          await downloadFile(`${baseUrl}/data/${fileName}`, zipPath);
          zipPaths.push(zipPath);
        }
      }
    }
  } catch {
    // Directory listing not available — caller will handle empty result
  }
  return zipPaths;
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
