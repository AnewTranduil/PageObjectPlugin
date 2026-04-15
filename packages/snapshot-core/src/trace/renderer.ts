/**
 * Framework-agnostic snapshot renderer. Ported from Playwright's
 * `playwright-core/lib/utils/isomorphic/trace/snapshotRenderer.js:63-170`
 * (`SnapshotRenderer.render()` and `resourceByUrl()`), dropped down to a
 * single pure function over the `TraceBackend` shape.
 *
 * Two intentional deviations from Playwright's copy:
 *
 *  1. **No `pw-http://` custom-protocol rewrite.** Playwright rewrites
 *     live URLs to `https://pw-http--example.com/...` so its trace-viewer
 *     HTTP server can intercept them. We render bundles that are opened
 *     outside a trace viewer, so we skip the rewrite entirely and let
 *     `inlineResources` consume the original URLs instead.
 *
 *  2. **No LRU cache.** Playwright renders the same snapshot repeatedly
 *     as users scrub; we render once per marker and write to disk.
 */

import {
  FrameSnapshot,
  NodeSnapshot,
  ResourceEntry,
  TraceBackend,
} from './types';
import { buildRuntimeScript } from './runtime-script';

const AUTO_CLOSING = new Set([
  'AREA',
  'BASE',
  'BR',
  'COL',
  'COMMAND',
  'EMBED',
  'HR',
  'IMG',
  'INPUT',
  'KEYGEN',
  'LINK',
  'MENUITEM',
  'META',
  'PARAM',
  'SOURCE',
  'TRACK',
  'WBR',
]);

const ESCAPE_MAP: Record<string, string> = {
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;',
};

function escapeHTML(s: string): string {
  return s.replace(/[&<]/gu, (c) => ESCAPE_MAP[c]);
}

function escapeHTMLAttribute(s: string): string {
  return s.replace(/[&<>"']/gu, (c) => ESCAPE_MAP[c]);
}

const K_CURRENT_SRC = '__playwright_current_src__';
const K_LEGACY_BLOB_PREFIX = 'http://playwright.bloburl/#';

function stripLegacyBlobPrefix(href: string): string {
  return href.startsWith(K_LEGACY_BLOB_PREFIX) ? href.substring(K_LEGACY_BLOB_PREFIX.length) : href;
}

const URL_IN_CSS_REGEX = /url\(\s*['"]?([^'")]+)['"]?\s*\)/gi;
const URL_QUOTED1_REGEX = /url\(\s*'([^']*)'\s*\)/gi;
const URL_QUOTED2_REGEX = /url\(\s*"([^"]*)"\s*\)/gi;

/**
 * Escape literal `</` sequences inside CSS `url('...')` values so that a
 * user-supplied URL can't accidentally close the surrounding `<style>`
 * element. Matches `snapshotRenderer.js:478-485`.
 */
function escapeURLsInStyleSheet(text: string): string {
  const replacer = (match: string, url: string): string => {
    if (url.includes('</')) return match.replace(url, encodeURI(url));
    return match;
  };
  return text.replace(URL_QUOTED1_REGEX, replacer).replace(URL_QUOTED2_REGEX, replacer);
}

function isSubtreeReference(n: NodeSnapshot): n is [[number, number]] {
  return Array.isArray(n) && Array.isArray(n[0]);
}

function isElement(n: NodeSnapshot): n is [string, Record<string, string>, ...NodeSnapshot[]] {
  return Array.isArray(n) && typeof n[0] === 'string';
}

/**
 * Flatten a FrameSnapshot.html tree into the `_nodes` array that
 * `SnapshotRenderer.render()` uses to resolve subtree references (`[[d, i]]`).
 * Ported from `snapshotRenderer.js:173-190`.
 */
function snapshotNodes(snapshot: FrameSnapshot): NodeSnapshot[] {
  const cached = (snapshot as any)._nodes as NodeSnapshot[] | undefined;
  if (cached) return cached;
  const nodes: NodeSnapshot[] = [];
  const visit = (n: NodeSnapshot): void => {
    if (typeof n === 'string') {
      nodes.push(n);
    } else if (isElement(n)) {
      const [, , ...children] = n;
      for (const child of children) visit(child);
      nodes.push(n);
    }
  };
  visit(snapshot.html);
  (snapshot as any)._nodes = nodes;
  return nodes;
}

export interface RenderedSnapshot {
  /** Full HTML document including doctype, bootstrap `<script>`, and body. */
  html: string;
  /** The snapshot whose DOM was rendered (for downstream `resourceByUrl` calls). */
  snapshot: FrameSnapshot;
  /**
   * Bound `resourceByUrl(url, method)` — resolves a URL to a
   * `ResourceEntry` using the same rules as
   * `snapshotRenderer.js:131-170`. Used by the inliner.
   */
  resourceByUrl(url: string, method: string): ResourceEntry | undefined;
  /** Viewport for this snapshot (needed by the runtime script and manifest). */
  viewport: { width: number; height: number };
}

/**
 * Locate a snapshot by name within a frame/page's snapshot list and
 * render it. Returns the full HTML string plus a closure that resolves
 * resource URLs for the inliner.
 *
 * The caller typically picks `snapshotName` from a trace marker (e.g.
 * `after@<callId>`) — which is what `loadTraceMarkers` in the Playwright
 * package already produces.
 */
