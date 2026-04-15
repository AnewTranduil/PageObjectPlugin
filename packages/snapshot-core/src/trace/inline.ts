/**
 * Resource inliner for rendered trace snapshots. Walks the HTML produced
 * by `renderSnapshot`, finds every externally-referenced asset (CSS,
 * images, fonts, media, SVG sprites), resolves each URL to a sha1 via
 * the snapshot's `resourceByUrl` closure, fetches bytes via
 * `backend.readResource`, and rewrites the reference to a relative
 * `resources/<sha1>.<ext>` path. Nested CSS references (e.g. `@import`,
 * `url(...)` inside a `@font-face`) are discovered in a second pass and
 * fed back into the worklist.
 *
 * The intent is that the produced HTML + `resources/` directory is
 * completely self-contained — opening `index.html` in any browser
 * renders the page with full visual fidelity, no network required.
 */

import { parse, serialize, defaultTreeAdapter } from 'parse5';
import type {
  Document,
  Element,
  ChildNode,
  ParentNode,
  TextNode,
} from 'parse5/dist/tree-adapters/default';
import type { Attribute } from 'parse5/dist/common/token';
import postcss, { AtRule, Declaration, Root } from 'postcss';
import { ResourceEntry, TraceBackend } from './types';
import { extensionFromContentType } from './content-type';

export interface InlinedResource {
  /** Opaque content id from the backend (may already carry an extension). */
  sha1: string;
  /** Final filename under `resources/`, e.g. `a1b2c3d4.css`. */
  filename: string;
  bytes: Uint8Array;
}

export interface InlineResult {
  /** Rewritten HTML with `<base>` removed and every external URL → `resources/<sha1>.<ext>`. */
  html: string;
  /** Sidecar files to write under `<snapshotDir>/resources/`. */
  resources: InlinedResource[];
}

/**
 * Shape-match `renderSnapshot`'s return value so callers can hand in the
 * full `RenderedSnapshot` object. We only need `html` + `resourceByUrl`;
 * the other fields are optional from the inliner's perspective.
 */
export interface RenderedSnapshotInput {
  html: string;
  resourceByUrl(url: string, method: string): ResourceEntry | undefined;
}

type ResourceByUrl = RenderedSnapshotInput['resourceByUrl'];

interface PendingCss {
  /** sha1 already enqueued (bytes are fetched lazily to allow async dedup). */
  sha1: string;
  /** Resolved URL of the stylesheet itself — used as the base for `url()` inside. */
  baseUrl: string;
}

/**
 * Walk the HTML produced by `renderSnapshot`, inline every resolvable
 * external resource, and return the rewritten HTML plus the resource
 * bytes to persist.
 *
 * Unresolvable URLs (no resource in the trace, data URIs, `javascript:`,
 * fragment-only `#id` refs) are left alone so the page still renders
 * best-effort.
 */
