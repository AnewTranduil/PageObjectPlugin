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

  /** Additional CSS selectors to include in layout.json beyond the defaults */
  extraSelectors?: string[];

  /** Selectors to exclude from layout.json */
  excludeSelectors?: string[];

  /** Additional attribute keys to capture per element (beyond the defaults) */
  extraAttributes?: string[];
}

export interface SnapshotResult {
  /** Absolute path to the snapshot directory */
  outputDir: string;
  /** Number of elements captured in layout.json */
  elementCount: number;
  /** Paths to all generated files */
  files: {
    html: string;
    layout: string;
    screenshot?: string;
    manifest?: string;
  };
}

export interface LayoutJson {
  version: number;
  viewport: { width: number; height: number };
  elements: LayoutElement[];
}

export interface LayoutElement {
  selector: string;
  role: string | null;
  text: string;
  tag: string;
  bounds: { x: number; y: number; w: number; h: number };
  interactive: boolean;
  attributes: Record<string, string>;
}

export interface ManifestJson {
  version: number;
  url: string;
  viewport: { width: number; height: number };
  timestamp: string;
  playwright: string;
  userAgent: string;
}

export interface LayoutOptions {
  extraSelectors?: string[];
  excludeSelectors?: string[];
  extraAttributes?: string[];
}
