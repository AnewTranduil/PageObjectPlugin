# Task 13b: UI Tests — Reliability (polling, retry, quarantine)

> **Goal:** Eliminate `Thread.sleep`/fixed timeouts, make transient failures retry once with visibility, and allow known-flaky tests to be quarantined without deletion.
> **Depends on:** Task 13a
> **Output:** `support/Wait.kt`, a retry-once JUnit 5 extension, a `@Quarantine` annotation + condition, all fixtures migrated off `Thread.sleep`.

## Motivation

Today's UI tests rely on `Thread.sleep` and fixed-duration `waitFor(30s..60s)` calls scattered across `BaseUiTest` and every fixture. This makes the suite either slow (when durations are padded) or flaky (when they aren't). When a test fails there is no retry, so every transient JCEF/Remote-Robot hiccup scores a red build. Conversely, when a test genuinely rots, the temptation is to delete it — losing coverage silently.

We need three things: (1) a single polling primitive used everywhere, (2) a retry-once mechanism that tags retried runs so retries are *visible*, not hidden, and (3) a quarantine annotation that skips a test but surfaces it in reports so it can't be forgotten.

## Key Files

> **Note:** paths below reflect the pre-Task-13d layout. After the
> layered refactor (Task 13d), UI test infra lives under the `ui/`
> sub-package (e.g. `.../ui/BaseUiTest.kt`, `.../ui/fixtures/*`,
> `.../ui/support/*`). Also: `@Quarantine` was **not** shipped — it is
> tracked only as a reporting field in `ClaudeSummaryModel.kt`.

- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/BaseUiTest.kt` — main offender; multiple `Thread.sleep` and fixed `waitFor` calls.
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/PageMirrorToolWindowFixture.kt`
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/SnapshotBrowserFixture.kt`
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/PageMirrorSettingsFixture.kt`
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/GutterFixture.kt`
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/StatusBarFixture.kt`
- New: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/Wait.kt`
- New: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/RetryOnceExtension.kt`
- ~~New: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/Quarantine.kt`~~ (not shipped)

## Steps

1. **Polling primitives** (`support/Wait.kt`):
   ```kotlin
   fun <T> pollUntil(
       timeout: Duration = 10.seconds,
       interval: Duration = 100.milliseconds,
       message: () -> String = { "condition not met" },
       block: () -> T?
   ): T
   fun retryOnce(block: () -> Unit)
   ```
   `pollUntil` returns the first non-null value from `block` or throws with `message`. `retryOnce` runs `block`, catches any throwable, and re-runs once.
2. Migrate every `Thread.sleep` and fixed `waitFor` across `BaseUiTest` and all five fixtures to `pollUntil`. Record any sleep that *cannot* be migrated (e.g., animation waits) as a TODO with justification.
3. **Retry-once extension** (`support/RetryOnceExtension.kt`): JUnit 5 `TestExecutionExceptionHandler` + `InvocationInterceptor` that reruns a failing test once, reports the second run with a `@Flaky` marker (injected into the test display name so it surfaces in both IntelliJ runner and the Task 14 `claude-summary.md`).
4. **Quarantine** (`support/Quarantine.kt`):
   ```kotlin
   @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
   @ExtendWith(QuarantineCondition::class)
   annotation class Quarantine(val reason: String, val ticket: String = "")
   ```
   `QuarantineCondition` is a JUnit 5 `ExecutionCondition` that disables the test but attaches the reason to the report. Task 14 will surface quarantined tests under a dedicated section in `claude-summary.md`.
5. Register `RetryOnceExtension` via `@ExtendWith` on `BaseUiTest` so all UI tests inherit it.

## Verification

- `grep -r "Thread.sleep" src/uiTest/kotlin/` returns only lines with a TODO comment justifying the exception.
- A deliberately-failing test (throws on first invocation, passes on second) retries once and is reported as `@Flaky`.
- A test annotated `@Quarantine("example", ticket = "ISSUE-1")` is skipped at runtime and appears in the JUnit report with the reason attached.
- All existing UI scenarios still pass after migration.

## Out of Scope

- Per-test trace capture (→ 13c), page-object refactor (→ 13d), parallelization (not in roadmap).
