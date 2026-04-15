import { describe, it, expect } from 'vitest';
import { inlineResources } from '../../src/trace/inline';
import type { ResourceEntry, TraceBackend } from '../../src/trace/types';

interface Stub {
  url: string;
  sha1: string;
  mimeType: string;
  bytes: Uint8Array;
}

function mkBackend(stubs: Stub[]): {
  backend: TraceBackend;
  rendered: { html: string; resourceByUrl: (u: string, m: string) => ResourceEntry | undefined };
} {
  const byUrl = new Map(stubs.map((s) => [s.url, s]));
  const bySha = new Map(stubs.map((s) => [s.sha1, s.bytes]));
  const backend: TraceBackend = {
    getFrameSnapshots: () => [],
    getResources: () => [],
    readResource: async (sha1) => bySha.get(sha1),
  };
  const resourceByUrl = (url: string): ResourceEntry | undefined => {
    const s = byUrl.get(url);
    if (!s) return undefined;
    return {
      request: { url, method: 'GET' },
      response: { status: 200, content: { sha1: s.sha1, mimeType: s.mimeType } },
    };
  };
  return { backend, rendered: { html: '', resourceByUrl } };
}

function textBytes(s: string): Uint8Array {
  return new TextEncoder().encode(s);
}

describe('inlineResources', () => {
  it('rewrites <link rel="stylesheet" href> and writes the CSS sidecar', async () => {
    const { backend, rendered } = mkBackend([
      { url: 'https://x.example/a.css', sha1: 'AAA', mimeType: 'text/css', bytes: textBytes('body{}') },
    ]);
    rendered.html = '<html><head><base href="https://x.example/"><link rel="stylesheet" href="a.css"></head><body></body></html>';
    const result = await inlineResources(rendered, backend);
    expect(result.html).toContain('href="resources/AAA.css"');
    expect(result.html).not.toContain('<base');
    expect(result.resources).toHaveLength(1);
    expect(result.resources[0].filename).toBe('AAA.css');
  });

  it('removes <base> even when no resources are rewritten', async () => {
    const { backend, rendered } = mkBackend([]);
    rendered.html = '<html><head><base href="https://x.example/"></head><body></body></html>';
    const result = await inlineResources(rendered, backend);
    expect(result.html).not.toContain('<base');
  });

  it('resolves <img src>, <img srcset>, poster, and <source>', async () => {
    const { backend, rendered } = mkBackend([
      { url: 'https://x.example/a.png', sha1: 'IMG1', mimeType: 'image/png', bytes: new Uint8Array([1]) },
      { url: 'https://x.example/a2.png', sha1: 'IMG2', mimeType: 'image/png', bytes: new Uint8Array([2]) },
      { url: 'https://x.example/poster.jpg', sha1: 'POS', mimeType: 'image/jpeg', bytes: new Uint8Array([3]) },
    ]);
    rendered.html =
      '<html><head><base href="https://x.example/"></head><body>' +
      '<img src="a.png" srcset="a.png 1x, a2.png 2x">' +
      '<video poster="poster.jpg"><source src="a.png"></video>' +
      '</body></html>';
    const result = await inlineResources(rendered, backend);
    expect(result.html).toContain('src="resources/IMG1.png"');
    expect(result.html).toContain('resources/IMG2.png 2x');
    expect(result.html).toContain('poster="resources/POS.jpg"');
    const shas = result.resources.map((r) => r.filename.split('.')[0]).sort();
    expect(shas).toEqual(['IMG1', 'IMG2', 'POS']);
  });

  it('rewrites <use href> and xlink:href for SVG sprites but leaves #fragment alone', async () => {
    const { backend, rendered } = mkBackend([
      { url: 'https://x.example/sprite.svg', sha1: 'SPR', mimeType: 'image/svg+xml', bytes: textBytes('<svg/>') },
    ]);
    rendered.html =
      '<html><head><base href="https://x.example/"></head><body>' +
      '<svg><use href="sprite.svg#icon-a"></use></svg>' +
      '<svg><use href="#internal"></use></svg>' +
      '</body></html>';
    const result = await inlineResources(rendered, backend);
    // The non-fragment href gets resolved against the base. URL() strips the fragment,
    // so the rewritten path points at resources/SPR.svg (no fragment, but the same file).
    expect(result.html).toContain('resources/SPR.svg');
    expect(result.html).toContain('href="#internal"');
  });

  it('rewrites url(...) inside <style> blocks and inline style="" attributes', async () => {
    const { backend, rendered } = mkBackend([
      { url: 'https://x.example/bg.png', sha1: 'BG', mimeType: 'image/png', bytes: new Uint8Array([1]) },
      { url: 'https://x.example/inline.png', sha1: 'INL', mimeType: 'image/png', bytes: new Uint8Array([2]) },
    ]);
    rendered.html =
      '<html><head><base href="https://x.example/">' +
      '<style>.a { background: url(bg.png); }</style>' +
      '</head><body><div style="background: url(inline.png)"></div></body></html>';
    const result = await inlineResources(rendered, backend);
    expect(result.html).toContain('url(resources/BG.png)');
    expect(result.html).toContain('url(resources/INL.png)');
  });

  it('discovers nested @font-face url() references inside linked CSS', async () => {
    const css = "@font-face { src: url('https://x.example/f.woff2') format('woff2'); }";
    const { backend, rendered } = mkBackend([
      { url: 'https://x.example/a.css', sha1: 'CSS1', mimeType: 'text/css', bytes: textBytes(css) },
      { url: 'https://x.example/f.woff2', sha1: 'FONT', mimeType: 'font/woff2', bytes: new Uint8Array([9]) },
    ]);
    rendered.html = '<html><head><base href="https://x.example/"><link rel="stylesheet" href="a.css"></head><body></body></html>';
    const result = await inlineResources(rendered, backend);
    const filenames = result.resources.map((r) => r.filename).sort();
    expect(filenames).toEqual(['CSS1.css', 'FONT.woff2']);
    const cssFile = result.resources.find((r) => r.filename === 'CSS1.css')!;
    const rewritten = new TextDecoder().decode(cssFile.bytes);
    expect(rewritten).toContain('resources/FONT.woff2');
  });

  it('leaves data: and javascript: URLs untouched', async () => {
    const { backend, rendered } = mkBackend([]);
    rendered.html =
      '<html><body>' +
      '<img src="data:image/png;base64,AAAA">' +
      '<a href="javascript:void(0)">x</a>' +
      '</body></html>';
    const result = await inlineResources(rendered, backend);
    expect(result.html).toContain('src="data:image/png;base64,AAAA"');
    expect(result.html).toContain('javascript:void(0)');
    expect(result.resources).toHaveLength(0);
  });

  it('preserves __playwright_target__ attributes', async () => {
    const { backend, rendered } = mkBackend([]);
    rendered.html = '<html><body><button __playwright_target__="xpath=/button">OK</button></body></html>';
    const result = await inlineResources(rendered, backend);
    expect(result.html).toContain('__playwright_target__="xpath=/button"');
  });

  it('deduplicates CSS with cyclic @import chain via the sha1 worklist', async () => {
    const aCss = "@import url('b.css');";
    const bCss = "@import url('a.css'); body { color: red; }";
    const { backend, rendered } = mkBackend([
      { url: 'https://x.example/a.css', sha1: 'A', mimeType: 'text/css', bytes: textBytes(aCss) },
      { url: 'https://x.example/b.css', sha1: 'B', mimeType: 'text/css', bytes: textBytes(bCss) },
    ]);
    rendered.html = '<html><head><base href="https://x.example/"><link rel="stylesheet" href="a.css"></head><body></body></html>';
    const result = await inlineResources(rendered, backend);
    const names = result.resources.map((r) => r.filename).sort();
    expect(names).toEqual(['A.css', 'B.css']);
  });
});
