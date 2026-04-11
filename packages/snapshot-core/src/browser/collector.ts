/**
 * Browser-side collector. Runs inside the page (via `page.evaluate` in
 * Playwright, `driver.executeScript` in Selenium, etc.). This file MUST
 * compile to a self-contained function — no Node imports, no references
 * outside its own scope. Adapters stringify `collectPage.toString()` and
 * feed it to their driver.
 */

import { CollectorOptions } from '../types';

/**
 * Payload the collector returns. A subset of `CapturedPage` — the
 * adapter fills in `url`, `viewport`, `userAgent`, and any `resources`
 * (screenshot) before handing the full `CapturedPage` to core.
 */
export interface CollectedPayload {
  html: string;
  stylesheets: Array<{ href?: string; source: string }>;
}

/**
 * The function the browser actually runs. Argument `options` is forwarded
 * verbatim by the adapter. This function is also available as a
 * stringified source via {@link collectorSource} so other language
 * adapters can ship the identical logic.
 */
export async function collectPage(options: CollectorOptions = {}): Promise<CollectedPayload> {
  const stylesheets: Array<{ href?: string; source: string }> = [];

  // 1. Resolve every <link rel="stylesheet">: prefer same-origin cssRules
  //    (no network), fall back to fetch() for cross-origin.
  //
  //    The stored `href` is deliberately the RAW attribute value as it
  //    appears in the DOM (e.g. "styles/app.css"), not the resolved URL
  //    (e.g. "http://localhost/styles/app.css"). `document.documentElement
  //    .outerHTML` preserves raw attributes, and the Node-side assembler
  //    matches `<link href="X">` tags in that serialized HTML by this
  //    exact key. Using `link.href` (the resolved property) would break
  //    the match and leave sidecars orphaned.
  const links = Array.from(document.querySelectorAll('link[rel="stylesheet"]')) as HTMLLinkElement[];
  for (const link of links) {
    try {
      let cssText = '';
      const sheet = link.sheet as CSSStyleSheet | null;
      if (sheet) {
        try {
          cssText = Array.from(sheet.cssRules).map((r) => r.cssText).join('\n');
        } catch {
          // Cross-origin — cssRules is blocked. Fetch instead.
          if (link.href) {
            const resp = await fetch(link.href);
            cssText = await resp.text();
          }
        }
      } else if (link.href) {
        const resp = await fetch(link.href);
        cssText = await resp.text();
      }
      if (cssText) {
        const rawHref = link.getAttribute('href') ?? link.href;
        stylesheets.push({ href: rawHref, source: cssText });
      }
    } catch {
      // Skip unresolvable stylesheets; the assembler leaves them in place.
    }
  }

  // 2. Capture inline <style> blocks as stylesheets without an href.
  const styleEls = Array.from(document.querySelectorAll('style')) as HTMLStyleElement[];
  for (const el of styleEls) {
    const source = (el.textContent ?? '').trim();
    if (source.length > 0) {
      stylesheets.push({ source });
    }
  }

  // 3. Apply excludeSelectors: remove matched nodes entirely from the clone.
  //    We work on a clone so the live page isn't mutated.
  const docClone = document.documentElement.cloneNode(true) as HTMLElement;
  const excludeSelectors = options.excludeSelectors ?? [];
  for (const sel of excludeSelectors) {
    try {
      docClone.querySelectorAll(sel).forEach((n) => n.remove());
    } catch {
      // Invalid selector — ignore.
    }
  }

  // 4. extraSelectors / extraAttributes are passive for now — the collector
  //    honors them as a data-tag hook so future plugin features (Task 15.5,
  //    17, 20) can rely on them. Current implementation: tag every matching
  //    element with a `data-pagemirror-extra` attribute so downstream tools
  //    can identify "pinned" selectors without re-running selectors on the
  //    Kotlin side.
  const extraSelectors = options.extraSelectors ?? [];
  for (const sel of extraSelectors) {
    try {
      docClone.querySelectorAll(sel).forEach((n) => {
        (n as HTMLElement).setAttribute('data-pagemirror-extra', sel);
      });
    } catch {
      // Ignore invalid selectors.
    }
  }

  // 5. extraAttributes: preserve additional attributes that might otherwise
  //    be stripped downstream. Currently a no-op passthrough because the
  //    collector doesn't strip any attributes — kept for forward compat.
  const _extraAttributes = options.extraAttributes ?? [];
  void _extraAttributes;

  const html = '<!DOCTYPE html>\n' + docClone.outerHTML;

  return { html, stylesheets };
}

/**
 * Stringified source of {@link collectPage} suitable for passing to
 * `page.evaluate(new Function('opts', 'return (' + collectorSource + ')(opts)'), opts)`.
 * Adapters that need a different shape can still import {@link collectPage}
 * directly and serialize it themselves.
 */
export const collectorSource: string = collectPage.toString();