export function renderSnapshot(
  backend: TraceBackend,
  pageOrFrameId: string,
  snapshotName: string,
): RenderedSnapshot {
  const snapshots = backend.getFrameSnapshots(pageOrFrameId);
  const index = snapshots.findIndex((s) => s.snapshotName === snapshotName);
  if (index < 0) {
    throw new Error(
      `No snapshot named ${JSON.stringify(snapshotName)} found for ${JSON.stringify(pageOrFrameId)}`,
    );
  }
  const snapshot = snapshots[index];
  const resources = backend.getResources();

  const chunks: string[] = [];
  const visit = (
    n: NodeSnapshot,
    snapshotIndex: number,
    parentTag: string | undefined,
    parentAttrs: Array<[string, string]> | undefined,
  ): void => {
    if (typeof n === 'string') {
      if (parentTag === 'STYLE' || parentTag === 'style')
        chunks.push(escapeURLsInStyleSheet(n));
      else chunks.push(escapeHTML(n));
      return;
    }
    if (isSubtreeReference(n)) {
      const referenceIndex = snapshotIndex - n[0][0];
      if (referenceIndex >= 0 && referenceIndex <= snapshotIndex) {
        const refNodes = snapshotNodes(snapshots[referenceIndex]);
        const nodeIndex = n[0][1];
        if (nodeIndex >= 0 && nodeIndex < refNodes.length) {
          visit(refNodes[nodeIndex], referenceIndex, parentTag, parentAttrs);
        }
      }
      return;
    }
    if (!isElement(n)) return;

    const [name, nodeAttrs, ...children] = n;
    const nodeName = name === 'NOSCRIPT' ? 'X-NOSCRIPT' : name;
    const attrs = Object.entries(nodeAttrs || {});
    chunks.push('<', nodeName);

    const isFrame = nodeName === 'IFRAME' || nodeName === 'FRAME';
    const isAnchor = nodeName === 'A';
    const isImg = nodeName === 'IMG';
    const isImgWithCurrentSrc = isImg && attrs.some((a) => a[0] === K_CURRENT_SRC);
    const isSourceInsidePictureWithCurrentSrc =
      nodeName === 'SOURCE' &&
      parentTag === 'PICTURE' &&
      !!parentAttrs?.some((a) => a[0] === K_CURRENT_SRC);

    for (const [attr, value] of attrs) {
      let attrName = attr;
      if (isFrame && attr.toLowerCase() === 'src') attrName = '__playwright_src__';
      if (isImg && attr === K_CURRENT_SRC) attrName = 'src';
      if (
        ['src', 'srcset'].includes(attr.toLowerCase()) &&
        (isImgWithCurrentSrc || isSourceInsidePictureWithCurrentSrc)
      ) {
        attrName = '_' + attrName;
      }
      let attrValue = value;
      if (
        !isAnchor &&
        (attr.toLowerCase() === 'href' || attr.toLowerCase() === 'src' || attr === K_CURRENT_SRC)
      ) {
        attrValue = stripLegacyBlobPrefix(value);
      }
      chunks.push(' ', attrName, '="', escapeHTMLAttribute(attrValue), '"');
    }
    chunks.push('>');
    for (const child of children) visit(child, snapshotIndex, nodeName, attrs);
    if (!AUTO_CLOSING.has(nodeName)) chunks.push('</', nodeName, '>');
  };
  visit(snapshot.html, index, undefined, undefined);

  const doctypePrefix = snapshot.doctype ? `<!DOCTYPE ${snapshot.doctype}>` : '';
  const bootstrap =
    '<style>*,*::before,*::after { visibility: hidden }</style>' +
    `<script>${buildRuntimeScript(snapshot.viewport, [snapshot.callId, `after@${snapshot.callId}`])}</script>`;
  const html = doctypePrefix + bootstrap + chunks.join('');

  const resourceByUrl = (url: string, method: string): ResourceEntry | undefined =>
    resourceByUrlImpl(resources, snapshots, index, url, method);

  return {
    html,
    snapshot,
    viewport: snapshot.viewport,
    resourceByUrl,
  };
}

/**
 * URL → resource-entry lookup. Mirrors `snapshotRenderer.js:131-170`:
 *  1. walk `resources` in time order, stop at the snapshot's timestamp
 *  2. prefer a same-frame response over a cross-frame one
 *  3. skip 304s (cached, no body)
 *  4. for GETs, honor `resourceOverrides` (including `ref`-chased
 *     overrides that point at earlier snapshots in the same frame)
 */
function resourceByUrlImpl(
  resources: ResourceEntry[],
  snapshots: FrameSnapshot[],
  index: number,
  url: string,
  method: string,
): ResourceEntry | undefined {
  const snapshot = snapshots[index];
  let sameFrame: ResourceEntry | undefined;
  let otherFrame: ResourceEntry | undefined;
  for (const resource of resources) {
    if (
      typeof resource.monotonicTime === 'number' &&
      resource.monotonicTime >= snapshot.timestamp
    ) {
      break;
    }
    if (resource.response.status === 304) continue;
    if (resource.request.url === url && resource.request.method === method) {
      if (resource.frameref === snapshot.frameId) sameFrame = resource;
      else otherFrame = resource;
    }
  }
  let result = sameFrame ?? otherFrame;
  if (result && method.toUpperCase() === 'GET') {
    let override = snapshot.resourceOverrides.find((o) => o.url === url);
    if (override?.ref !== undefined) {
      const refIndex = index - override.ref;
      if (refIndex >= 0 && refIndex < snapshots.length) {
        override = snapshots[refIndex].resourceOverrides.find((o) => o.url === url);
      }
    }
    if (override?.sha1) {
      result = {
        ...result,
        response: {
          ...result.response,
          content: {
            ...result.response.content,
            sha1: override.sha1,
          },
        },
      };
    }
  }
  return result;
}

// Re-exported only so callers don't need a separate import for the regex.
export { URL_IN_CSS_REGEX };
