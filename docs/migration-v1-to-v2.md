# Migration Guide: Snapshot Bundle v1 to v2

## Overview

The snapshot bundle format was upgraded from v1 to v2 as part of the
`@pagemirror/snapshot-core` extraction (Task 15). The Page Mirror plugin
(v2024.3+) **only loads v2 bundles** — bundles with `manifest.version`
set to any other integer are rejected with a log warning.

If your snapshots stopped loading after an upgrade, this guide explains
what changed and how to fix it.

## What Changed

| Aspect | v1 | v2 |
|---|---|---|
| **Screenshot location** | `<bundle>/screenshot.<ext>` (top-level) | `<bundle>/resources/screenshot.<ext>` |
| **CSS handling** | Inlined as `<style>` inside `index.html` by the saver | Written as `resources/<sha1>.css` sidecars; plugin inlines on read |
| **`manifest.version`** | Write counter that incremented on every save (1 → 2 → 3 → ...) | Fixed schema version, always `2` |
| **Top-level files** | `index.html`, `screenshot.*`, `manifest.json` | `index.html`, `manifest.json`, `resources/` |
| **Core package** | All logic in `playwright-snapshot-saver` | Shared logic in `@pagemirror/snapshot-core`, driver-specific adapters on top |

### The version counter bug

In v1, `manifest.version` was overloaded: Task 11 introduced a
monotonic write counter that read the existing version, added 1, and
wrote it back. This meant `manifest.version` drifted upward with every
test run (1 → 2 → 3 → ...) and had nothing to do with the bundle
schema.

v2 restores the original intent: `manifest.version` is a **fixed schema
version** (`2`). The value is set by the `MANIFEST_VERSION` constant in
`@pagemirror/snapshot-core` and never reads or increments the previous
value.

## Symptoms of a Stale Build

If you see this in the IDE log:

```
Refusing snapshot bundle at .snapshots/login/initial:
manifest.version=3, plugin supports version=2.
Re-run your snapshot saver to regenerate bundles in the v2 layout.
```

...your test-project is still running a **stale pre-v2 build** of
`playwright-snapshot-saver` that contains the old incrementing counter.
The `dist/` directory was compiled before the core extraction and
still imports `./manifest-generator` instead of `@pagemirror/snapshot-core`.

## How to Upgrade

### Option A: Rebuild packages (recommended)

If you use local `file:` dependencies (the default for this monorepo):

```bash
# 1. Build core (produces dist/ that snapshot-saver depends on)
cd packages/snapshot-core
npm install
npm run build

# 2. Rebuild snapshot-saver against the new core
cd ../playwright-snapshot-saver
npm install
npm run build

# 3. Reinstall in your test project and regenerate snapshots
cd ../test-project
npm install
npx playwright test
```

After this, every `manifest.json` will contain `"version": 2`.

### Option B: Manual bundle migration

If you can't re-run tests (e.g. snapshots captured from a CI
environment you no longer have access to):

1. **Fix `manifest.json`** — set `"version": 2`.
2. **Create `resources/` directory** inside the bundle.
3. **Move screenshots** — move any top-level `screenshot.webp` or
   `screenshot.png` into `resources/`.
4. **CSS sidecars** (optional) — if you want sidecar CSS instead of
   inline `<style>` blocks, extract each `<style>` body into
   `resources/<sha1>.css` and replace the `<style>` with
   `<link rel="stylesheet" href="resources/<sha1>.css">`. This step is
   optional: inline `<style>` tags still work in v2.

Example before (v1):

```
initial/
  index.html
  screenshot.webp
  manifest.json          <- "version": 3 (stale counter)
```

Example after (v2):

```
initial/
  index.html
  manifest.json          <- "version": 2
  resources/
    screenshot.webp
```

## Verifying the Fix

1. Open a `.page.ts` file in the IDE with `runIde`.
2. The Page Mirror tool window should load the snapshot and render it.
3. Check the IDE log (`Help > Show Log in Explorer`) — there should be
   no "Refusing snapshot bundle" warnings.
4. The status bar widget should show the snapshot name.

## Reference

- Bundle spec: [`docs/snapshot-bundle-spec.md`](snapshot-bundle-spec.md)
- Core package: [`packages/snapshot-core/`](../packages/snapshot-core/)
- Plugin loader: `SnapshotBundle.kt` — `isSupportedVersion()` method
- Manifest builder: `packages/snapshot-core/src/manifest.ts` (`MANIFEST_VERSION` constant is defined in `packages/snapshot-core/src/types.ts` and re-exported via `index.ts`)
