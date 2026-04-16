# Page Object Helper Changelog

## [Unreleased]

### Added

### Changed

### Fixed

### Removed

## [0.5.0] - 2026-04-16

### ⚠️ Breaking changes

- Snapshot bundle format bumped to **v2**. The plugin now refuses to
  load bundles whose `manifest.json` declares a version other than `2`.
  If you see a warning banner inside the Page Mirror tool window after
  upgrading, regenerate your snapshots with
  [`playwright-snapshot-saver`](https://www.npmjs.com/package/playwright-snapshot-saver)
  `≥ 0.7.0` — run `npx playwright test` in your project and the v2
  bundles will replace the v1 ones. See
  [docs/migration-v1-to-v2.md](docs/migration-v1-to-v2.md) for the full
  migration guide.

### Added

- Outdated-bundle banner in the Page Mirror tool window. When the
  snapshot scanner finds bundle directories whose manifest version is
  unsupported, the tool window renders an actionable banner above the
  snapshot iframe with a link to the migration guide. The banner
  disappears automatically after you regenerate the bundles.
- Strict v2 snapshot bundle support. The plugin reads CSS sidecars from
  `resources/<sha1>.css` and inlines them into the HTML before handing
  it to the JCEF `srcdoc` iframe, so `resources/` references resolve
  correctly without a base URL.
- Screenshots are now read from `resources/screenshot.png` or
  `resources/screenshot.webp` (was top-level in v1).

### Fixed

- Snapshots no longer scroll into empty space below and right of the
  rendered page when the iframe is scaled to fit the tool window width.
- Snapshots taller than the capture viewport (e.g. dashboards) are no
  longer clipped at 720 px — the iframe grows to the document's full
  rendered height.
- Switching focus to an already-open editor tab now reloads the matching
  snapshot. Previously the tool window stayed on whichever snapshot was
  loaded when the tab was first opened.

## [0.4.0] - 2026-04-07

### Changed

- Settings: updated page-object regex, configurable file extensions list, and snapshot location.
- Settings dialog now cleans up state when an applied configuration no longer matches.

## [0.3.0]

- Previous release.

[Unreleased]: https://github.com/AnewTranduil/PageObjectPlugin/compare/v0.5.0...HEAD
[0.5.0]: https://github.com/AnewTranduil/PageObjectPlugin/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/AnewTranduil/PageObjectPlugin/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/AnewTranduil/PageObjectPlugin/commits/v0.3.0
