# Manifest timestamp shows epoch-zero date

> **Date:** 2026-04-01
> **Status:** Resolved (Task 11)
> **Affects:** `extractSnapshots()`, reporter
>
> Fixed in `packages/snapshot-core/src/trace/extract.ts` (`buildTraceManifest`):
> the manifest timestamp is now derived from `marker.wallTime` (epoch ms
> reconstructed in
> `packages/playwright-snapshot-saver/src/trace/playwright-backend.ts`
> from `context.wallTime + (action.startTime - context.startTime)`) and
> falls back to `new Date()` only when the trace omits wall-clock data.
> Verified by the regenerated
> `packages/test-project/.snapshots/dashboard/initial/manifest.json`
> carrying real 2026-04 timestamps.

## Symptom

`manifest.json` contains timestamps like `"1970-01-01T00:00:00.632Z"` instead of the actual wall clock time of the snapshot.

## Cause

In `extractor.ts`, the manifest timestamp is derived from `marker.timestamp`:

```typescript
timestamp: new Date(marker.timestamp).toISOString(),
```

`marker.timestamp` is set in `playwright-adapter.ts` as:

```typescript
const markerTimestamp = action.wallTime ?? action.startTime;
```

For `test.step` actions (which is how snapshot markers work), Playwright stores a **monotonic** value in `wallTime` — seconds from trace start (e.g., `0.559`, `0.632`), not epoch milliseconds. `new Date(0.559)` produces `1970-01-01T00:00:00.000Z`.

The `timestamp` field serves double duty:
1. **Snapshot matching** — finding the closest `after@` snapshot by monotonic time (works correctly)
2. **Manifest output** — needs actual wall clock time (broken)

## Fix

Derive wall clock time from the context's epoch anchor:

```typescript
const wallTime = context.wallTime + (action.startTime - context.startTime);
```

`context.wallTime` is the trace start time as epoch ms. Adding the monotonic offset gives the correct wall clock time for the action.

Store `wallTime` as a separate field on `TraceSnapshotMarker` so `timestamp` stays monotonic for snapshot matching, and `wallTime` is used for the manifest.

In `extractor.ts`:

```typescript
timestamp: new Date(marker.wallTime).toISOString(),
```
