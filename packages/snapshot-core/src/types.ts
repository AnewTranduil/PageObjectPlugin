/**
 * Framework-agnostic snapshot-core types.
 *
 * Shared across every language/driver adapter (Playwright, Selenium,
 * Cypress, Appium, ...). See `docs/snapshot-bundle-spec.md` for the
 * on-disk bundle format these types describe.
 */

/**
 * One stylesheet as collected from a live page. `source` is the raw CSS
 * text. `href` is the original stylesheet URL when the sheet was fetched
 * from a `<link rel="stylesheet">` — used only so `assembleHtml` can
 * rewrite the matching `<link>` tag. Inline `<style>` blocks have `href`
 * omitted.
 */
export interface StylesheetData {
  href?: string;
  source: string;
}

/**
 * A file to be written under the bundle's `resources/` directory. The
 * filename is the final basename (no directory component). Paths are
 * assembled by `save-snapshot.ts`.
 */
export interface Resource {
  /** Final filename under `resources/`, e.g. `screenshot.webp` or `a1b2c3.css`. */
  filename: string;
  bytes: Uint8Array;
}

/**
 * The result of the browser-side `collectPage()` call plus whatever
 * metadata the driver adapter can attach (url, viewport, userAgent,
 * screenshot).
 */
export interface CapturedPage {
  /** Serialized outer HTML of `document.documentElement`. */
  html: string;
  /** Every `<link rel="stylesheet">` + `<style>` the collector resolved. */
  stylesheets: StylesheetData[];
  /**
   * Non-stylesheet resources the adapter produced (most commonly the
   * screenshot, if enabled). Core turns these into sidecar files under
   * `resources/` verbatim.
   */
  resources: Resource[];
  url: string;
  viewport: { width: number; height: number };
  userAgent?: string;
}

/**
 * Options forwarded to the browser-side collector. The collector supports
 * the `extra*` / `exclude*` knobs so callers can tune DOM sanitization
 * per-snapshot.
 */
export interface CollectorOptions {
  extraSelectors?: string[];
  excludeSelectors?: string[];
  extraAttributes?: string[];
}

export interface ScreenshotOptions {
  format: 'webp' | 'png';
  fullPage: boolean;
}

/**
 * Request shape passed to `PageAdapter.capture()`. Adapters must honor
 * the `screenshot` field: when present, run the driver's screenshot API
 * and push the result into `CapturedPage.resources` as
 * `{ filename: "screenshot.<format>", bytes }`. When omitted, no
 * screenshot is taken.
 */
export interface CaptureRequest extends CollectorOptions {
  screenshot?: ScreenshotOptions;
}

/**
 * The driver abstraction. Concrete implementations live in each language
 * adapter package (`PlaywrightAdapter`, `SeleniumAdapter`, ...). Every
 * adapter has exactly one responsibility: run the collector inside the
 * page under whatever API its driver exposes, fill in the URL / viewport
 * / userAgent / optional screenshot fields, and return the result.
 */
export interface PageAdapter {
  capture(request: CaptureRequest): Promise<CapturedPage>;
}

/**
 * Driver identity written into the manifest. The `name` becomes the
 * field key so consumers can distinguish `playwright: "1.48.0"` from
 * `selenium: "4.18.0"` without sniffing.
 */
export interface DriverInfo {
  name: 'playwright' | 'selenium' | 'cypress' | 'appium' | string;
  version: string;
}

export interface SaveSnapshotOptions extends CollectorOptions {
  /** Base output directory, e.g. `path.join(__dirname, '.snapshots')`. */
  outputDir: string;
  /** Snapshot name — becomes the subdirectory (e.g. `initial`). */
  name: string;
  /** Optional parent group (e.g. `login` → `.snapshots/login/initial/`). */
  group?: string;
  /**
   * Screenshot options. Pass `false` to disable, omit for default
   * (`{ format: 'png', fullPage: false }`), or specify a partial object.
   */
  screenshot?: Partial<ScreenshotOptions> | false;
  /** Generate `manifest.json`. Default `true`. */
  manifest?: boolean;
  /**
   * Driver identity written into the manifest. Adapters fill this in
   * from their own package metadata before calling core.
   */
  driver?: DriverInfo;
}

export interface SnapshotResult {
  outputDir: string;
  files: {
    html: string;
    manifest?: string;
    /** Absolute paths of every file written under `resources/`. */
    resources: string[];
  };
}

/**
 * Manifest schema (version 2). v1 had `screenshot.<ext>` at the top level
 * and CSS inlined as `<style>`; v2 moves both into `resources/`.
 */
export interface ManifestJson {
  version: 2;
  url: string;
  viewport: { width: number; height: number };
  timestamp: string;
  userAgent?: string;
  /**
   * Exactly one driver field is populated per manifest: the one matching
   * `DriverInfo.name`. Consumers read whichever is non-null.
   */
  playwright?: string;
  selenium?: string;
  cypress?: string;
  appium?: string;
  [driver: string]: unknown;
}

/** Current manifest schema version emitted by core. */
export const MANIFEST_VERSION = 2 as const;
