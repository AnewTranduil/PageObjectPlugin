import { Page } from '@playwright/test';
import {
  CaptureRequest,
  CapturedPage,
  PageAdapter,
  collectorSource,
} from '@pagemirror/snapshot-core';
import type { CollectedPayload } from '@pagemirror/snapshot-core';

/**
 * Playwright-flavored `PageAdapter`. Runs `@pagemirror/snapshot-core`'s
 * browser collector inside the page via `page.evaluate`, attaches the
 * Playwright-specific metadata (viewport, url, userAgent, optional
 * screenshot), and returns a `CapturedPage` the core can assemble.
 *
 * Screenshot format support: Playwright's `page.screenshot()` natively
 * produces png and jpeg. The live-capture path therefore only supports
 * `format: 'png'`. `format: 'webp'` throws a clear error — for webp
 * bytes, use the trace-extraction path (`extractSnapshots`) which pulls
 * frames directly from Playwright's screencast.
 */
export class PlaywrightAdapter implements PageAdapter {
  constructor(private readonly page: Page) {}

  async capture(request: CaptureRequest): Promise<CapturedPage> {
    const page = this.page;

    // 1. Run the framework-agnostic collector inside the page.
    const collectorFn = new Function(
      'opts',
      `return (${collectorSource})(opts)`,
    ) as (opts: CaptureRequest) => Promise<CollectedPayload>;
    const payload: CollectedPayload = await page.evaluate(collectorFn, {
      extraSelectors: request.extraSelectors,
      excludeSelectors: request.excludeSelectors,
      extraAttributes: request.extraAttributes,
    });

    // 2. Driver-specific metadata.
    const viewportSize = page.viewportSize() ?? { width: 1280, height: 720 };
    const userAgent = await page.evaluate(() => navigator.userAgent);

    const captured: CapturedPage = {
      html: payload.html,
      stylesheets: payload.stylesheets,
      resources: [],
      url: page.url(),
      viewport: { width: viewportSize.width, height: viewportSize.height },
      userAgent,
    };

    // 3. Optional screenshot.
    if (request.screenshot) {
      if (request.screenshot.format !== 'png') {
        throw new Error(
          `PlaywrightAdapter: screenshot.format='${request.screenshot.format}' is not supported on ` +
            `the live-capture path. Playwright's page.screenshot() produces png only. ` +
            `Use format='png' or switch to the trace-extraction path (extractSnapshots) for other formats.`,
        );
      }
      const buffer = await page.screenshot({
        type: 'png',
        fullPage: request.screenshot.fullPage,
      });
      captured.resources.push({
        filename: 'screenshot.png',
        bytes: new Uint8Array(buffer),
      });
    }

    return captured;
  }
}
