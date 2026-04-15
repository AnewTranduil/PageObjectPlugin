/**
 * `TraceBackend` implementation for Playwright traces. All the Playwright
 * internal knowledge (underscored field names, nested storage maps, Blob
 * return type from `resourceForSha1`) is contained here — the rest of
 * `snapshot-core` sees only the framework-agnostic `TraceBackend` shape.
 *
 * Mapping table:
 *
 *   Playwright internal                           → core shape
 *   storage._frameSnapshots.get(id).raw           → getFrameSnapshots(id)
 *   storage._contextToResources (flattened)       → getResources()
 *   contextEntries[*].pages[*].screencastFrames   → getScreencastFrames(pageId)
 *   await loader.resourceForSha1(sha1)            → readResource(sha1) (Blob → Uint8Array)
 *
 *   resource._monotonicTime                       → resource.monotonicTime
 *   resource._frameref                            → resource.frameref
 *   resource.response.content._sha1               → resource.response.content.sha1
 */

import type {
  FrameSnapshot,
  ResourceEntry,
  ScreencastFrame,
  TraceBackend,
} from '@pagemirror/snapshot-core';
import type { TraceLoader as TraceLoaderType } from 'playwright-core/lib/utils/isomorphic/trace/traceLoader';

/**
 * Playwright exposes resource rows with underscored internal names.
 * Typing them explicitly so the reshape in `getResources()` is
 * type-checked instead of all-`any`.
 */
interface RawPwResource {
  request: { url: string; method: string };
  response: {
    status: number;
    content: { _sha1?: string; mimeType?: string };
  };
  _monotonicTime?: number;
  _frameref?: string;
}

interface RawFrameSnapshot {
  callId: string;
  snapshotName: string;
  pageId: string;
  frameId: string;
  timestamp: number;
  wallTime?: number;
  viewport: { width: number; height: number };
  url: string;
  doctype?: string;
  html: unknown;
  resourceOverrides: Array<{ url: string; sha1?: string; ref?: number }>;
  isMainFrame?: boolean;
}

export class PlaywrightTraceBackend implements TraceBackend {
  private readonly storage: {
    _frameSnapshots: Map<string, { raw: RawFrameSnapshot[] }>;
    _contextToResources: Map<string, RawPwResource[]>;
  };

  constructor(private readonly loader: TraceLoaderType) {
    // `storage()` returns `SnapshotStorage` — its private maps carry the
    // data we need. We cast once here and keep the rest of the class
    // typed.
    this.storage = (loader as unknown as { storage(): unknown }).storage() as typeof this.storage;
  }

  getFrameSnapshots(pageOrFrameId: string): FrameSnapshot[] {
    const bucket = this.storage._frameSnapshots.get(pageOrFrameId);
    if (!bucket) return [];
    return bucket.raw.map((s) => reshapeSnapshot(s));
  }

  getResources(): ResourceEntry[] {
    const out: ResourceEntry[] = [];
    for (const list of this.storage._contextToResources.values()) {
      for (const r of list) out.push(reshapeResource(r));
    }
    return out;
  }

  getScreencastFrames(pageId: string): ScreencastFrame[] {
    for (const context of this.loader.contextEntries) {
      for (const page of context.pages) {
        if (page.pageId === pageId) {
          return page.screencastFrames.map((f) => ({
            sha1: f.sha1,
            timestamp: f.timestamp,
            frameSwapWallTime: f.frameSwapWallTime,
          }));
        }
      }
    }
    return [];
  }

  async readResource(sha1: string): Promise<Uint8Array | undefined> {
    const blob = await this.loader.resourceForSha1(sha1);
    if (!blob) return undefined;
    const buf = await blob.arrayBuffer();
    return new Uint8Array(buf);
  }
}

function reshapeSnapshot(raw: RawFrameSnapshot): FrameSnapshot {
  return {
    callId: raw.callId,
    snapshotName: raw.snapshotName,
    pageId: raw.pageId,
    frameId: raw.frameId,
    timestamp: raw.timestamp,
    wallTime: raw.wallTime,
    viewport: raw.viewport,
    url: raw.url,
    doctype: raw.doctype,
    html: raw.html as FrameSnapshot['html'],
    resourceOverrides: raw.resourceOverrides,
    isMainFrame: raw.isMainFrame,
  };
}

function reshapeResource(raw: RawPwResource): ResourceEntry {
  return {
    request: raw.request,
    response: {
      status: raw.response.status,
      content: {
        sha1: raw.response.content._sha1 ?? '',
        mimeType: raw.response.content.mimeType,
      },
    },
    monotonicTime: raw._monotonicTime,
    frameref: raw._frameref,
  };
}
