/**
 * MIME type → file extension mapping for resources materialized from a
 * trace. The map is deliberately narrow and explicit: we only list types
 * that real web pages serve to browsers. Unknown types fall back to
 * `bin`, which keeps the resource addressable-by-sha1 on disk without
 * pretending to know what it is.
 *
 * Callers pass the raw `Content-Type` header value (e.g.
 * `text/css; charset=utf-8`). `;`-delimited parameters and surrounding
 * whitespace are stripped before the lookup.
 */

const MIME_TO_EXT: Record<string, string> = {
  // Stylesheets
  'text/css': 'css',
  // Images
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/jpg': 'jpg',
  'image/gif': 'gif',
  'image/webp': 'webp',
  'image/svg+xml': 'svg',
  'image/bmp': 'bmp',
  'image/x-icon': 'ico',
  'image/vnd.microsoft.icon': 'ico',
  'image/avif': 'avif',
  // Fonts
  'font/woff': 'woff',
  'font/woff2': 'woff2',
  'font/ttf': 'ttf',
  'font/otf': 'otf',
  'application/font-woff': 'woff',
  'application/font-woff2': 'woff2',
  'application/x-font-ttf': 'ttf',
  'application/x-font-otf': 'otf',
  // Media
  'video/mp4': 'mp4',
  'video/webm': 'webm',
  'video/ogg': 'ogv',
  'audio/mpeg': 'mp3',
  'audio/ogg': 'ogg',
  'audio/wav': 'wav',
  'audio/webm': 'weba',
  // Text
  'text/plain': 'txt',
  'text/html': 'html',
  'text/javascript': 'js',
  'application/javascript': 'js',
  'application/json': 'json',
  'application/xml': 'xml',
  'text/xml': 'xml',
};

export function extensionFromContentType(contentType: string | undefined): string {
  if (!contentType) return 'bin';
  const base = contentType.split(';')[0].trim().toLowerCase();
  return MIME_TO_EXT[base] ?? 'bin';
}
