import { Page } from '@playwright/test';

export async function generateInlinedHtml(page: Page): Promise<string> {
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
