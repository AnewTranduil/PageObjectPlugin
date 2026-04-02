# Changelog

## 0.6.0

### Breaking Changes

- **Screenshot extraction disabled by default.** `extractSnapshots()` and the reporter no longer generate `screenshot.webp` from trace screencast frames unless explicitly opted in. Screencast frames are low-fidelity and often blank, providing no value.
  - To re-enable: pass `screenshot: true` in `ExtractOptions`, or `--screenshot` on the CLI.
  - `saveSnapshot()` (direct API) is **not affected** -- it still captures real `page.screenshot()` by default.

### Changed

- CLI: added `--screenshot` flag to opt in to screenshot extraction. `--no-screenshot` is still accepted for backwards compatibility.
- `ExtractOptions.screenshot` default changed from `true` to `false`.

### Removed

- Deleted blank `screenshot.webp` files from `test-project/` snapshots.
