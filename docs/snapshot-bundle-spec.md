# Snapshot Bundle Spec (v1)

> **Status:** Source of truth for the on-disk `.snapshots/<name>/` bundle format consumed by the PageObjectPlugin tool window and produced by all language-specific snapshot savers (TS / Python / JVM / Selenium / Cypress / Appium).
>
> This document promotes the format currently described in `CLAUDE.md` §"Snapshot Bundle Format" to a formal spec. Any change to the format MUST be accompanied by a version bump in `manifest.json.version` and a migration note below.

---

## Directory Layout

```
<snapshot-name>/
  index.html        # REQUIRED — sanitized DOM with CSS inlined
  screenshot.webp   # OPTIONAL — visual reference (PNG also accepted)
  manifest.json     # OPTIONAL but STRONGLY RECOMMENDED
```

The directory name is the snapshot identifier surfaced in the tool window dropdown.

## `index.html` requirements

- Self-contained: no external `<link rel="stylesheet">` or `<script src>`. All CSS inlined in `<style>` blocks or element `style` attributes.
- Scripts stripped or neutralized — the plugin renders this in a `srcdoc` iframe and does not execute arbitrary JS from snapshots.
- UTF-8 encoded.
- Preserves original element attributes so locators (`data-testid`, `role`, `aria-*`, `id`, `name`, classes) resolve identically to the live page.

## `manifest.json` schema (v1)

```json
{
  "version": 1,
  "url": "https://example.com/login",
  "viewport": { "width": 1280, "height": 720 },
  "timestamp": "2025-01-15T10:30:00Z",
  "playwright": "1.48.0",
  "userAgent": "Mozilla/5.0 ..."
}
```

Fields:
- `version` (int, required) — schema version. Current: `1`.
- `url` (string, required) — the page URL at capture time.
- `viewport` (object, required) — `{width, height}` in CSS pixels. Mobile adapters (B3) MAY add `{platform, deviceName}`.
- `timestamp` (string, required) — ISO-8601 UTC.
- `playwright` / `selenium` / `cypress` / `appium` (string, optional) — driver version that captured the snapshot. Exactly one SHOULD be present.
- `userAgent` (string, optional).

## Versioning

- The plugin reads `manifest.version` and refuses to render unknown future versions with a user-visible error.
- Breaking changes bump the integer. Additive changes (new optional fields) do NOT bump the version.

## Compatibility Notes

- Older `test-project/.snapshots/` bundles produced before this spec may omit `manifest.json`; the plugin treats them as v1 with unknown metadata.
- All language savers (`packages/playwright-snapshot-saver`, `packages/playwright-snapshot-saver-python`, `packages/playwright-snapshot-saver-jvm`, and future adapters) MUST emit bundles conforming to this spec. Any divergence is a bug.
