import { Page } from '@playwright/test';
import { LayoutJson, LayoutOptions } from './types';

const DEFAULT_SELECTORS = [
  'button', 'input', 'select', 'textarea', 'a',
  '[role="button"]', '[role="link"]', '[tabindex]',
  '[id]', '[data-testid]',
];

const DEFAULT_ATTR_KEYS = [
  'type', 'name', 'placeholder', 'href', 'data-testid',
  'id', 'role', 'aria-label', 'value',
];

export async function generateLayout(
  page: Page,
  options?: LayoutOptions
): Promise<LayoutJson> {
  const viewportSize = page.viewportSize() ?? { width: 1280, height: 720 };

  const selectors = [...DEFAULT_SELECTORS, ...(options?.extraSelectors ?? [])];
  const excludeSelectors = options?.excludeSelectors ?? [];
  const attrKeys = [...DEFAULT_ATTR_KEYS, ...(options?.extraAttributes ?? [])];

  const elements = await page.evaluate(
    ({ selector, excludeSelectors, attrKeys }) => {
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

      // Check if element matches any exclude selector
      function isExcluded(el: Element): boolean {
        for (const sel of excludeSelectors) {
          try {
            if (el.matches(sel)) return true;
          } catch {
            // Invalid selector, skip
          }
        }
        return false;
      }

      for (const el of document.querySelectorAll(selector)) {
        if (seen.has(el)) continue;
        seen.add(el);

        if (isExcluded(el)) continue;

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
    },
    {
      selector: selectors.join(', '),
      excludeSelectors,
      attrKeys,
    }
  );

  return {
    version: 1,
    viewport: { width: viewportSize.width, height: viewportSize.height },
    elements,
  };
}
