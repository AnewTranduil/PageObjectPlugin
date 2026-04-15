/**
 * Runtime bootstrap script embedded at the top of every rendered trace
 * snapshot. Ported verbatim from Playwright's internal
 * `snapshotRenderer.js:191-439` (the `applyPlaywrightAttributes` function).
 *
 * The script runs inside the snapshot's own browser context after
 * `DOMContentLoaded`. Responsibilities:
 *   - materialize `__playwright_value_` / `__playwright_checked_` /
 *     `__playwright_selected_` / `__playwright_scroll_top_` / etc. as
 *     real DOM state
 *   - attach shadow roots from `<template __playwright_shadow_root_>`
 *   - highlight click targets passed via `targetIds`
 *   - paint canvases with a checkerboard + optional screenshot crop
 *
 * Adaptation notes vs. Playwright's copy:
 *   - The `blankSnapshotUrl` parameter keeps the same `data:text/html`
 *     fallback so `<iframe>` stubs without recorded content don't 404.
 *   - The `location.href.replace("/snapshot", "/closest-screenshot")`
 *     canvas-source path only works when running behind Playwright's
 *     trace-viewer HTTP server. Outside the trace viewer the image load
 *     fails, `onerror` fires, and canvases get the checkerboard fallback
 *     — which is the correct bundle-opened-in-a-plain-browser behavior.
 *
 * The script is emitted as a string because it must be serialized into
 * the HTML output. Types on the function body are loose (`any`) — it runs
 * in the browser, not Node, so TypeScript DOM typings are irrelevant at
 * the emission site.
 */

/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/no-unused-vars, prefer-const */

