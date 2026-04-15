// @pagemirror/snapshot-core — public barrel.

export type {
  StylesheetData,
  Resource,
  CapturedPage,
  CollectorOptions,
  ScreenshotOptions,
  CaptureRequest,
  PageAdapter,
  DriverInfo,
  SaveSnapshotOptions,
  SnapshotResult,
  ManifestJson,
} from './types';
export { MANIFEST_VERSION } from './types';

export { buildManifest } from './manifest';
export { assembleHtml } from './assemble-html';
export type { AssembleResult } from './assemble-html';

export { collectPage, collectorSource } from './browser/collector';
export type { CollectedPayload } from './browser/collector';

export { saveSnapshot } from './save-snapshot';

// --- Trace pipeline (framework-agnostic) ------------------------------------

export type {
  NodeSnapshot,
  FrameSnapshot,
  ResourceEntry,
  ResourceOverride,
  ScreencastFrame,
  TraceBackend,
} from './trace/types';

export { renderSnapshot } from './trace/renderer';
export type { RenderedSnapshot } from './trace/renderer';

export { inlineResources } from './trace/inline';
export type { InlineResult, InlinedResource, RenderedSnapshotInput } from './trace/inline';

export { extractFromBackend } from './trace/extract';
export type {
  TraceMarker,
  ExtractFromBackendOptions,
  ExtractFromBackendResult,
  ExtractedSnapshotInfo,
} from './trace/extract';

export { extensionFromContentType } from './trace/content-type';
