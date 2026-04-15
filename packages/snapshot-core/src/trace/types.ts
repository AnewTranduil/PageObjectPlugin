/**
 * Framework-agnostic trace types.
 *
 * These shapes intentionally mirror Playwright's internal snapshot format
 * (see `playwright-core/lib/utils/isomorphic/trace/snapshotRenderer.js`
 * and `snapshotStorage.js`) so the Playwright backend is a near-zero-cost
 * reshape. Future backends (Selenium, Cypress, Appium) produce the same
 * shapes from their own trace formats.
 *
 * The only semantic deviations from Playwright's private types:
 *  - `_monotonicTime` → `monotonicTime` (leading underscore dropped)
 *  - `_frameref`      → `frameref`      (leading underscore dropped)
 *  - `readResource`   returns `Uint8Array | undefined` instead of `Blob`
 *
 * Everything else (including the recursive `NodeSnapshot` tuple layout) is
 * copy-compatible with Playwright's internal data.
 */

/**
 * Recursive DOM tuple layout used by Playwright traces. Three possible
 * shapes, distinguished at runtime:
 *
 *   1. string                                            — text node
 *   2. [[number, number]]                                — subtree reference
 *      [[snapshotDelta, nodeIndex]]: "find the Nth node in the snapshot
 *      `snapshotDelta` steps before this one and splice it in here".
 *   3. [tagName, attrs, ...children]                     — element
 *      attrs is a plain object of attribute-name → attribute-value strings.
 */
export type NodeSnapshot =
  | string
  | [[number, number]]
  | [string, Record<string, string>, ...NodeSnapshot[]];

/**
 * `resourceOverrides` entries attach a different sha1 to a URL for a given
 * snapshot — e.g. to reflect a cached response from an earlier timestamp.
 * `ref` is a back-reference to a prior snapshot in the same frame.
 */
export interface ResourceOverride {
  url: string;
  sha1?: string;
  ref?: number;
}

/**
 * One captured DOM snapshot (= one `page.content()` equivalent frozen at
 * a point in the trace). Matches Playwright's raw-snapshot shape from
 * `snapshotStorage.js:38-53` + the fields `snapshotRenderer.js:42-62`
 * reads off it.
 */
export interface FrameSnapshot {
  callId: string;
  snapshotName: string;
  pageId: string;
  frameId: string;
  /** Monotonic seconds from trace start. Used to bound resource lookup. */
  timestamp: number;
  /** Wall-clock milliseconds, optional — used for screencast-frame matching. */
  wallTime?: number;
  viewport: { width: number; height: number };
  url: string;
  doctype?: string;
  html: NodeSnapshot;
  resourceOverrides: ResourceOverride[];
  /** True for the top frame of a page. Used by backends to index by pageId. */
  isMainFrame?: boolean;
}

/**
 * One network-resource log entry. Used by `resourceByUrl` to map a live
 * URL reference to a sha1 stored in the trace.
 */
export interface ResourceEntry {
  request: { url: string; method: string };
  response: {
    status: number;
    content: { sha1: string; mimeType?: string };
  };
  /** Monotonic seconds; resources from after a snapshot's timestamp are ignored. */
  monotonicTime?: number;
  /** Frame the network event was recorded on; same-frame resources are preferred. */
  frameref?: string;
}

/**
 * One screencast frame — a low-frequency viewport screenshot that backends
 * can offer as `resources/screenshot.webp`. Matches the shape backends
 * already expose via Playwright's `PageEntry.screencastFrames`.
 */
export interface ScreencastFrame {
  sha1: string;
  timestamp: number;
  frameSwapWallTime?: number;
}

/**
 * Framework-agnostic trace accessor. The four methods map one-to-one to
 * Playwright's `SnapshotStorage._frameSnapshots`, `_contextToResources`
 * (flattened), `ContextEntry.pages[].screencastFrames`, and
 * `TraceLoader.resourceForSha1`.
 */
export interface TraceBackend {
  /**
   * Frame snapshots for a page or frame, in trace order (matches
   * `_frameSnapshots.get(pageOrFrameId).raw`). Returns `[]` for unknown
   * ids so the caller can gracefully iterate.
   */
  getFrameSnapshots(pageOrFrameId: string): FrameSnapshot[];
  /**
   * All network-resource entries visible to the trace, sorted by
   * `monotonicTime` ascending. `resourceByUrl` walks this array and
   * stops at the snapshot's timestamp.
   */
  getResources(): ResourceEntry[];
  /**
   * Screencast frames for a pageId, in trace order. Optional — backends
   * without screencasts (Selenium HTML-only traces) can omit this.
   */
  getScreencastFrames?(pageId: string): ScreencastFrame[];
  /**
   * Fetch a resource's bytes by sha1. This is the single I/O primitive
   * the rest of core needs — all other methods are synchronous reshapes.
   * Returns `undefined` when the sha1 is unknown.
   */
  readResource(sha1: string): Promise<Uint8Array | undefined>;
}
