export interface SaveSnapshotOptions {
  /** Base output directory (e.g., path.join(__dirname, '.snapshots')) */
  outputDir: string;

  /** Snapshot name — becomes the subdirectory (e.g., 'initial', 'error-state') */
  name: string;

  /** Optional group name — creates parent dir (e.g., 'login' -> .snapshots/login/initial/) */
  group?: string;

  /** Override viewport dimensions (default: reads from page.viewportSize()) */
  viewport?: { width: number; height: number };

  /** Screenshot options */
  screenshot?: {
    enabled?: boolean;       // default: true
    fullPage?: boolean;      // default: false
    format?: 'png' | 'jpeg'; // default: 'png'
  };

  /** Generate manifest.json (default: true) */
  manifest?: boolean;
}

export interface SnapshotResult {
  /** Absolute path to the snapshot directory */
  outputDir: string;
  /** Paths to all generated files */
  files: {
    html: string;
    screenshot?: string;
    manifest?: string;
  };
}

export interface ManifestJson {
  version: number;
  url: string;
  viewport: { width: number; height: number };
  timestamp: string;
  playwright: string;
  userAgent: string;
}

export interface SnapshotMarkerOptions {
  /** Page identifier — becomes the parent directory (e.g., 'login', 'dashboard') */
  page: string;
  /** State within the page (default: 'main') */
  state?: string;
}

export interface ExtractOptions {
  /** Report directory, trace ZIP path, or URL */
  source: string;
  /** Output directory (default: '.snapshots') */
  outputDir?: string;
  /** Generate screenshot from trace screencast frame (default: true) */
  screenshot?: boolean;
  /** Generate manifest.json (default: true) */
  manifest?: boolean;
  /** Filter to extract only specific page/state */
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
      screenshot?: string;
      manifest?: string;
    };
  }>;
}
