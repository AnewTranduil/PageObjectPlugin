# Task 11: Manifest Fixes — Timestamp, Change Detection, Version Increment

> **Partially superseded by Task 15.** The timestamp fix and
> change-detection (no-op writes when content is unchanged) are still
> in effect. The "auto-incrementing version" behaviour was reverted:
> `manifest.version` is now the fixed schema version `2` (managed by
> `MANIFEST_VERSION` in `@pagemirror/snapshot-core`), not a write
> counter. See `docs/migration-v1-to-v2.md` for the rationale.

> **Goal:** Fix three manifest.json bugs: broken timestamps in extractor path, unconditional file overwrites, and static version field.
> **Depends on:** Task 10
> **Output:** Correct wall-clock timestamps, no-op writes when content unchanged, auto-incrementing version on content changes

## Motivation

The extractor path produces manifests with 1970 timestamps (`"1970-01-01T00:00:02.198Z"`) because `test.step` trace actions lack `wallTime`, causing fallback to monotonic `startTime`. Additionally, every extraction run overwrites all files unconditionally — creating noisy git diffs even when nothing changed. The `version` field is hardcoded to `1` and never increments.

---

## Bug 1: Broken Timestamp in Extractor Path

### Root Cause

In `src/trace/playwright-adapter.ts:193`:

```typescript
const markerTimestamp = action.wallTime ?? action.startTime;
```

`test.step` actions in Playwright traces have `wallTime` of `0` or `undefined`. The fallback `action.startTime` is monotonic milliseconds from trace start (e.g., `2198`). Passing this to `new Date(2198)` produces `"1970-01-01T00:00:02.198Z"`.

### Fix

Compute real wall-clock time using the parent `ContextEntry` timing:

```
realWallTime = context.wallTime + (action.startTime - context.startTime)
```

The `ContextEntry` reliably has both `wallTime` (epoch ms) and `startTime` (monotonic ms), providing the offset needed to convert any action's monotonic time to wall-clock time.

### File: `src/trace/playwright-adapter.ts`

In `loadTraceMarkers()`, replace:

```typescript
const markerTimestamp = action.wallTime ?? action.startTime;
```

With:

```typescript
const markerTimestamp = (action.wallTime && action.wallTime > 1e10)
  ? action.wallTime
  : context.wallTime + (action.startTime - context.startTime);
```

The `> 1e10` guard distinguishes real epoch timestamps (~1.7e12 for 2024+) from zero or small monotonic values.

---

## Bug 2: Unconditional File Overwrites

### Root Cause

Both `saveSnapshot()` in `src/index.ts` and `extractSnapshots()` in `src/extractor.ts` call `fs.writeFileSync()` unconditionally. Every run produces new file modification times and (in the `saveSnapshot` path) a new `timestamp` field, creating git diffs even when nothing meaningful changed.

### Fix

Before writing, compare new content against existing files. Skip the write if content is identical.

### Change Detection Logic

Add a shared helper (in a new `src/manifest-utils.ts` or inline):

```typescript
interface ExistingManifest {
  version: number;
  [key: string]: unknown;
}

function readExistingManifest(manifestPath: string): ExistingManifest | null {
  try {
    return JSON.parse(fs.readFileSync(manifestPath, 'utf-8'));
  } catch {
    return null;
  }
}

function hasHtmlChanged(htmlPath: string, newHtml: string): boolean {
  try {
    const existing = fs.readFileSync(htmlPath, 'utf-8');
    return existing !== newHtml;
  } catch {
    return true; // file doesn't exist = changed
  }
}
```

**Decision rule:**
- If `index.html` doesn't exist → first write, version = 1, write everything
- If `index.html` exists and content is identical → skip all writes (html, manifest, screenshot)
- If `index.html` exists and content differs → write new html + screenshot, increment manifest version

### File: `src/extractor.ts`

In `processTraceZips()`, after rendering HTML (~line 144):

1. Check if existing `index.html` matches new HTML
2. If unchanged → skip writing, reuse existing entry info
3. If changed → write files, read existing manifest version, increment it

### File: `src/index.ts`

In `saveSnapshot()`, after generating HTML (~line 40):

1. Check if existing `index.html` matches new HTML
2. If unchanged → skip all writes, return existing file paths
3. If changed → write files, increment version from existing manifest

---

## Bug 3: Static Version Field

### Root Cause

Version is hardcoded to `1` in both paths:
- `src/manifest-generator.ts:19` → `version: 1`
- `src/extractor.ts:160` → `version: 1`

### Fix

When content has changed:
1. Read existing `manifest.json` if present
2. Extract its `version` field
3. Set new version = `existingVersion + 1`
4. If no existing manifest → version = 1

### File: `src/manifest-generator.ts`

Add an optional `previousVersion` parameter:

```typescript
export async function generateManifest(
  page: Page,
  previousVersion?: number,
): Promise<ManifestJson> {
  // ...
  return {
    version: previousVersion ? previousVersion + 1 : 1,
    // ... rest unchanged
  };
}
```

### File: `src/extractor.ts`

In the manifest generation block (~line 158):

```typescript
const existingManifest = readExistingManifest(path.join(snapshotDir, 'manifest.json'));
const manifest = {
  version: existingManifest ? existingManifest.version + 1 : 1,
  // ... rest unchanged
};
```

---

## Files to Modify

| File | Changes |
|------|---------|
| `src/trace/playwright-adapter.ts` | Fix `markerTimestamp` computation using context wallTime offset |
| `src/extractor.ts` | Add change detection, conditional writes, version increment |
| `src/index.ts` | Add change detection, conditional writes, version increment |
| `src/manifest-generator.ts` | Accept optional `previousVersion` parameter |

---

## Verification

1. **Timestamp fix:** Run `npx playwright test` in `test-project/`, inspect `.snapshots/login/initial/manifest.json` — timestamp should show current date, not 1970
2. **Idempotent writes:** Run extraction twice, run `git diff` on `.snapshots/` — no changes on second run
3. **Version increment:** Run extraction, manually edit a test to change page content, run again — manifest version should be `2`
4. **Existing tests pass:** `cd packages/playwright-snapshot-saver && npx playwright test` — all green
5. **Edge case — first run:** Delete `.snapshots/`, run extraction — version should be `1`, all files created