export async function inlineResources(
  rendered: RenderedSnapshotInput,
  backend: TraceBackend,
): Promise<InlineResult> {
  const doc = parse(rendered.html) as Document;

  // Pass 1: locate <base href> so relative URLs can be resolved.
  const baseElement = findFirstElement(doc, 'base');
  const baseHref =
    baseElement ? getAttr(baseElement, 'href') : undefined;

  const resources = new Map<string, InlinedResource>(); // sha1 → file
  const cssWorklist: PendingCss[] = [];
  const enqueuedSha1s = new Set<string>();

  /**
   * Try to resolve `rawUrl` against `resourceByUrl` and remember the
   * resulting sha1 for a later bytes-fetch. Returns the new relative
   * path for the caller to rewrite to, or undefined if the URL isn't in
   * the trace.
   */
  const tryResolve = (rawUrl: string, baseUrl: string | undefined): string | undefined => {
    if (!rawUrl) return undefined;
    const trimmed = rawUrl.trim();
    if (!trimmed) return undefined;
    if (isInlineUrl(trimmed)) return undefined;
    // Fragment-only refs (e.g. <use href="#sprite-x">) point inside the
    // current document — leave them alone.
    if (trimmed.startsWith('#')) return undefined;
    const absolute = resolveUrl(trimmed, baseUrl);
    if (!absolute) return undefined;
    // Strip the fragment before resource lookup — HTTP requests never carry
    // one, so the trace stores URLs without it (e.g. SVG sprite refs like
    // `sprite.svg#icon-a` resolve to an entry keyed on `sprite.svg`).
    const hashIdx = absolute.indexOf('#');
    const fragment = hashIdx >= 0 ? absolute.slice(hashIdx) : '';
    const lookupUrl = hashIdx >= 0 ? absolute.slice(0, hashIdx) : absolute;
    const resource = rendered.resourceByUrl(lookupUrl, 'GET');
    if (!resource) return undefined;
    const sha1 = resource.response.content.sha1;
    const ext = extensionFromContentType(resource.response.content.mimeType);
    // Playwright's trace stores resources keyed by `<hash>.<ext>` and exposes
    // that identifier as `content.sha1`. Don't double-append the extension.
    const filename = /\.[A-Za-z0-9]+$/.test(sha1) ? sha1 : `${sha1}.${ext}`;
    if (!enqueuedSha1s.has(sha1)) {
      enqueuedSha1s.add(sha1);
      // CSS needs a second pass; everything else is just bytes.
      const isCss = ext === 'css';
      if (isCss) cssWorklist.push({ sha1, baseUrl: lookupUrl });
      resources.set(sha1, { sha1, filename, bytes: new Uint8Array() }); // placeholder
    }
    return `resources/${filename}${fragment}`;
  };

  // Pass 2: rewrite all URL-bearing attributes in the HTML tree.
  walkElements(doc, (el) => {
    const tag = el.tagName.toLowerCase();

    // Link stylesheets.
    if (tag === 'link') {
      const rel = (getAttr(el, 'rel') ?? '').toLowerCase();
      if (rel.split(/\s+/).includes('stylesheet')) {
        rewriteAttr(el, 'href', baseHref, tryResolve);
      }
      // Other <link> rels (icon, manifest, etc.) — try best-effort
      else if (rel === 'icon' || rel === 'shortcut icon' || rel === 'apple-touch-icon') {
        rewriteAttr(el, 'href', baseHref, tryResolve);
      }
      return;
    }

    // Images.
    if (tag === 'img') {
      rewriteAttr(el, 'src', baseHref, tryResolve);
      rewriteSrcsetAttr(el, 'srcset', baseHref, tryResolve);
      return;
    }
    if (tag === 'source') {
      rewriteAttr(el, 'src', baseHref, tryResolve);
      rewriteSrcsetAttr(el, 'srcset', baseHref, tryResolve);
      return;
    }
    if (tag === 'image') {
      // SVG <image href> / xlink:href.
      rewriteAttr(el, 'href', baseHref, tryResolve);
      rewriteAttr(el, 'xlink:href', baseHref, tryResolve);
      return;
    }

    // Media.
    if (tag === 'video' || tag === 'audio') {
      rewriteAttr(el, 'src', baseHref, tryResolve);
      rewriteAttr(el, 'poster', baseHref, tryResolve);
      return;
    }

    // SVG sprites / external-svg references.
    if (tag === 'use') {
      rewriteAttr(el, 'href', baseHref, tryResolve);
      rewriteAttr(el, 'xlink:href', baseHref, tryResolve);
      return;
    }

    // Inline <style> blocks — rewrite against the page URL.
    if (tag === 'style') {
      const textNode = findFirstText(el);
      if (textNode) {
        textNode.value = rewriteCssText(textNode.value, baseHref, tryResolve);
      }
      return;
    }

    // Inline style="" attributes.
    const styleAttr = getAttr(el, 'style');
    if (styleAttr) {
      const rewritten = rewriteCssDeclarations(styleAttr, baseHref, tryResolve);
      if (rewritten !== styleAttr) setAttr(el, 'style', rewritten);
    }
  });

  // Pass 3: remove <base>. All external URLs are now relative to the
  // bundle directory, so a <base href> pointing at the original origin
  // would break them.
  if (baseElement && baseElement.parentNode) {
    detach(baseElement);
  }

  // Pass 4: drain CSS worklist. Each entry may discover further resources
  // (e.g. `@font-face` → woff2). Fetches are parallel per batch.
  while (cssWorklist.length > 0) {
    const batch = cssWorklist.splice(0, cssWorklist.length);
    const fetched = await Promise.all(
      batch.map(async (entry) => {
        const bytes = await backend.readResource(entry.sha1);
        return { entry, bytes };
      }),
    );
    for (const { entry, bytes } of fetched) {
      if (!bytes) {
        // Missing body — drop the placeholder so we don't write an empty file.
        resources.delete(entry.sha1);
        enqueuedSha1s.delete(entry.sha1);
        continue;
      }
      const original = new TextDecoder('utf-8').decode(bytes);
      const rewritten = rewriteCssText(original, entry.baseUrl, tryResolve);
      const encoded = new TextEncoder().encode(rewritten);
      const existing = resources.get(entry.sha1);
      if (existing) existing.bytes = encoded;
    }
  }

  // Pass 5: fetch non-CSS resources. These don't need reprocessing.
  const nonCss = Array.from(resources.values()).filter((r) => r.bytes.byteLength === 0);
  await Promise.all(
    nonCss.map(async (res) => {
      const bytes = await backend.readResource(res.sha1);
      if (!bytes) {
        resources.delete(res.sha1);
        enqueuedSha1s.delete(res.sha1);
        return;
      }
      res.bytes = bytes;
    }),
  );

  const html = serialize(doc);
  return {
    html,
    resources: Array.from(resources.values()),
  };
}

