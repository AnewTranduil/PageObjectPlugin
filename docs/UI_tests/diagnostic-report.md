# UI Tests Diagnostic Report

**Date:** 2025-03-25
**Status:** RESOLVED — see `docs/UI_tests/resolution-report.md` and
`docs/tasks/task-13a-ui-tests-unblock.md`. UI tests run in CI today via
the `intellijPlatformTesting.runIde.register("runIdeForUiTests")` DSL
at `build.gradle.kts:112-144`; retained for historical reference.

**Original status:** BLOCKED — tests cannot run

## Overview

The project has 30 UI test scenarios across 8 test classes using JetBrains Remote Robot 0.11.22.
UI tests require a two-terminal execution model:

```
Terminal 1: ./gradlew runIdeForUiTests   # launches IDE with robot-server on port 8082
Terminal 2: ./gradlew uiTest             # connects to IDE and runs tests
```

## What Works

| Component | Status | Notes |
|-----------|--------|-------|
| UI test compilation | PASS | `./gradlew compileUiTestKotlin` succeeds cleanly |
| Robot plugin extraction | PASS | `extractRobotPlugin` produces valid plugin structure in `build/robot-server-plugin/` |
| Unit/integration tests | PASS | `./gradlew test` passes |
| Standard `runIde` | PASS | Built-in task dry-runs successfully |

## What Fails

### Blocker 1: `splitMode` property not configured on `runIdeForUiTests`

**Error:**
```
Task ':runIdeForUiTests' (type 'RunIdeTask')
  - property 'splitMode' doesn't have a configured value
  - property 'splitModeTarget' doesn't have a configured value
  - property 'argumentProviders.$0.splitMode' doesn't have a configured value
```

**Cause:** IntelliJ Platform Gradle Plugin 2.13.1 added `splitMode` as a required property on `RunIdeTask`. The auto-generated `runIde` task gets this configured automatically by the plugin, but the custom `register<RunIdeTask>("runIdeForUiTests")` in `build.gradle.kts:108` does not inherit this auto-configuration.

**Fix:** Add to the `runIdeForUiTests` task registration:
```kotlin
splitMode.set(false)
splitModeTarget.set(
    org.jetbrains.intellij.platform.gradle.Constants.SplitModeAware.SplitModeTarget.BACKEND
)
```

### Blocker 2: Configuration cache incompatibility

**Error:**
```
Task ':runIdeForUiTests' — cannot serialize Gradle script object references
as these are not supported with the configuration cache.
```

**Cause:** `gradle.properties` enables `org.gradle.configuration-cache = true`. The `RunIdeTask` from IPG 2.x is not configuration-cache-compatible. Additionally, the `doFirst` block at `build.gradle.kts:123` captures Gradle script objects (`layout.buildDirectory`) which cannot be serialized.

**Fix options:**
1. Mark the task: `notCompatibleWithConfigurationCache("dynamic plugin path")`
2. Or move plugin path resolution out of `doFirst` into configuration-time resolution

### Issue 3: No fast-fail when IDE is not running

When `./gradlew uiTest` runs without the IDE, each test class blocks for its full 2-minute `@BeforeAll` timeout in `BaseUiTest.waitForIde()`. With 8 test classes, this means ~16 minutes of hanging before all tests report failure.

**Fix:** Add an HTTP health check to `BaseUiTest` that pings `http://localhost:8082` before attempting component lookups, and fails immediately with a descriptive message if the robot server is unreachable.

## Existing Test Result

Only one test result XML exists from a prior run:

**File:** `build/test-results/uiTest/TEST-...ElementPickerUiTest.xml`
**Error:** `WaitForConditionTimeoutException: Exceeded timeout (PT2M) for condition function`
**Stack:** `BaseUiTest.waitForIde() → waitFor() → ideFrame() != null`

This confirms the IDE was not reachable when the test was attempted.

## Action Items

| Priority | Fix | File | Line |
|----------|-----|------|------|
| P0 | Set `splitMode` and `splitModeTarget` on `runIdeForUiTests` | `build.gradle.kts` | 108 |
| P0 | Handle configuration cache incompatibility for `runIdeForUiTests` | `build.gradle.kts` | 108 |
| P1 | Add fast-fail HTTP health check before 2-min timeout | `src/uiTest/.../BaseUiTest.kt` | 32 |

## Test Infrastructure Summary

- **Framework:** Remote Robot 0.11.22 + JUnit Jupiter 5.10.1
- **Test classes:** 8 (ToolWindow, SnapshotRendering, HighlightBridge, ElementPicker, GutterAnnotation, Settings, StatusBar, Theme)
- **Test scenarios:** 30 (UT-01 through UT-30)
- **Fixtures:** 5 custom (PageMirrorToolWindow, SnapshotBrowser, Gutter, PageMirrorSettings, StatusBar)
- **Test data:** `src/uiTest/resources/uiTestData/snapshots/login/initial/`
- **CI/CD:** Not configured (manual execution only)
