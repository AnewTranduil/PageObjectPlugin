# @pagemirror/snapshot-core

Framework-agnostic core for building [Page Mirror](https://github.com/AnewTranduil/PageObjectPlugin) snapshot bundles. This package contains no driver-specific code — it's the shared engine that Playwright, Selenium, Cypress, and Appium adapters all build on.

If you just want to capture Playwright snapshots, use [`playwright-snapshot-saver`](https://www.npmjs.com/package/playwright-snapshot-saver) instead. Reach for this package when you're:

- Writing a **new driver adapter** for a framework Page Mirror doesn't support yet.
- Building a **custom reporter** that post-processes captured pages.
- Consuming raw Playwright trace data and want the rendering pipeline without the Playwright-specific glue.

## Installation

```bash
npm install @pagemirror/snapshot-core
```

## What this package provides

A small set of pure functions plus a couple of interfaces. Everything is stateless; you orchestrate.

### Live capture

1. **`collectorSource`** / **`collectPage`** — browser-side collector that serializes `document.documentElement`, pulls in every `<link rel="stylesheet">` + `<style>`, and returns a `CollectedPayload`.
2. **`assembleHtml(captured)`** — rewrites the collected HTML to reference CSS sidecars under `resources/<sha1>.css`.
3. **`buildManifest(captured, driver?)`** — produces a schema-v2 `manifest.json` object.
4. **`saveSnapshot(adapter, options)`** — orchestrator that writes the full bundle to disk. Takes a `PageAdapter` you implement.

### Trace pipeline (framework-agnostic)

1. **`TraceBackend`** — the single interface you implement to give core access to your driver's trace data (frame snapshots, resources, screencast frames, resource-by-sha1).
2. **`renderSnapshot(backend, pageOrFrameId, snapshotName)`** — pure renderer that turns a `FrameSnapshot` plus its resource overrides into HTML.
3. **`inlineResources(rendered)`** — resolves every `<link>`, `<img>`, CSS `url(...)`, `@font-face`, and SVG `<use>` reference against the backend's sha1 store, emitting files under `resources/` and rewriting the HTML to match. Strips any `<base>` element.
4. **`extractFromBackend(backend, markers, options)`** — orchestrator that takes a list of `TraceMarker`s and writes one v2 bundle per marker.

## Writing a new driver adapter

A driver adapter is a class that implements `PageAdapter`. It has one job: run the collector inside a live page and hand core a `CapturedPage`.

```ts
import {
  PageAdapter,
  CaptureRequest,
  CapturedPage,
  collectorSource,
} from '@pagemirror/snapshot-core';

export class MyDriverAdapter implements PageAdapter {
  constructor(private readonly page: MyDriverPage) {}

  async capture(request: CaptureRequest): Promise<CapturedPage> {
    // 1. Run `collectorSource` inside the page. Every adapter ships the
    //    same stringified source so the collector behaves identically
    //    everywhere. Most drivers need to eval the source inside the
    //    page rather than passing the function directly — serializers
    //    often mangle nested async function bodies.
    const payload = await this.page.evaluate(
      async ({ src, opts }) => {
        // eslint-disable-next-line no-eval
        const fn = eval(`(${src})`) as (o: unknown) => Promise<unknown>;
        return fn(opts);
      },
      {
        src: collectorSource,
        opts: {
          extraSelectors: request.extraSelectors,
          excludeSelectors: request.excludeSelectors,
          extraAttributes: request.extraAttributes,
        },
      },
    );

    // 2. Fill in driver-specific metadata.
    const captured: CapturedPage = {
      html: payload.html,
      stylesheets: payload.stylesheets,
      resources: [],
      url: this.page.url(),
      viewport: await this.page.viewportSize(),
      userAgent: await this.page.userAgent(),
    };

    // 3. Optional screenshot — push it into `resources` with the final filename.
    if (request.screenshot) {
      const bytes = await this.page.screenshot({
        format: request.screenshot.format,
        fullPage: request.screenshot.fullPage,
      });
      captured.resources.push({
        filename: `screenshot.${request.screenshot.format}`,
        bytes,
      });
    }

    return captured;
  }
}
```

Consumers then call `saveSnapshot`:

```ts
import { saveSnapshot } from '@pagemirror/snapshot-core';

await saveSnapshot(new MyDriverAdapter(page), {
  outputDir: '.snapshots',
  name: 'initial',
  group: 'login',
  driver: { name: 'my-driver', version: '1.2.3' },
});
```

The `driver.name` becomes the key in `manifest.json` — e.g. `{ "my-driver": "1.2.3" }` — so downstream tooling can distinguish bundles by producer.

## Writing a new trace backend

A trace backend lets you reuse the entire extraction pipeline with any trace format. Implement `TraceBackend`:

```ts
import { TraceBackend, FrameSnapshot, ResourceEntry, ScreencastFrame } from '@pagemirror/snapshot-core';

export class MyTraceBackend implements TraceBackend {
  getFrameSnapshots(pageOrFrameId: string): FrameSnapshot[] {
    // Return frame snapshots in trace order. Shape matches Playwright's
    // internal snapshot format — see docs on `FrameSnapshot`.
  }

  getResources(): ResourceEntry[] {
    // All network-resource entries, sorted by monotonicTime ascending.
  }

  getScreencastFrames(pageId: string): ScreencastFrame[] {
    // Optional — omit if your trace format has no screencast.
  }

  async readResource(sha1: string): Promise<Uint8Array | undefined> {
    // The single I/O primitive. Return bytes for the given sha1, or
    // undefined when unknown.
  }
}
```

Then extract:

```ts
import { extractFromBackend, TraceMarker } from '@pagemirror/snapshot-core';

const markers: TraceMarker[] = [
  {
    page: 'login',
    state: 'initial',
    pageOrFrameId: 'page@abc123',
    snapshotName: 'after@call42',
    timestamp: 1.234,
    wallTime: 1705312345678,
  },
];

const result = await extractFromBackend(backend, markers, {
  outputDir: '.snapshots',
  screenshot: true,          // emits resources/screenshot.webp when available
  manifest: true,
  driver: { name: 'my-driver', version: '1.2.3' },
});

for (const info of result.snapshots) {
  console.log(`${info.page}/${info.state} -> ${info.outputDir}`);
}
```

`extractFromBackend` is the only orchestrator you need. It calls `renderSnapshot` and `inlineResources` internally, writes the v2 bundle layout to disk, and returns the paths.

## Bundle format (v2)

```
<name>/
  index.html                   # Sanitized DOM referencing resources/
  manifest.json                # Schema version 2
  resources/
    screenshot.png|webp        # Optional
    <sha1>.css                 # Stylesheet sidecars
    <sha1>.woff2|png|jpg|…     # Fonts, images, media (trace-extracted bundles)
```

`manifest.json`:

```json
{
  "version": 2,
  "url": "https://example.com/login",
  "viewport": { "width": 1280, "height": 720 },
  "timestamp": "2025-01-15T10:30:00Z",
  "userAgent": "Mozilla/5.0 …",
  "playwright": "1.58.0"
}
```

Exactly one driver field is populated per manifest, matching `DriverInfo.name`. The v1 format is **not backwards compatible** and is refused by the plugin.

See [`docs/snapshot-bundle-spec.md`](../../docs/snapshot-bundle-spec.md) in the main repo for the authoritative spec.

## Public API reference

### Types

| Type                       | Purpose                                                                          |
|----------------------------|----------------------------------------------------------------------------------|
| `StylesheetData`           | One stylesheet (`href?`, `source`) as produced by the collector                   |
| `Resource`                 | A file to be written under `resources/` (`filename`, `bytes`)                     |
| `CapturedPage`             | Output of a `PageAdapter.capture()` call                                         |
| `CollectorOptions`         | `extraSelectors` / `excludeSelectors` / `extraAttributes`                         |
| `ScreenshotOptions`        | `{ format: 'png' \| 'webp', fullPage: boolean }`                                  |
| `CaptureRequest`           | `CollectorOptions & { screenshot?: ScreenshotOptions }`                           |
| `PageAdapter`              | The driver abstraction — `capture(request): Promise<CapturedPage>`                |
| `DriverInfo`               | `{ name, version }` written into the manifest                                    |
| `SaveSnapshotOptions`      | Options for `saveSnapshot`                                                       |
| `SnapshotResult`           | `{ outputDir, files: { html, manifest?, resources } }`                           |
| `ManifestJson`             | Schema-v2 manifest shape                                                         |
| `MANIFEST_VERSION`         | Constant `2`                                                                     |

### Trace types

| Type                       | Purpose                                                                          |
|----------------------------|----------------------------------------------------------------------------------|
| `NodeSnapshot`             | Recursive DOM tuple layout matching Playwright's internal format                 |
| `FrameSnapshot`            | One captured DOM state for a frame                                               |
| `ResourceEntry`            | Network-resource log entry                                                       |
| `ResourceOverride`         | Per-snapshot URL → sha1 override                                                 |
| `ScreencastFrame`          | One screencast frame (sha1 + timestamp)                                          |
| `TraceBackend`             | The trace-data abstraction you implement                                         |
| `TraceMarker`              | Snapshot marker consumed by `extractFromBackend`                                 |
| `ExtractFromBackendOptions`| Options for `extractFromBackend`                                                 |
| `ExtractFromBackendResult` | `{ snapshots: ExtractedSnapshotInfo[] }`                                         |

### Functions

| Function                  | Description                                                                       |
|---------------------------|-----------------------------------------------------------------------------------|
| `collectPage(opts)`       | Runs the browser-side collector. Call inside the page.                             |
| `collectorSource`         | Stringified version of `collectPage`, suitable for `page.evaluate(source, opts)`.  |
| `assembleHtml(captured)`  | Rewrites captured HTML, returns `{ html, cssResources }`.                          |
| `buildManifest(captured, driver?, now?)` | Produces a v2 `ManifestJson`.                                      |
| `saveSnapshot(adapter, options)` | Full live-capture orchestrator.                                             |
| `renderSnapshot(backend, pageOrFrameId, snapshotName)` | Renders a single `FrameSnapshot` to HTML.            |
| `inlineResources(rendered)` | Resolves all resource references, emitting files under `resources/`.            |
| `extractFromBackend(backend, markers, options)` | Full trace-extraction orchestrator.                       |
| `extensionFromContentType(mimeType)` | Maps a MIME type to a canonical file extension.                      |

## Requirements

- Node.js 18+

## License

MIT
