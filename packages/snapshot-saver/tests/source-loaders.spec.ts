import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

import {
  findTraceZipsInReport,
  isPlaywrightReportDir,
} from '../src/sources/directory-source';
import { createBackendFromZip } from '../src/sources/zip-source';
import {
  downloadTracesFromUrl,
  extractTracePathsFromHtml,
} from '../src/sources/url-source';

/**
 * Unit tests for the source loader modules.
 *
 * directory-source and zip-source are tested against the filesystem using
 * temporary directories and a real trace ZIP from the test-results directory.
 *
 * url-source tests use the real playwright-report from the test-project.
 */

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Creates a temporary directory, returns its path and a cleanup function. */
function makeTmpDir(): { dir: string; cleanup: () => void } {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pw-src-test-'));
  return { dir, cleanup: () => fs.rmSync(dir, { recursive: true, force: true }) };
}

// ---------------------------------------------------------------------------
// directory-source
// ---------------------------------------------------------------------------

test.describe('directory-source', () => {
  test.describe('isPlaywrightReportDir', () => {
    test('returns false for a nonexistent path', () => {
      expect(isPlaywrightReportDir('/definitely/does/not/exist')).toBe(false);
    });

    test('returns false for an empty directory', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        expect(isPlaywrightReportDir(dir)).toBe(false);
      } finally {
        cleanup();
      }
    });

    test('returns false when only index.html exists', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        fs.writeFileSync(path.join(dir, 'index.html'), '<html/>');
        expect(isPlaywrightReportDir(dir)).toBe(false);
      } finally {
        cleanup();
      }
    });

    test('returns false when only data/ directory exists', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        fs.mkdirSync(path.join(dir, 'data'));
        expect(isPlaywrightReportDir(dir)).toBe(false);
      } finally {
        cleanup();
      }
    });

    test('returns true when both index.html and data/ exist', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        fs.writeFileSync(path.join(dir, 'index.html'), '<html/>');
        fs.mkdirSync(path.join(dir, 'data'));
        expect(isPlaywrightReportDir(dir)).toBe(true);
      } finally {
        cleanup();
      }
    });
  });

  test.describe('findTraceZipsInReport', () => {
    test('returns empty array when reportDir does not exist', () => {
      expect(findTraceZipsInReport('/definitely/does/not/exist')).toEqual([]);
    });

    test('returns empty array when data/ subdirectory does not exist', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        expect(findTraceZipsInReport(dir)).toEqual([]);
      } finally {
        cleanup();
      }
    });

    test('returns empty array when data/ has no ZIP files', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        const dataDir = path.join(dir, 'data');
        fs.mkdirSync(dataDir);
        fs.writeFileSync(path.join(dataDir, 'trace.json'), '{}');
        fs.writeFileSync(path.join(dataDir, 'screenshot.png'), '');
        expect(findTraceZipsInReport(dir)).toEqual([]);
      } finally {
        cleanup();
      }
    });

    test('returns only ZIP files from data/', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        const dataDir = path.join(dir, 'data');
        fs.mkdirSync(dataDir);
        fs.writeFileSync(path.join(dataDir, 'abc123.zip'), '');
        fs.writeFileSync(path.join(dataDir, 'def456.zip'), '');
        fs.writeFileSync(path.join(dataDir, 'report.json'), '{}');
        fs.writeFileSync(path.join(dataDir, 'screenshot.png'), '');

        const result = findTraceZipsInReport(dir);
        expect(result).toHaveLength(2);
        expect(result).toContain(path.join(dataDir, 'abc123.zip'));
        expect(result).toContain(path.join(dataDir, 'def456.zip'));
      } finally {
        cleanup();
      }
    });

    test('returned paths are absolute', () => {
      const { dir, cleanup } = makeTmpDir();
      try {
        const dataDir = path.join(dir, 'data');
        fs.mkdirSync(dataDir);
        fs.writeFileSync(path.join(dataDir, 'trace.zip'), '');

        const [zipPath] = findTraceZipsInReport(dir);
        expect(path.isAbsolute(zipPath)).toBe(true);
      } finally {
        cleanup();
      }
    });
  });
});

// ---------------------------------------------------------------------------
// zip-source
// ---------------------------------------------------------------------------

