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