// ---------------------------------------------------------------------------
// CSS rewriting
// ---------------------------------------------------------------------------

/**
 * Rewrite `url(...)`, `@import`, and `image-set(...)` URLs in a full CSS
 * stylesheet text. Uses postcss so quoted vs. unquoted urls, comments,
 * and nested at-rules are parsed correctly instead of regex-matched.
 */
function rewriteCssText(
  css: string,
  baseUrl: string | undefined,
  tryResolve: (url: string, base: string | undefined) => string | undefined,
): string {
  let root: Root;
  try {
    root = postcss.parse(css);
  } catch {
    // Malformed CSS — fall back to regex rewrite so at least simple refs work.
    return fallbackRewriteCss(css, baseUrl, tryResolve);
  }

  root.walkAtRules((rule: AtRule) => {
    if (rule.name === 'import') {
      rule.params = rewriteCssParams(rule.params, baseUrl, tryResolve);
    }
  });
  root.walkDecls((decl: Declaration) => {
    if (decl.value.includes('url(') || decl.value.includes('image-set(')) {
      decl.value = rewriteCssParams(decl.value, baseUrl, tryResolve);
    }
  });

  return root.toString();
}

/**
 * Rewrite URLs inside a single CSS value string (a postcss `params` or
 * `Declaration.value`). Handles `url(...)` tokens only — `image-set(...)`
 * inner urls are matched via the same regex since they nest `url(...)`.
 */
