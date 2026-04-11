import { describe, it, expect } from 'vitest';
import { assembleHtml } from '../src/assemble-html';
import { CapturedPage } from '../src/types';

function fakeCaptured(overrides: Partial<CapturedPage>): CapturedPage {
  return {
    html: '<html><head></head><body></body></html>',
    stylesheets: [],
    resources: [],
    url: 'https://example.com',
    viewport: { width: 1280, height: 720 },
    ...overrides,
  };
}

describe('assembleHtml', () => {
  it('prepends DOCTYPE when missing', () => {
    const { html } = assembleHtml(fakeCaptured({ html: '<html><body>x</body></html>' }));
    expect(html.startsWith('<!DOCTYPE html>\n')).toBe(true);
  });

  it('preserves an existing DOCTYPE', () => {
    const { html } = assembleHtml(
      fakeCaptured({ html: '<!DOCTYPE html>\n<html><body>x</body></html>' }),
    );
    const matches = html.match(/<!DOCTYPE html>/gi) ?? [];
    expect(matches).toHaveLength(1);
  });

  it('rewrites <link rel=stylesheet> to resources/<sha1>.css', () => {
    const { html, cssResources } = assembleHtml(
      fakeCaptured({
        html: '<html><head><link rel="stylesheet" href="https://cdn.example.com/main.css"></head><body></body></html>',
        stylesheets: [
          { href: 'https://cdn.example.com/main.css', source: 'body { color: red; }' },
        ],
      }),
    );
    expect(cssResources).toHaveLength(1);
    expect(cssResources[0].filename).toMatch(/^[a-f0-9]{16}\.css$/);
    const sidecarName = cssResources[0].filename;
    expect(html).toContain(`href="resources/${sidecarName}"`);
    expect(html).not.toContain('https://cdn.example.com/main.css');
  });

  it('honors differing attribute orders on <link>', () => {
    const { html, cssResources } = assembleHtml(
      fakeCaptured({
        html: '<html><head><link href="https://x/y.css" rel="stylesheet" crossorigin="anonymous"></head><body></body></html>',
        stylesheets: [{ href: 'https://x/y.css', source: 'a{}' }],
      }),
    );
    const sidecar = cssResources[0].filename;
    expect(html).toContain(`href="resources/${sidecar}"`);
  });

  it('leaves unknown <link> hrefs unchanged', () => {
    const { html } = assembleHtml(
      fakeCaptured({
        html: '<html><head><link rel="stylesheet" href="https://other.example/a.css"></head><body></body></html>',
        stylesheets: [{ href: 'https://known.example/b.css', source: 'b{}' }],
      }),
    );
    expect(html).toContain('https://other.example/a.css');
  });

  it('converts inline <style> blocks into sidecar + <link>', () => {
    const { html, cssResources } = assembleHtml(
      fakeCaptured({
        html: '<html><head><style>.foo { color: blue; }</style></head><body></body></html>',
        stylesheets: [{ source: '.foo { color: blue; }' }],
      }),
    );
    expect(cssResources).toHaveLength(1);
    const name = cssResources[0].filename;
    expect(html).not.toContain('<style>');
    expect(html).toContain(`<link rel="stylesheet" href="resources/${name}">`);
  });

  it('de-duplicates identical CSS across stylesheets', () => {
    const { cssResources } = assembleHtml(
      fakeCaptured({
        html: '<html><head><link rel="stylesheet" href="a.css"><link rel="stylesheet" href="b.css"></head><body></body></html>',
        stylesheets: [
          { href: 'a.css', source: 'shared{}' },
          { href: 'b.css', source: 'shared{}' },
        ],
      }),
    );
    expect(cssResources).toHaveLength(1);
  });

  it('passes through when there are no stylesheets', () => {
    const { html, cssResources } = assembleHtml(
      fakeCaptured({ html: '<html><body>plain</body></html>' }),
    );
    expect(cssResources).toHaveLength(0);
    expect(html).toContain('<html><body>plain</body></html>');
  });

  it('emits deterministic sidecar filenames for identical CSS', () => {
    const a = assembleHtml(
      fakeCaptured({
        stylesheets: [{ href: 'x.css', source: 'body{color:red}' }],
        html: '<html><head><link rel="stylesheet" href="x.css"></head><body></body></html>',
      }),
    );
    const b = assembleHtml(
      fakeCaptured({
        stylesheets: [{ href: 'x.css', source: 'body{color:red}' }],
        html: '<html><head><link rel="stylesheet" href="x.css"></head><body></body></html>',
      }),
    );
    expect(a.cssResources[0].filename).toBe(b.cssResources[0].filename);
  });
});
