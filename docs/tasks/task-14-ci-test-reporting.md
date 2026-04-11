# Task 14: CI Test Reporting + Claude Inner Loop

> **Goal:** Single Gradle aggregator task that runs unit + UI + npm tests and emits a machine-readable JSON and human-readable Markdown summary optimized for Claude Code to autonomously iterate (run → read → fix → re-run).
> **Depends on:** Task 13a–c (UI tests runnable + trace bundles exist)
> **Output:** `./gradlew testReport`, `build/reports/claude-summary.{json,md}`, CI artifact upload, `CLAUDE.md` Test Loop section.

## Motivation

Today's CI produces scattered reports (JUnit XML under `build/test-results/`, HTML under `build/reports/tests/`, npm test output in package directories). For Claude Code to iterate on a failure, it must know *where* to look, *what* failed, and *why* — without paging through HTML or digging per package. A single canonical summary file per build is the fastest path.

We also need guardrails: when Claude encounters a failing test, the temptation to delete/skip it must be blocked by explicit process rules. `CLAUDE.md` is the right place.

## Key Files

- `build.gradle.kts` — new `testReport` aggregator task.
- `.github/workflows/ci.yml` — upload both summary files as artifacts.
- `CLAUDE.md` — new "Test Loop" section.
- New: `buildSrc/src/main/kotlin/ClaudeSummary.kt` (or equivalent) — aggregator logic.

## `claude-summary.json` Schema

```json
{
  "version": 1,
  "generatedAt": "2026-04-07T12:00:00Z",
  "totals": { "passed": 123, "failed": 4, "skipped": 2, "flaky": 1, "quarantined": 3, "durationMs": 456789 },
  "suites": [
    {
      "suite": "unit | uiTest | npm:playwright-snapshot-saver | npm:snapshot-core",
      "tests": [
        {
          "name": "ClassName.testName",
          "status": "passed | failed | skipped | flaky | quarantined",
          "durationMs": 123,
          "file": "src/test/.../Foo.kt",
          "line": 42,
          "failureMessage": "...",
          "tracePath": "build/reports/uiTest/traces/ClassName__testName/"
        }
      ]
    }
  ],
  "quarantined": [{ "name": "...", "reason": "...", "ticket": "..." }]
}
```

## `claude-summary.md` Layout

```
# Test Summary — <commit sha>

Totals: 123 passed, 4 failed, 2 skipped, 1 flaky, 3 quarantined (7m 36s)

## Failures (4)
1. `com.example.FooTest.bar` — NullPointerException at Foo.kt:42
   Trace: build/reports/uiTest/traces/FooTest__bar/
2. ...

## Flaky (retried once) (1)
- `com.example.BazTest.qux` — passed on retry

## Quarantined (3)
- `com.example.XyzTest.old` — reason: "CEF race", ticket: ISSUE-123
```

## Steps

1. **Aggregator task** (`testReport`) that depends on `test`, `uiTest`, and each npm package's `npmTest` equivalent. After all complete (even with failures — use `finalizedBy`), parse:
   - JUnit XML from `build/test-results/test/**/*.xml` and `build/test-results/uiTest/**/*.xml`.
   - Each npm package's JSON reporter output (jest/vitest/playwright `--reporter=json` already used by existing packages).
   - `@Quarantine` metadata emitted by Task 13b.
   - Trace bundle paths from Task 13c.
2. Emit `build/reports/claude-summary.json` and `build/reports/claude-summary.md` with the schemas above.
3. **CI wiring** — update `.github/workflows/ci.yml` to:
   - Run `./gradlew testReport` (non-failing even if tests fail, so the artifact uploads).
   - Upload `build/reports/claude-summary.*` and `build/reports/uiTest/traces/**` as a single artifact named `test-report-<sha>`.
   - Fail the job after upload if `totals.failed > 0`.
4. **`CLAUDE.md` "Test Loop" section**:
   - `./gradlew testReport` → read `build/reports/claude-summary.md` → fix → re-run.
   - **Redlines** (non-negotiable):
     - Never delete a failing test to turn CI green.
     - Never add `@Ignore` / `@Disabled` without a ticket and `@Quarantine(reason, ticket)` — bare skips are banned.
     - Never mark a test `@Quarantine` without also opening a tracking issue.
     - Always re-run the full `testReport` after a fix; never cherry-pick a single test to claim green.
     - Never use `node -e` or inline scripts for verification — add a real test.

## Verification

- `./gradlew testReport` produces both files; running locally with an intentionally failing test shows the failure in the Markdown under `## Failures`.
- `claude-summary.json` parses as valid JSON matching the schema.
- CI job uploads `test-report-<sha>` artifact; downloading it locally yields browsable traces.
- A fresh Claude Code session given only `claude-summary.md` + a repo checkout can identify which file and line to fix.
- `CLAUDE.md` contains the Test Loop section with all redlines verbatim.

## Out of Scope

- Parallel CI shards.
- Historical test metrics (flakiness dashboards).
- Demo / trace viewer (→ Task 19).