test.describe('zip-source', () => {
  test('createBackendFromZip throws when ZIP file does not exist', () => {
    expect(() => createBackendFromZip('/definitely/does/not/exist.zip')).toThrow(
      'Trace file not found',
    );
  });

  test('createBackendFromZip returns a backend and a cleanup function', () => {
    // We need a real ZIP file on disk. Use the trace ZIP produced by the
    // snapshot-saver integration tests if available, otherwise create a
    // minimal stub ZIP (PK header only — enough for the constructor to run).
    const { dir, cleanup: cleanupTmp } = makeTmpDir();
    try {
      // Write the PK (ZIP) magic bytes so the file passes existsSync
      const zipPath = path.join(dir, 'stub.zip');
      // Minimal valid empty ZIP: local file header + end of central directory
      const emptyZip = Buffer.from([
        0x50, 0x4b, 0x05, 0x06, // End of central directory signature
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00,
      ]);
      fs.writeFileSync(zipPath, emptyZip);

      // The backend is created synchronously (no ZIP parsing at construction time)
      const { backend, cleanup } = createBackendFromZip(zipPath);

      expect(backend).toBeDefined();
      expect(typeof backend.entryNames).toBe('function');
      expect(typeof backend.hasEntry).toBe('function');
      expect(typeof backend.readText).toBe('function');
      expect(typeof backend.readBlob).toBe('function');
      expect(typeof backend.isLive).toBe('function');
      expect(backend.isLive()).toBe(false);
      expect(typeof cleanup).toBe('function');

      // Calling cleanup should not throw
      expect(() => cleanup()).not.toThrow();
    } finally {
      cleanupTmp();
    }
  });

  test('backend from a real trace ZIP can enumerate entries', async () => {
    // Locate the most recent trace ZIP produced by our own integration tests.
    // If none exists (e.g. clean CI checkout), skip gracefully.
    const testResultsDir = path.join(
      __dirname,
      '..',
      'test-results',
    );
    let traceZip: string | undefined;
    if (fs.existsSync(testResultsDir)) {
      const entries = fs.readdirSync(testResultsDir, { withFileTypes: true });
      for (const entry of entries) {
        if (!entry.isDirectory()) continue;
        const candidate = path.join(testResultsDir, entry.name, 'trace.zip');
        if (fs.existsSync(candidate)) {
          traceZip = candidate;
          break;
        }
      }
    }

    if (!traceZip) {
      test.skip(); // No trace ZIP available in this environment
      return;
    }

    const { backend, cleanup } = createBackendFromZip(traceZip);
    try {
      const entries = await backend.entryNames();
      expect(Array.isArray(entries)).toBe(true);
      expect(entries.length).toBeGreaterThan(0);
      // Playwright trace ZIPs always contain at least one .trace file
      expect(entries.some(e => e.endsWith('.trace'))).toBe(true);
    } finally {
      cleanup();
    }
  });
});

// ---------------------------------------------------------------------------
// url-source
// ---------------------------------------------------------------------------



