import { describe, it, expect } from 'vitest';
import { renderSnapshot } from '../../src/trace/renderer';
import type {
  FrameSnapshot,
  ResourceEntry,
  TraceBackend,
  NodeSnapshot,
} from '../../src/trace/types';

function makeBackend(
  snapshots: FrameSnapshot[],
  resources: ResourceEntry[] = [],
  resourceBytes: Record<string, Uint8Array> = {},
): TraceBackend {
  return {
    getFrameSnapshots: () => snapshots,
    getResources: () => resources,
    readResource: async (sha1) => resourceBytes[sha1],
  };
}

function minimalSnapshot(overrides: Partial<FrameSnapshot> = {}): FrameSnapshot {
  return {
    callId: 'call-1',
    snapshotName: 'after@call-1',
    pageId: 'page@p1',
    frameId: 'frame@f1',
    timestamp: 1,
    viewport: { width: 1280, height: 720 },
    url: 'https://example.com/',
    doctype: 'html',
    html: ['HTML', {}, ['HEAD', {}], ['BODY', {}]] as NodeSnapshot,
    resourceOverrides: [],
    ...overrides,
  };
}

describe('renderSnapshot', () => {
  it('produces a full document with doctype, bootstrap style, and runtime script', () => {
    const snap = minimalSnapshot();
    const rendered = renderSnapshot(makeBackend([snap]), snap.pageId, snap.snapshotName);

    expect(rendered.html.startsWith('<!DOCTYPE html>')).toBe(true);
    expect(rendered.html).toContain('visibility: hidden');
    expect(rendered.html).toContain('<script>');
    expect(rendered.html).toContain('<HTML>');
    expect(rendered.html).toContain('</HTML>');
  });

  it('throws when snapshotName is not found', () => {
    const backend = makeBackend([minimalSnapshot()]);
    expect(() => renderSnapshot(backend, 'page@p1', 'no-such-snap')).toThrow(/No snapshot named/);
  });

  it('renders element attributes, escaping quotes and ampersands', () => {
    const snap = minimalSnapshot({
      html: ['HTML', {}, ['BODY', {}, ['DIV', { title: 'a "b" & <c>' }]]] as NodeSnapshot,
    });
    const rendered = renderSnapshot(makeBackend([snap]), snap.pageId, snap.snapshotName);
    expect(rendered.html).toContain('title="a &quot;b&quot; &amp; &lt;c&gt;"');
  });

  it('emits self-closing tags without a closing tag', () => {
    const snap = minimalSnapshot({
      html: ['HTML', {}, ['BODY', {}, ['IMG', { src: 'a.png' }]]] as NodeSnapshot,
    });
    const rendered = renderSnapshot(makeBackend([snap]), snap.pageId, snap.snapshotName);
    expect(rendered.html).toContain('<IMG src="a.png">');
    expect(rendered.html).not.toContain('</IMG>');
  });

  it('renames iframe src to __playwright_src__', () => {
    const snap = minimalSnapshot({
      html: ['HTML', {}, ['BODY', {}, ['IFRAME', { src: 'https://child.example' }]]] as NodeSnapshot,
    });
    const rendered = renderSnapshot(makeBackend([snap]), snap.pageId, snap.snapshotName);
    expect(rendered.html).toContain('__playwright_src__="https://child.example"');
  });

  it('rewrites __playwright_current_src__ to src on <img>', () => {
    const snap = minimalSnapshot({
      html: ['HTML', {}, ['BODY', {}, [
        'IMG',
        { src: 'orig.png', __playwright_current_src__: 'current.png' },
      ]]] as NodeSnapshot,
    });
    const rendered = renderSnapshot(makeBackend([snap]), snap.pageId, snap.snapshotName);
    expect(rendered.html).toContain('_src="orig.png"');
    expect(rendered.html).toContain('src="current.png"');
  });

  it('renames NOSCRIPT to X-NOSCRIPT', () => {
    const snap = minimalSnapshot({
      html: ['HTML', {}, ['BODY', {}, ['NOSCRIPT', {}, 'nope']]] as NodeSnapshot,
    });
    const rendered = renderSnapshot(makeBackend([snap]), snap.pageId, snap.snapshotName);
    expect(rendered.html).toContain('<X-NOSCRIPT>');
    expect(rendered.html).toContain('</X-NOSCRIPT>');
  });

  it('escapes </ inside <style> url(...) to prevent tag-closure', () => {
    const css = "a { background: url('/x.png?q=</style>'); }";
    const snap = minimalSnapshot({
      html: ['HTML', {}, ['BODY', {}, ['STYLE', {}, css]]] as NodeSnapshot,
    });
    const rendered = renderSnapshot(makeBackend([snap]), snap.pageId, snap.snapshotName);
    // The literal </style> sequence in the url must be URL-encoded.
    expect(rendered.html).not.toContain("url('/x.png?q=</style>')");
    expect(rendered.html).toContain('%3C/style%3E');
  });

  it('resolves subtree references from earlier snapshots', () => {
    const first = minimalSnapshot({
      callId: 'c1',
      snapshotName: 'after@c1',
      timestamp: 1,
      html: ['HTML', {}, ['BODY', {}, ['P', { id: 'carried' }, 'hello']]] as NodeSnapshot,
    });
    // snapshotNodes order is post-order: text, P, BODY, HTML → indices 0..3
    // Subtree reference: [[delta, nodeIndex]] — delta=1 means "one snapshot back".
    const second = minimalSnapshot({
      callId: 'c2',
      snapshotName: 'after@c2',
      timestamp: 2,
      html: ['HTML', {}, ['BODY', {}, [[1, 1]] as any]] as NodeSnapshot,
    });
    const rendered = renderSnapshot(makeBackend([first, second]), second.pageId, second.snapshotName);
    expect(rendered.html).toContain('id="carried"');
    expect(rendered.html).toContain('hello');
  });

  it('resourceByUrl finds a same-frame entry before the snapshot timestamp', () => {
    const snap = minimalSnapshot({ timestamp: 10 });
    const entry: ResourceEntry = {
      request: { url: 'https://example.com/a.css', method: 'GET' },
      response: { status: 200, content: { sha1: 'abc', mimeType: 'text/css' } },
      monotonicTime: 5,
      frameref: snap.frameId,
    };
    const rendered = renderSnapshot(makeBackend([snap], [entry]), snap.pageId, snap.snapshotName);
    const found = rendered.resourceByUrl('https://example.com/a.css', 'GET');
    expect(found?.response.content.sha1).toBe('abc');
  });

  it('resourceByUrl skips resources after the snapshot timestamp', () => {
    const snap = minimalSnapshot({ timestamp: 10 });
    const late: ResourceEntry = {
      request: { url: 'https://example.com/a.css', method: 'GET' },
      response: { status: 200, content: { sha1: 'late' } },
      monotonicTime: 20,
    };
    const rendered = renderSnapshot(makeBackend([snap], [late]), snap.pageId, snap.snapshotName);
    expect(rendered.resourceByUrl('https://example.com/a.css', 'GET')).toBeUndefined();
  });

  it('resourceByUrl honors resourceOverrides for GETs', () => {
    const base: ResourceEntry = {
      request: { url: 'https://example.com/a.css', method: 'GET' },
      response: { status: 200, content: { sha1: 'orig' } },
      monotonicTime: 5,
    };
    const snap = minimalSnapshot({
      timestamp: 10,
      resourceOverrides: [{ url: 'https://example.com/a.css', sha1: 'override' }],
    });
    const rendered = renderSnapshot(makeBackend([snap], [base]), snap.pageId, snap.snapshotName);
    expect(rendered.resourceByUrl('https://example.com/a.css', 'GET')?.response.content.sha1).toBe('override');
  });
});
