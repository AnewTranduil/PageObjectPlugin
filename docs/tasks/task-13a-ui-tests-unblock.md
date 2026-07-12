# Task 13a: UI Tests — Unblock Runner

> **Status:** DONE — resolved via the newer
> `intellijPlatformTesting.runIde.register("runIdeForUiTests")` DSL,
> which sidesteps the `splitMode` / config-cache issues entirely rather
> than working around them on a `RunIdeTask` registration. See
> `build.gradle.kts:112-144` for the current wiring.
>
> **Goal:** Restore the ability to run `./gradlew runIdeForUiTests` so the existing 30 UI scenarios execute again.
> **Depends on:** nothing (prerequisite for 13b–d, 14, 19)
> **Output:** Updated `build.gradle.kts` and `BaseUiTest.kt`; all existing UI scenarios runnable.

## Motivation

The UI test suite has been non-runnable since the bump to IntelliJ Platform Gradle plugin 2.13.1. `./gradlew runIdeForUiTests` fails immediately because the custom `RunIdeTask` registration is missing the `splitMode` property (now required by IPG 2.x), and the task is not configuration-cache compatible. On top of that, when the IDE *does* fail to start, every test class blocks for the full 2-minute `BaseUiTest.waitForIde()` timeout before failing — burning ~16 minutes to confirm nothing works. Details in `docs/UI_tests/diagnostic-report.md`.

Until this is fixed, no further UI-test work (13b–d), CI reporting (14), or demo trace viewer (19) can proceed.

## Key Files

- `build.gradle.kts:112-144` — `runIdeForUiTests` task registration
  (now via `intellijPlatformTesting.runIde.register("runIdeForUiTests")`).
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/BaseUiTest.kt` — `waitForIde()` health check.
- `docs/UI_tests/diagnostic-report.md` — full symptom trace and original fix outline.
- `docs/UI_tests/resolution-report.md` — the interim `register<RunIdeTask>` fix that was later replaced by the DSL above.

## Steps

1. In `build.gradle.kts`, on the `runIdeForUiTests` task:
   - Call `splitMode.set(false)` (IPG 2.13.1 requirement).
   - Configure `splitModeTarget` to the matching value used in default `runIde`.
   - Mark the task `@DisableCachingByDefault` or otherwise opt out of the configuration cache until IPG ships a compatible version.
2. In `BaseUiTest.waitForIde()`:
   - Replace the 2-minute blind `Thread.sleep` / fixed `waitFor` with a fast health-check loop that `HEAD`s the Remote Robot endpoint (`http://localhost:8082`) every 500 ms.
   - Abort with a clear error after **30 seconds** of unreachable endpoint.
   - Keep the existing window-focus / VFS-refresh / tool-window-setup logic after the health check passes.
3. No changes to individual fixtures or tests.

## Verification

- `./gradlew runIdeForUiTests` launches an IDE process that becomes reachable on port 8082.
- Running the UI test entry point connects within 30 seconds when the IDE is healthy.
- Killing the IDE mid-run causes `BaseUiTest` to fail within 30 seconds (not 2 minutes).
- All 30 existing UI scenarios execute (pass/fail independent of this task).

## Out of Scope

- New polling helpers (→ 13b), trace capture (→ 13c), page-object refactor (→ 13d), CI integration (→ 14).
