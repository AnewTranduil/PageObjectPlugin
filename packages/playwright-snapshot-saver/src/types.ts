// Trace-extraction-only types. Live-capture types
// (SaveSnapshotOptions, SnapshotResult, ManifestJson) moved to
// @pagemirror/snapshot-core in task 15 and are re-exported from
// `index.ts` for consumer convenience.

export interface SnapshotMarkerOptions {
  /** Page identifier — becomes the parent directory (e.g. 'login'). */
  page: string;
  /** State within the page (default: 'main'). */
  state?: string;
}

export interface ExtractOptions {
  /** Report directory, trace ZIP path, or URL. */
  source: string;
  /** Output directory (default: '.snapshots'). */
  outputDir?: string;
  /** Generate screenshot from trace screencast frame (default: false). */
  screenshot?: boolean;
  /** Generate manifest.json (default: true). */
  manifest?: boolean;
  /** Filter to extract only specific page/state. */
  filter?: {
    page?: string;
    state?: string;
  };
}

export interface ExtractResult {
  snapshots: Array<{
    page: string;
    state: string;
    outputDir: string;
    files: {
      html: string;
      /** Absolute path to resources/screenshot.webp, when produced. */
      screenshot?: string;
      manifest?: string;
    };
  }>;
}
