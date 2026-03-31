import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

import {
  findTraceZipsInReport,
  isPlaywrightReportDir,
} from '../src/sources/directory-source';
import { createBackendFromZip } from '../src/sources/zip-source';

/**
 * Unit tests for the source loader modules.
 *
 * directory-source and zip-source are tested against the filesystem using
 * temporary directories and a real trace ZIP from the test-results directory.
 *
 * url-source requires a live HTTP server and is covered by a separate
 * integration test (not run here).
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