function rewriteCssParams(
  value: string,
  baseUrl: string | undefined,
  tryResolve: (url: string, base: string | undefined) => string | undefined,
): string {
  return value.replace(/url\(\s*(['"]?)([^'")]+)\1\s*\)/g, (match, quote, url) => {
    const rewritten = tryResolve(url, baseUrl);
    if (!rewritten) return match;
    return `url(${quote}${rewritten}${quote})`;
  });
}

/**
 * Rewrite URLs in an inline `style="..."` attribute. Same shape as
 * `rewriteCssParams` — attributes don't contain at-rules, so this is a
 * thin alias for clarity.
 */
function rewriteCssDeclarations(
  value: string,
  baseUrl: string | undefined,
  tryResolve: (url: string, base: string | undefined) => string | undefined,
): string {
  return rewriteCssParams(value, baseUrl, tryResolve);
}

/**
 * Last-resort rewriter for CSS that postcss refused to parse. Strictly
 * weaker than the postcss path — only handles `url(...)` tokens, skips
 * `@import` strings without a `url(...)` wrapper.
 */
function fallbackRewriteCss(
  css: string,
  baseUrl: string | undefined,
  tryResolve: (url: string, base: string | undefined) => string | undefined,
): string {
  return rewriteCssParams(css, baseUrl, tryResolve);
}

// ---------------------------------------------------------------------------
// HTML helpers (parse5 tree operations)
// ---------------------------------------------------------------------------

function rewriteAttr(
  el: Element,
  attrName: string,
  baseHref: string | undefined,
  tryResolve: (url: string, base: string | undefined) => string | undefined,
): void {
  const current = getAttr(el, attrName);
  if (current === undefined) return;
  const rewritten = tryResolve(current, baseHref);
  if (rewritten) setAttr(el, attrName, rewritten);
}

/**
 * Rewrite a `srcset="url1 1x, url2 2x"` attribute — each comma-separated
 * candidate is resolved independently, preserving its size descriptor.
 */
function rewriteSrcsetAttr(
  el: Element,
  attrName: string,
  baseHref: string | undefined,
  tryResolve: (url: string, base: string | undefined) => string | undefined,
): void {
  const current = getAttr(el, attrName);
  if (!current) return;
  const rewritten = current
    .split(',')
    .map((candidate) => {
      const trimmed = candidate.trim();
      if (!trimmed) return candidate;
      const parts = trimmed.split(/\s+/);
      const url = parts[0];
      const descriptor = parts.slice(1).join(' ');
      const newUrl = tryResolve(url, baseHref);
      if (!newUrl) return candidate;
      return descriptor ? `${newUrl} ${descriptor}` : newUrl;
    })
    .join(', ');
  setAttr(el, attrName, rewritten);
}

function getAttr(el: Element, name: string): string | undefined {
  for (const a of el.attrs) if (attrFullName(a) === name) return a.value;
  return undefined;
}

function setAttr(el: Element, name: string, value: string): void {
  for (const a of el.attrs) {
    if (attrFullName(a) === name) {
      a.value = value;
      return;
    }
  }
  el.attrs.push({ name, value });
}

function attrFullName(a: Attribute): string {
  return a.prefix ? `${a.prefix}:${a.name}` : a.name;
}

function detach(node: ChildNode): void {
  const parent = node.parentNode as ParentNode | null;
  if (!parent) return;
  const idx = parent.childNodes.indexOf(node);
  if (idx >= 0) parent.childNodes.splice(idx, 1);
  node.parentNode = null;
}

function findFirstElement(root: ParentNode, tagName: string): Element | undefined {
  const lower = tagName.toLowerCase();
  const stack: Array<ParentNode> = [root];
  while (stack.length > 0) {
    const node = stack.pop()!;
    for (const child of node.childNodes) {
      if (defaultTreeAdapter.isElementNode(child)) {
        if (child.tagName.toLowerCase() === lower) return child;
        stack.push(child);
      }
    }
  }
  return undefined;
}

function findFirstText(el: Element): TextNode | undefined {
  for (const child of el.childNodes) {
    if (defaultTreeAdapter.isTextNode(child)) return child;
  }
  return undefined;
}

function walkElements(root: ParentNode, visit: (el: Element) => void): void {
  const stack: Array<ParentNode> = [root];
  while (stack.length > 0) {
    const node = stack.pop()!;
    for (const child of node.childNodes) {
      if (defaultTreeAdapter.isElementNode(child)) {
        visit(child);
        stack.push(child);
      }
    }
  }
}

// ---------------------------------------------------------------------------
// URL helpers
// ---------------------------------------------------------------------------

function isInlineUrl(url: string): boolean {
  const lower = url.toLowerCase();
  return (
    lower.startsWith('data:') ||
    lower.startsWith('blob:') ||
    lower.startsWith('javascript:') ||
    lower.startsWith('vbscript:') ||
    lower.startsWith('mailto:') ||
    lower.startsWith('tel:') ||
    lower.startsWith('about:')
  );
}

function resolveUrl(ref: string, base: string | undefined): string | undefined {
  try {
    if (base) return new URL(ref, base).toString();
    return new URL(ref).toString();
  } catch {
    return undefined;
  }
}
