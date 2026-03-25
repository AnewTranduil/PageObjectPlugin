# UI Tests — Resolution Report

**Date:** 2026-03-25
**Status:** RESOLVED — all three blockers from `diagnostic-report.md` are fixed

## Fixes Applied

### Blocker 1 (P0): `splitMode` property not configured

**File:** `build.gradle.kts:113-114`

IPG 2.13.1 requires `splitMode` and `splitModeTarget` on every `RunIdeTask`. The built-in `runIde` task gets these automatically, but our custom `runIdeForUiTests` did not.

**Change:** Added both properties inside the task registration block:

```kotlin
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget

register<RunIdeTask>("runIdeForUiTests") {
    splitMode.set(false)
    splitModeTarget.set(SplitModeTarget.BACKEND)
    // ...
}
```

---

### Blocker 2 (P0): Configuration cache incompatibility

**File:** `build.gradle.kts:111, 128-136`

Two changes were needed:

1. **Marked the task as config-cache-incompatible** so Gradle doesn't fail the build:

```kotlin
notCompatibleWithConfigurationCache("dynamic plugin path resolution in doFirst")
```

2. **Moved `layout.buildDirectory` resolution out of `doFirst`** into a configuration-time `Provider`, preventing serialization of Gradle script objects:

```kotlin
// Before (broken — captures layout.buildDirectory in doFirst closure)
doFirst {
    val pluginBase = layout.buildDirectory.dir("robot-server-plugin").get().asFile
    val pluginDir = pluginBase.listFiles()?.firstOrNull { it.isDirectory } ?: pluginBase
    jvmArgs("-Dplugin.path=${pluginDir.absolutePath}")
}

// After (fixed — resolved at configuration time via Provider)
val robotPluginDir = layout.buildDirectory.dir("robot-server-plugin").map { dir ->
    val base = dir.asFile
    base.listFiles()?.firstOrNull { it.isDirectory } ?: base
}
doFirst {
    jvmArgs("-Dplugin.path=${robotPluginDir.get().absolutePath}")
}
```

---

### Issue 3 (P1): No fast-fail when IDE is not running

**File:** `src/uiTest/kotlin/.../BaseUiTest.kt:44-67`

Previously, each test class would block for its full 2-minute `@BeforeAll` timeout when the robot server was unreachable (8 classes = ~16 minutes of hanging).

**Change:** Added `ensureRobotServerReachable()` as the first step of `waitForIde()`. It pings the robot server URL via `HttpURLConnection` up to 5 times with 2-second intervals (~10 seconds total). If all attempts fail, the test class is aborted immediately via `Assumptions.abort()` with a clear message:

```
Robot server not reachable at http://localhost:8082 after 5 attempts.
Is ./gradlew runIdeForUiTests running?
```

Using `Assumptions.abort()` marks tests as **skipped** (not failed), which correctly signals that the precondition was not met rather than the test logic being broken.

---

## Verification

| Check | Result |
|-------|--------|
| `./gradlew compileKotlin` | PASS |
| `./gradlew compileUiTestKotlin` | PASS |
| `./gradlew help --task runIdeForUiTests` | PASS — task recognized |
| Configuration cache | Stored successfully |

## Remaining Work

- **CI/CD pipeline** is not yet configured (manual two-terminal execution only)
- **End-to-end validation** requires running `./gradlew runIdeForUiTests` + `./gradlew uiTest` with a display available (not possible in headless-only environments without Xvfb)
