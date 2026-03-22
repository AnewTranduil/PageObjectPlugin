import { Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

export async function saveState(
  page: Page,
  handle: string,
  snapshotDir: string,
  options?: { fullPage?: boolean }
): Promise<void> {
  const outDir = path.join(snapshotDir, handle);
  fs.mkdirSync(outDir, { recursive: true });

  await page.screenshot({
    path: path.join(outDir, 'screenshot.png'),
    type: 'png',
    fullPage: options?.fullPage ?? false,
  });

  const html = await generateInlinedHtml(page);
  fs.writeFileSync(path.join(outDir, 'index.html'), html, 'utf-8');

  const layout = await generateLayout(page);
  fs.writeFileSync(path.join(outDir, 'layout.json'), JSON.stringify(layout, null, 2), 'utf-8');

  const manifest = await generateManifest(page);
  fs.writeFileSync(path.join(outDir, 'manifest.json'), JSON.stringify(manifest, null, 2), 'utf-8');
}

async function generateInlinedHtml(page: Page): Promise<string> {
  return await page.evaluate(async () => {
    const links = Array.from(document.querySelectorAll('link[rel="stylesheet"]'));
    for (const link of links) {
      try {
        let cssText = '';
        const sheet = (link as HTMLLinkElement).sheet;
        if (sheet) {
          try {
            cssText = Array.from(sheet.cssRules).map(r => r.cssText).join('\n');
          } catch {
            const resp = await fetch((link as HTMLLinkElement).href);
            cssText = await resp.text();
          }
        } else {
          const href = (link as HTMLLinkElement).href;
          if (href) {
            const resp = await fetch(href);
            cssText = await resp.text();
          }
        }
        if (cssText) {
          const style = document.createElement('style');
          style.textContent = cssText;
          link.parentNode?.insertBefore(style, link);
        }
        link.remove();
      } catch {
        // Skip unresolvable stylesheets
      }
    }
    return '<!DOCTYPE html>\n' + document.documentElement.outerHTML;
  });
}

async function generateLayout(page: Page): Promise<object> {
  const viewportSize = page.viewportSize() ?? { width: 1280, height: 720 };

  const elements = await page.evaluate(() => {
    const selector = [
      'button', 'input', 'select', 'textarea', 'a',
      '[role="button"]', '[role="link"]', '[tabindex]',
      '[id]', '[data-testid]',
    ].join(', ');

    const seen = new Set<Element>();
    const results: Array<{
      selector: string;
      role: string | null;
      text: string;
      tag: string;
      bounds: { x: number; y: number; w: number; h: number };
      interactive: boolean;
      attributes: Record<string, string>;
    }> = [];

    const interactiveTags = new Set(['button', 'input', 'select', 'textarea', 'a']);
    const interactiveRoles = new Set(['button', 'link']);

    function inferRole(el: Element): string | null {
      const explicit = el.getAttribute('role');
      if (explicit) return explicit;
      const tag = el.tagName.toLowerCase();
      if (tag === 'button') return 'button';
      if (tag === 'a') return 'link';
      if (tag === 'select') return 'combobox';
      if (tag === 'textarea') return 'textbox';
      if (tag === 'input') {
        const type = (el as HTMLInputElement).type?.toLowerCase() || 'text';
        if (type === 'checkbox') return 'checkbox';
        if (type === 'radio') return 'radio';
        if (type === 'submit' || type === 'button') return 'button';
        return 'textbox';
      }
      return null;
    }

    function bestSelector(el: Element, depth = 0): string {
      const testId = el.getAttribute('data-testid');
      if (testId) return `[data-testid="${testId}"]`;

      if (el.id) {
        const escaped = CSS.escape(el.id);
        if (document.querySelectorAll('#' + escaped).length === 1) {
          return '#' + escaped;
        }
      }

      const tag = el.tagName.toLowerCase();
      const name = el.getAttribute('name');
      if (name) {
        const sel = `${tag}[name="${name}"]`;
        if (document.querySelectorAll(sel).length === 1) return sel;
      }

      if (tag === 'input') {
        const type = (el as HTMLInputElement).type;
        if (type) {
          const sel = `input[type="${type}"]`;
          if (document.querySelectorAll(sel).length === 1) return sel;
        }
      }

      if (depth >= 3) return tag;

      const parent = el.parentElement;
      if (parent) {
        const siblings = Array.from(parent.querySelectorAll(':scope > ' + tag));
        const idx = siblings.indexOf(el) + 1;
        if (siblings.length === 1) {
          const parentSel = bestSelector(parent, depth + 1);
          return `${parentSel} > ${tag}`;
        }
        const parentSel = bestSelector(parent, depth + 1);
        return `${parentSel} > ${tag}:nth-of-type(${idx})`;
      }

      return tag;
    }

    const attrKeys = ['type', 'name', 'placeholder', 'href', 'data-testid', 'id', 'role', 'aria-label', 'value'];

    for (const el of document.querySelectorAll(selector)) {
      if (seen.has(el)) continue;
      seen.add(el);

      const rect = el.getBoundingClientRect();
      if (rect.width === 0 && rect.height === 0) continue;

      const tag = el.tagName.toLowerCase();
      const isInteractive = interactiveTags.has(tag) ||
        interactiveRoles.has(el.getAttribute('role') || '') ||
        el.hasAttribute('tabindex');

      const text = (
        el.textContent ||
        el.getAttribute('aria-label') ||
        el.getAttribute('placeholder') ||
        ''
      ).trim().substring(0, 80);

      const attributes: Record<string, string> = {};
      for (const key of attrKeys) {
        const val = el.getAttribute(key);
        if (val !== null) attributes[key] = val;
      }

      results.push({
        selector: bestSelector(el),
        role: inferRole(el),
        text,
        tag,
        bounds: {
          x: Math.round(rect.x),
          y: Math.round(rect.y),
          w: Math.round(rect.width),
          h: Math.round(rect.height),
        },
        interactive: isInteractive,
        attributes,
      });
    }

    return results;
  });

  return {
    version: 1,
    viewport: { width: viewportSize.width, height: viewportSize.height },
    elements,
  };
}

async function generateManifest(page: Page): Promise<object> {
  const viewportSize = page.viewportSize() ?? { width: 1280, height: 720 };
  const userAgent = await page.evaluate(() => navigator.userAgent);

  let playwrightVersion = 'unknown';
  try {
    const pkgPath = require.resolve('@playwright/test/package.json');
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'));
    playwrightVersion = pkg.version;
  } catch {
    // fallback
  }

  return {
    version: 1,
    url: page.url(),
    viewport: { width: viewportSize.width, height: viewportSize.height },
    timestamp: new Date().toISOString(),
    playwright: playwrightVersion,
    userAgent,
  };
}
