import { describe, it, expect } from 'vitest';
import { buildManifest } from '../src/manifest';
import { CapturedPage, MANIFEST_VERSION } from '../src/types';

function fakeCaptured(overrides: Partial<CapturedPage> = {}): CapturedPage {
  return {
    html: '<html></html>',
    stylesheets: [],
    resources: [],
    url: 'https://example.com/login',
    viewport: { width: 1280, height: 720 },
    userAgent: 'Mozilla/5.0 test',
    ...overrides,
  };
}

describe('buildManifest', () => {
  const now = new Date('2026-04-11T12:00:00Z');

  it('writes the current schema version', () => {
    const m = buildManifest(fakeCaptured(), undefined, now);
    expect(m.version).toBe(MANIFEST_VERSION);
    expect(m.version).toBe(2);
  });

  it('copies url / viewport / userAgent verbatim from captured', () => {
    const m = buildManifest(
      fakeCaptured({
        url: 'https://x.y/z',
        viewport: { width: 800, height: 600 },
        userAgent: 'ua-string',
      }),
      undefined,
      now,
    );
    expect(m.url).toBe('https://x.y/z');
    expect(m.viewport).toEqual({ width: 800, height: 600 });
    expect(m.userAgent).toBe('ua-string');
  });

  it('writes the driver identity under its own key', () => {
    const pw = buildManifest(fakeCaptured(), { name: 'playwright', version: '1.58.2' }, now);
    expect(pw.playwright).toBe('1.58.2');
    expect(pw.selenium).toBeUndefined();

    const se = buildManifest(fakeCaptured(), { name: 'selenium', version: '4.18.0' }, now);
    expect(se.selenium).toBe('4.18.0');
    expect(se.playwright).toBeUndefined();
  });

  it('omits userAgent when captured has none', () => {
    const m = buildManifest(fakeCaptured({ userAgent: undefined }), undefined, now);
    expect(m.userAgent).toBeUndefined();
  });

  it('emits an ISO-8601 UTC timestamp', () => {
    const m = buildManifest(fakeCaptured(), undefined, now);
    expect(m.timestamp).toBe('2026-04-11T12:00:00.000Z');
  });
});
