import { createHash } from 'crypto';
import { CapturedPage, Resource } from './types';

/**
 * Output of `assembleHtml`: the rewritten HTML string plus the CSS
 * sidecar files that must be written to `resources/` alongside it.
 * Caller (`save-snapshot.ts`) merges `cssResources` into the full
 * resource list before writing to disk.
 */
export interface AssembleResult {
  html: string;
  cssResources: Resource[];
}

/**
 * Pure HTML post-processor. Takes a `CapturedPage` and rewrites every
 * `<link rel="stylesheet">` tag whose href matches one of
 * `captured.stylesheets[].href` to point at a sidecar file named
 * `resources/<sha1>.css`. The sidecar bytes are returned so the caller
 * can write them.
 *
 * Stylesheets without an `href` (i.e. inline `<style>` the collector
 * captured) are emitted as fresh sidecar files and referenced via
 * injected `<link>` tags. This keeps all CSS — inline or external —
 * under the uniform `resources/` layout.
 *
 * The `<!DOCTYPE html>` prefix is preserved if present on the captured
 * HTML and added otherwise.
 *
 * No DOM library — a small regex-based rewriter is enough here because
 * the collector emits clean, deterministic HTML (`document.documentElement.outerHTML`).
 */
export function assembleHtml(captured: CapturedPage): AssembleResult {
  const cssResources: Resource[] = [];
  const hrefToFilename = new Map<string, string>();
  const inlineStylesheets: { filename: string; source: string }[] = [];

  for (const sheet of captured.stylesheets) {
    const sha1 = createHash('sha1').update(sheet.source).digest('hex').slice(0, 16);
    const filename = `${sha1}.css`;
    const resource: Resource = {
      filename,
      bytes: Buffer.from(sheet.source, 'utf-8'),
    };
    // De-dup by filename — identical CSS from two sources shares one sidecar.
    if (!cssResources.some((r) => r.filename === filename)) {
      cssResources.push(resource);
    }
    if (sheet.href) {
      hrefToFilename.set(sheet.href, filename);
    } else {
      inlineStylesheets.push({ filename, source: sheet.source });
    }
  }

  let html = rewriteLinkTags(captured.html, hrefToFilename);
  html = injectInlineReplacements(html, inlineStylesheets);
  if (!/^\s*<!DOCTYPE/i.test(html)) {
    html = '<!DOCTYPE html>\n' + html;
  }
  return { html, cssResources };
}

/**
 * Replace `<link rel="stylesheet" href="X">` with `<link rel="stylesheet"
 * href="resources/<sha1>.css">` when `X` matches a known href. Unknown
 * stylesheets (cross-origin, CDN, ...) are left alone — they were
 * already untouched by the collector and staying-as-is is the least
 * surprising behavior.
 */
function rewriteLinkTags(html: string, hrefToFilename: Map<string, string>): string {
  if (hrefToFilename.size === 0) return html;
  // Match <link ... rel="stylesheet" ... href="...">  and its attribute-order variants.
  const linkPattern = /<link\b[^>]*\brel=["']stylesheet["'][^>]*>/gi;
  return html.replace(linkPattern, (tag) => {
    const hrefMatch = /\bhref=["']([^"']+)["']/.exec(tag);
    if (!hrefMatch) return tag;
    const originalHref = hrefMatch[1];
    const filename = hrefToFilename.get(originalHref);
    if (!filename) return tag;
    const newHref = `resources/${filename}`;
    return tag.replace(hrefMatch[0], `href="${newHref}"`);
  });
}

/**
 * For every stylesheet the collector reported WITHOUT an `href` (inline
 * `<style>` blocks), drop the original inline `<style>` from the HTML
 * and inject a `<link rel="stylesheet" href="resources/<sha1>.css">`
 * into `<head>` (or before `</html>` as a fallback).
 *
 * We match inline styles by textContent — exact-match the CSS source
 * with both leading/trailing whitespace trimmed.
 */
function injectInlineReplacements(
  html: string,
  inline: { filename: string; source: string }[],
): string {
  if (inline.length === 0) return html;
  let out = html;
  for (const { source } of inline) {
    // Remove one matching <style>...</style> block (greedy within a single tag).
    const stylePattern = /<style\b[^>]*>([\s\S]*?)<\/style>/i;
    out = out.replace(stylePattern, (match, body: string) => {
      return body.trim() === source.trim() ? '' : match;
    });
  }
  const linkTags = inline
    .map((i) => `<link rel="stylesheet" href="resources/${i.filename}">`)
    .join('\n');
  if (/<head\b[^>]*>/i.test(out)) {
    out = out.replace(/<head\b[^>]*>/i, (m) => `${m}\n${linkTags}`);
  } else {
    out = out.replace(/<\/html>/i, `${linkTags}\n</html>`);
  }
  return out;
}
