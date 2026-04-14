import { CapturedPage, DriverInfo, ManifestJson, MANIFEST_VERSION } from './types';

/**
 * Pure manifest builder — takes a `CapturedPage` from the adapter and
 * produces a `ManifestJson`. No `page` parameter, no driver API calls.
 * `previousVersion` is only used to preserve manifest compatibility when
 * re-running on an unchanged bundle (no behavior change since MANIFEST_VERSION
 * is a fixed literal; callers can keep passing the existing version and
 * core will happily re-emit the same value).
 *
 * NOTE: MANIFEST_VERSION here is the *schema* version, not a monotonic
 * write counter. Task 11 used to increment a counter on every content
 * change; v2 drops that behavior since it served no downstream consumer.
 */
export function buildManifest(
  captured: CapturedPage,
  driver?: DriverInfo,
  now: Date = new Date(),
): ManifestJson {
  const manifest: ManifestJson = {
    version: MANIFEST_VERSION,
    url: captured.url,
    viewport: {
      width: captured.viewport.width,
      height: captured.viewport.height,
    },
    timestamp: now.toISOString(),
  };
  if (captured.userAgent !== undefined) {
    manifest.userAgent = captured.userAgent;
  }
  if (driver !== undefined) {
    manifest[driver.name] = driver.version;
  }
  return manifest;
}