test.describe('url-source', () => {

  test.describe('playwright core 1.49.1', () => {
    const reportDir = path.join(__dirname, 'fixtures', 'playwright-report-1-49');
    const reportHtmlPath = path.join(reportDir, 'index.html');
    test.describe('extractTracePathsFromHtml', () => {
      test('throws for HTML without embedded report data', async () => {
        await expect(extractTracePathsFromHtml('<html><body></body></html>')).rejects.toThrow(
            'No embedded report data found',
        );
      });

      test(
          'extracts trace paths from real playwright-report/index.html',
          async () => {
            const html = fs.readFileSync(reportHtmlPath, 'utf-8');
            const paths = await extractTracePathsFromHtml(html);

            expect(paths.length).toBeGreaterThan(0);
            // Each path should look like "data/<hash>" (Playwright's default attachmentsBaseURL)
            for (const p of paths) {
              expect(p).toMatch(/^data\//);
            }
          },
      );

      test(
          'extracted paths match actual files in data/ directory',
          async () => {
            const html = fs.readFileSync(reportHtmlPath, 'utf-8');
            const paths = await extractTracePathsFromHtml(html);

            for (const p of paths) {
              const fullPath = path.join(reportDir, p);
              expect(fs.existsSync(fullPath)).toBe(true);
            }
          },
      );
    });

    test.describe('downloadTracesFromUrl (integration)', () => {
      let serverProcess: ReturnType<typeof import('child_process').spawn> | undefined;
      const port = 8199;

      test.beforeAll(async () => {
        // Start a static file server for the report directory
        const { spawn } = await import('child_process');
        serverProcess = spawn('npx', ['serve', reportDir, '-l', String(port), '--no-clipboard'], {
          shell: true,
          stdio: 'pipe',
        });
        // Wait for server to be ready
        await new Promise<void>((resolve, reject) => {
          const timeout = setTimeout(() => reject(new Error('Server start timeout')), 10000);
          const checkReady = async () => {
            try {
              const res = await fetch(`http://localhost:${port}/index.html`);
              if (res.ok) {
                clearTimeout(timeout);
                resolve();
                return;
              }
            } catch {
              // not ready yet
            }
            setTimeout(checkReady, 200);
          };
          checkReady();
        });
      });

      test.afterAll(() => {
        if (serverProcess) {
          serverProcess.kill();
          serverProcess = undefined;
        }
      });

      test(
          'downloads trace ZIPs from a served report',
          async () => {
            const result = await downloadTracesFromUrl(`http://localhost:${port}`);
            try {
              expect(result.zipPaths.length).toBeGreaterThan(0);
              for (const zipPath of result.zipPaths) {
                expect(fs.existsSync(zipPath)).toBe(true);
                // Each downloaded file should be a valid ZIP (starts with PK magic bytes)
                const header = Buffer.alloc(2);
                const fd = fs.openSync(zipPath, 'r');
                fs.readSync(fd, header, 0, 2, 0);
                fs.closeSync(fd);
                expect(header[0]).toBe(0x50); // P
                expect(header[1]).toBe(0x4b); // K
              }
            } finally {
              result.cleanup();
            }
          },
      );

      test('throws for unreachable URL', async () => {
        await expect(downloadTracesFromUrl('http://localhost:19999')).rejects.toThrow();
      });
    });
  })
  test.describe('playwright core 1.58.2', () => {
    const reportDir = path.join(__dirname, 'fixtures', 'playwright-report-1-58');
    const reportHtmlPath = path.join(reportDir, 'index.html');
    test.describe('extractTracePathsFromHtml', () => {
      test('throws for HTML without embedded report data', async () => {
        await expect(extractTracePathsFromHtml('<html><body></body></html>')).rejects.toThrow(
            'No embedded report data found',
        );
      });

      test(
          'extracts trace paths from real playwright-report/index.html',
          async () => {
            const html = fs.readFileSync(reportHtmlPath, 'utf-8');
            const paths = await extractTracePathsFromHtml(html);

            expect(paths.length).toBeGreaterThan(0);
            // Each path should look like "data/<hash>" (Playwright's default attachmentsBaseURL)
            for (const p of paths) {
              expect(p).toMatch(/^data\//);
            }
          },
      );

      test(
          'extracted paths match actual files in data/ directory',
          async () => {
            const html = fs.readFileSync(reportHtmlPath, 'utf-8');
            const paths = await extractTracePathsFromHtml(html);

            for (const p of paths) {
              const fullPath = path.join(reportDir, p);
              expect(fs.existsSync(fullPath)).toBe(true);
            }
          },
      );
    });

    test.describe('downloadTracesFromUrl (integration)', () => {
      let serverProcess: ReturnType<typeof import('child_process').spawn> | undefined;
      const port = 8199;

      test.beforeAll(async () => {
        // Start a static file server for the report directory
        const { spawn } = await import('child_process');
        serverProcess = spawn('npx', ['serve', reportDir, '-l', String(port), '--no-clipboard'], {
          shell: true,
          stdio: 'pipe',
        });
        // Wait for server to be ready
        await new Promise<void>((resolve, reject) => {
          const timeout = setTimeout(() => reject(new Error('Server start timeout')), 10000);
          const checkReady = async () => {
            try {
              const res = await fetch(`http://localhost:${port}/index.html`);
              if (res.ok) {
                clearTimeout(timeout);
                resolve();
                return;
              }
            } catch {
              // not ready yet
            }
            setTimeout(checkReady, 200);
          };
          checkReady();
        });
      });

      test.afterAll(() => {
        if (serverProcess) {
          serverProcess.kill();
          serverProcess = undefined;
        }
      });

      test(
          'downloads trace ZIPs from a served report',
          async () => {
            const result = await downloadTracesFromUrl(`http://localhost:${port}`);
            try {
              expect(result.zipPaths.length).toBeGreaterThan(0);
              for (const zipPath of result.zipPaths) {
                expect(fs.existsSync(zipPath)).toBe(true);
                // Each downloaded file should be a valid ZIP (starts with PK magic bytes)
                const header = Buffer.alloc(2);
                const fd = fs.openSync(zipPath, 'r');
                fs.readSync(fd, header, 0, 2, 0);
                fs.closeSync(fd);
                expect(header[0]).toBe(0x50); // P
                expect(header[1]).toBe(0x4b); // K
              }
            } finally {
              result.cleanup();
            }
          },
      );

      test('throws for unreachable URL', async () => {
        await expect(downloadTracesFromUrl('http://localhost:19999')).rejects.toThrow();
      });
    });
  })
});