function applyPlaywrightAttributes(
  blankSnapshotUrl2: string,
  viewport2: any,
  ...targetIds2: string[]
): void {
  const win: any = (globalThis as any).window;
  const searchParams = new URLSearchParams(win.location.search);
  const shouldPopulateCanvasFromScreenshot = searchParams.has('shouldPopulateCanvasFromScreenshot');
  const isUnderTest = searchParams.has('isUnderTest');
  const frameBoundingRectsInfo: any = {
    viewport: viewport2,
    frames: new WeakMap(),
  };
  win['__playwright_frame_bounding_rects__'] = frameBoundingRectsInfo;
  const kPointerWarningTitle =
    'Recorded click position in absolute coordinates did not match the center of the clicked element. This is either due to the use of provided offset, or due to a difference between the test runner and the trace viewer operating systems.';
  const scrollTops: any[] = [];
  const scrollLefts: any[] = [];
  const targetElements: any[] = [];
  const canvasElements: any[] = [];
  let topSnapshotWindow: any = win;
  while (
    topSnapshotWindow !== topSnapshotWindow.parent &&
    !topSnapshotWindow.location.pathname.match(/\/page@[a-z0-9]+$/)
  ) {
    topSnapshotWindow = topSnapshotWindow.parent;
  }
  const visit = (root: any): void => {
    for (const e of root.querySelectorAll('[__playwright_scroll_top_]')) scrollTops.push(e);
    for (const e of root.querySelectorAll('[__playwright_scroll_left_]')) scrollLefts.push(e);
    for (const element of root.querySelectorAll('[__playwright_value_]')) {
      const inputElement: any = element;
      if (inputElement.type !== 'file')
        inputElement.value = inputElement.getAttribute('__playwright_value_');
      element.removeAttribute('__playwright_value_');
    }
    for (const element of root.querySelectorAll('[__playwright_checked_]')) {
      (element as any).checked = element.getAttribute('__playwright_checked_') === 'true';
      element.removeAttribute('__playwright_checked_');
    }
    for (const element of root.querySelectorAll('[__playwright_selected_]')) {
      (element as any).selected = element.getAttribute('__playwright_selected_') === 'true';
      element.removeAttribute('__playwright_selected_');
    }
    for (const element of root.querySelectorAll('[__playwright_popover_open_]')) {
      try {
        (element as any).showPopover();
      } catch {
        /* ignore */
      }
      element.removeAttribute('__playwright_popover_open_');
    }
    for (const element of root.querySelectorAll('[__playwright_dialog_open_]')) {
      try {
        if (element.getAttribute('__playwright_dialog_open_') === 'modal')
          (element as any).showModal();
        else (element as any).show();
      } catch {
        /* ignore */
      }
      element.removeAttribute('__playwright_dialog_open_');
    }
    for (const targetId of targetIds2) {
      for (const target of root.querySelectorAll(`[__playwright_target__="${targetId}"]`)) {
        const style = (target as any).style;
        style.outline = '2px solid #006ab1';
        style.backgroundColor = '#6fa8dc7f';
        targetElements.push(target);
      }
    }
    for (const iframe of root.querySelectorAll('iframe, frame')) {
      const boundingRectJson = iframe.getAttribute('__playwright_bounding_rect__');
      iframe.removeAttribute('__playwright_bounding_rect__');
      const boundingRect = boundingRectJson ? JSON.parse(boundingRectJson) : void 0;
      if (boundingRect)
        frameBoundingRectsInfo.frames.set(iframe, { boundingRect, scrollLeft: 0, scrollTop: 0 });
      const src = iframe.getAttribute('__playwright_src__');
      if (!src) {
        iframe.setAttribute('src', blankSnapshotUrl2);
      } else {
        const url = new URL(win.location.href);
        const index = url.pathname.lastIndexOf('/snapshot/');
        if (index !== -1) url.pathname = url.pathname.substring(0, index + 1);
        url.pathname += src.substring(1);
        iframe.setAttribute('src', url.toString());
      }
    }
    {
      const body = root.querySelector('body[__playwright_custom_elements__]');
      if (body && win.customElements) {
        const customElements = (body.getAttribute('__playwright_custom_elements__') || '').split(',');
        for (const elementName of customElements)
          win.customElements.define(elementName, class extends (win.HTMLElement as any) {});
      }
    }
    for (const element of root.querySelectorAll('template[__playwright_shadow_root_]')) {
      const template: any = element;
      const shadowRoot = template.parentElement.attachShadow({ mode: 'open' });
      shadowRoot.appendChild(template.content);
      template.remove();
      visit(shadowRoot);
    }
    for (const element of root.querySelectorAll('a'))
      element.addEventListener('click', (event: any) => {
        event.preventDefault();
      });
    if ('adoptedStyleSheets' in root) {
      const adoptedSheets = [...root.adoptedStyleSheets];
      for (const element of root.querySelectorAll('template[__playwright_style_sheet_]')) {
        const template: any = element;
        const sheet = new (win.CSSStyleSheet as any)();
        sheet.replaceSync(template.getAttribute('__playwright_style_sheet_'));
        adoptedSheets.push(sheet);
      }
      root.adoptedStyleSheets = adoptedSheets;
    }
    canvasElements.push(...root.querySelectorAll('canvas'));
  };
  const onLoad = (): void => {
    win.removeEventListener('load', onLoad);
    for (const element of scrollTops) {
      (element as any).scrollTop = +element.getAttribute('__playwright_scroll_top_');
      element.removeAttribute('__playwright_scroll_top_');
      if (frameBoundingRectsInfo.frames.has(element))
        frameBoundingRectsInfo.frames.get(element).scrollTop = (element as any).scrollTop;
    }
    for (const element of scrollLefts) {
      (element as any).scrollLeft = +element.getAttribute('__playwright_scroll_left_');
      element.removeAttribute('__playwright_scroll_left_');
      if (frameBoundingRectsInfo.frames.has(element))
        frameBoundingRectsInfo.frames.get(element).scrollLeft = (element as any).scrollLeft;
    }
    win.document.styleSheets[0].disabled = true;
    const search = new URL(win.location.href).searchParams;
    const isTopFrame = win === topSnapshotWindow;
    if (isTopFrame && search.get('pointX') && search.get('pointY')) {
      const pointX = +(search.get('pointX') as string);
      const pointY = +(search.get('pointY') as string);
      const pointElement: any = win.document.createElement('x-pw-pointer');
      pointElement.style.position = 'fixed';
      pointElement.style.backgroundColor = '#f44336';
      pointElement.style.width = '20px';
      pointElement.style.height = '20px';
      pointElement.style.borderRadius = '10px';
      pointElement.style.margin = '-10px 0 0 -10px';
      pointElement.style.zIndex = '2147483646';
      pointElement.style.display = 'flex';
      pointElement.style.alignItems = 'center';
      pointElement.style.justifyContent = 'center';
      const target = targetElements[0];
      const targetBox = target?.getBoundingClientRect();
      const targetCenter = target
        ? { x: targetBox.left + targetBox.width / 2, y: targetBox.top + targetBox.height / 2 }
        : null;
      pointElement.style.left = (targetCenter?.x ?? pointX) + 'px';
      pointElement.style.top = (targetCenter?.y ?? pointY) + 'px';
      const isAligned =
        !targetCenter ||
        (Math.abs(targetCenter.x - pointX) <= 10 && Math.abs(targetCenter.y - pointY) <= 10);
      if (!isAligned) {
        const warningElement: any = win.document.createElement('x-pw-pointer-warning');
        warningElement.textContent = '\u26A0';
        warningElement.style.fontSize = '19px';
        warningElement.style.color = 'white';
        warningElement.style.marginTop = '-3.5px';
        warningElement.style.userSelect = 'none';
        pointElement.appendChild(warningElement);
        pointElement.setAttribute('title', kPointerWarningTitle);
      }
      win.document.documentElement.appendChild(pointElement);
    }
    if (canvasElements.length > 0) {
      const drawCheckerboard = (context: any, canvas: any): void => {
        const createCheckerboardPattern = (): any => {
          const pattern: any = win.document.createElement('canvas');
          pattern.width = pattern.width / Math.floor(pattern.width / 24);
          pattern.height = pattern.height / Math.floor(pattern.height / 24);
          const context2 = pattern.getContext('2d');
          context2.fillStyle = 'lightgray';
          context2.fillRect(0, 0, pattern.width, pattern.height);
          context2.fillStyle = 'white';
          context2.fillRect(0, 0, pattern.width / 2, pattern.height / 2);
          context2.fillRect(pattern.width / 2, pattern.height / 2, pattern.width, pattern.height);
          return context2.createPattern(pattern, 'repeat');
        };
        context.fillStyle = createCheckerboardPattern();
        context.fillRect(0, 0, canvas.width, canvas.height);
      };
      const img: any = new (win.Image as any)();
      img.onload = (): void => {
        for (const canvas of canvasElements) {
          const context = (canvas as any).getContext('2d');
          const boundingRectAttribute = (canvas as any).getAttribute('__playwright_bounding_rect__');
          (canvas as any).removeAttribute('__playwright_bounding_rect__');
          if (!boundingRectAttribute) continue;
          let boundingRect: any;
          try {
            boundingRect = JSON.parse(boundingRectAttribute);
          } catch (e) {
            continue;
          }
          let currWindow: any = win;
          while (currWindow !== topSnapshotWindow) {
            const iframe = currWindow.frameElement;
            currWindow = currWindow.parent;
            const iframeInfo = currWindow['__playwright_frame_bounding_rects__']?.frames.get(iframe);
            if (!iframeInfo?.boundingRect) break;
            const leftOffset = iframeInfo.boundingRect.left - iframeInfo.scrollLeft;
            const topOffset = iframeInfo.boundingRect.top - iframeInfo.scrollTop;
            boundingRect.left += leftOffset;
            boundingRect.top += topOffset;
            boundingRect.right += leftOffset;
            boundingRect.bottom += topOffset;
          }
          const { width, height } =
            topSnapshotWindow['__playwright_frame_bounding_rects__'].viewport;
          boundingRect.left = boundingRect.left / width;
          boundingRect.top = boundingRect.top / height;
          boundingRect.right = boundingRect.right / width;
          boundingRect.bottom = boundingRect.bottom / height;
          const partiallyUncaptured = boundingRect.right > 1 || boundingRect.bottom > 1;
          const fullyUncaptured = boundingRect.left > 1 || boundingRect.top > 1;
          if (fullyUncaptured) {
            (canvas as any).title =
              `Playwright couldn't capture canvas contents because it's located outside the viewport.`;
            continue;
          }
          drawCheckerboard(context, canvas);
          if (shouldPopulateCanvasFromScreenshot) {
            context.drawImage(
              img,
              boundingRect.left * img.width,
              boundingRect.top * img.height,
              (boundingRect.right - boundingRect.left) * img.width,
              (boundingRect.bottom - boundingRect.top) * img.height,
              0,
              0,
              (canvas as any).width,
              (canvas as any).height,
            );
            if (partiallyUncaptured)
              (canvas as any).title =
                `Playwright couldn't capture full canvas contents because it's located partially outside the viewport.`;
            else
              (canvas as any).title =
                `Canvas contents are displayed on a best-effort basis based on viewport screenshots taken during test execution.`;
          } else {
            (canvas as any).title = 'Canvas content display is disabled.';
          }
          if (isUnderTest)
            console.log(
              `canvas drawn:`,
              JSON.stringify(
                [
                  boundingRect.left,
                  boundingRect.top,
                  boundingRect.right - boundingRect.left,
                  boundingRect.bottom - boundingRect.top,
                ].map((v: number) => Math.floor(v * 100)),
              ),
            );
        }
      };
      img.onerror = (): void => {
        for (const canvas of canvasElements) {
          const context = (canvas as any).getContext('2d');
          drawCheckerboard(context, canvas);
          (canvas as any).title = `Playwright couldn't show canvas contents because the screenshot failed to load.`;
        }
      };
      img.src = win.location.href.replace('/snapshot', '/closest-screenshot');
    }
  };
  const onDOMContentLoaded = (): void => visit(win.document);
  win.addEventListener('load', onLoad);
  win.addEventListener('DOMContentLoaded', onDOMContentLoaded);
}

/**
 * Blank-iframe fallback — inlined from `snapshotRenderer.js:486`. Used by
 * the runtime script when a `<frame>/iframe>` has no recorded content.
 */
export const BLANK_SNAPSHOT_URL =
  'data:text/html;base64,' +
  Buffer.from(
    '<body></body><style>body { color-scheme: light dark; background: light-dark(white, #333) }</style>',
  ).toString('base64');

/**
 * Build the `<script>` body that the renderer prepends to every snapshot.
 * The generated string is wrapped in parentheses + invoked with the blank
 * URL, viewport, and target-element ids, matching Playwright's
 * `snapshotRenderer.js:438-439`.
 */
export function buildRuntimeScript(
  viewport: { width: number; height: number },
  targetIds: string[],
): string {
  const targetArgs = targetIds.map((id) => `, ${JSON.stringify(id)}`).join('');
  return `
(${applyPlaywrightAttributes.toString()})(${JSON.stringify(BLANK_SNAPSHOT_URL)},${JSON.stringify(viewport)}${targetArgs})`;
}
