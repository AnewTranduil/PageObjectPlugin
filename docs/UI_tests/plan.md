# Page Mirror — UI Test Development Plan

> **Historical planning doc — captured before implementation.** The UI
> test suite has shipped (Tasks 13a–13d, 19). Some low-level details
> here have drifted from the shipped code:
> - `runIdeForUiTests` is registered via the
>   `intellijPlatformTesting.runIde.register("runIdeForUiTests")` DSL,
>   **not** a `tasks.register<RunIdeTask>` block. See
>   [`build.gradle.kts:112-144`](../../build.gradle.kts) for the current
>   wiring.
> - The `layout.json` sidecar was removed in Task 15 (v2 bundle format);
>   bundles now contain `index.html + manifest.json + resources/`. Tests
>   no longer read a layout file.
> - The `tests/` directory has grown to 11 classes — the file checklist
>   below covers the original 8; `DashboardV2UiTest`, `DemoSmokeUiTest`,
>   and `OutdatedBundleBannerUiTest` were added later.
> - Layered Page Object structure (`ui/{fixtures,locators,pages,flows,tests}/`)
>   is authoritative — see `docs/tasks/task-13d-ui-tests-page-object-refactor.md`
>   and `CLAUDE.md` "UI Test Conventions".
>
> Retained here for the scenario numbering (UT-01 … UT-30) that other
> docs reference. Please refresh section 2 (Build Integration) and
> section 8 (File Checklist) against the current tree before treating
> them as authoritative.

## Overview

This document defines the plan for developing UI (end-to-end) tests for the Page Mirror plugin using the JetBrains **intellij-ui-test-robot** framework (Remote Robot). These tests drive a real IDE instance, unlike the existing `BasePlatformTestCase` integration tests which run headlessly without a visible UI.

---

## 1. Framework: intellij-ui-test-robot

**Library:** `com.intellij.remoterobot:remote-robot` (JetBrains, open-source)

The Remote Robot approach:
- A lightweight server (`robot-server-plugin`) is installed into a sandboxed IDE instance.
- A Kotlin/JUnit5 test client connects to the running IDE over HTTP.
- Tests find and interact with real Swing components and JCEF views.

**Why this framework:**
- Official JetBrains tooling; maintained alongside IntelliJ Platform.
- Works with JCEF (can execute JS in the JCEF browser component).
- Supports headless (CI) and headed (local) modes.
- Compatible with Gradle Plugin 2.x via `runIdeForUiTests` task.

**Alternatives considered:**
| Option | Reason rejected |
|--------|-----------------|
| Selenium/Playwright | Cannot drive Swing UI |
| JUnit + Robot AWT | Fragile; no IDE-specific locators |
| Manual testing | Not automatable in CI |

---

## 2. Build Integration

### Gradle Dependencies

```kotlin
// build.gradle.kts additions
dependencies {
    // UI test client
    testImplementation("com.intellij.remoterobot:remote-robot:0.11.22")
    testImplementation("com.intellij.remoterobot:remote-fixtures:0.11.22")

    // REST client for robot server
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}
```

### Gradle Tasks

```kotlin
// Add robot-server plugin to the sandboxed IDE
intellij {
    plugins.add("com.intellij.remoterobot:robot-server-plugin:0.11.22")
}

tasks.register<RunIdeTask>("runIdeForUiTests") {
    systemProperty("robot-server.port", "8082")
    systemProperty("ide.mac.message.dialogs.as.sheets", "false")
    systemProperty("jb.privacy.policy.text", "<!--999.999-->")
    systemProperty("jb.consents.confirmation.enabled", "false")
    jvmArgs("-Xmx2g", "-Didea.trust.all.projects=true")
}

tasks.register<Test>("uiTests") {
    dependsOn("runIdeForUiTests")
    useJUnitPlatform()
    systemProperty("robot-server.url", "http://localhost:8082")
    testClassesDirs = sourceSets["uiTest"].output.classesDirs
    classpath = sourceSets["uiTest"].runtimeClasspath
}
```

### Source Set

```
src/
  uiTest/
    kotlin/com/github/artem/pageobjectplugin/ui/
      fixtures/        # Reusable UI fixture classes
      tests/           # Test classes by feature
    resources/
      uiTestData/      # Snapshot bundles for UI tests
```

UI tests live in a separate `uiTest` source set to keep them isolated from unit/integration tests and control when they run.

---

## 3. Test Infrastructure

### Base Class

```kotlin
// BaseUiTest.kt
abstract class BaseUiTest {
    val robot: RemoteRobot = RemoteRobot("http://localhost:8082")

    @BeforeEach
    fun openTestProject() {
        // Open the test-project/ directory via Welcome screen or File > Open
    }

    @AfterEach
    fun closeProject() {
        // Close via File > Close Project
    }
}
```

### Page Fixture Classes

Each major UI surface gets a fixture wrapping the RemoteRobot component locators:

| Fixture | Wraps |
|---------|-------|
| `PageMirrorToolWindowFixture` | Tool Window panel, combo box, toolbar |
| `SnapshotBrowserFixture` | JCEF component inside tool window |
| `PageMirrorSettingsFixture` | Settings > Tools > Page Mirror dialog |
| `GutterFixture` | Editor gutter annotations |
| `StatusBarFixture` | Page Mirror status bar widget |

### Shared Test Snapshot Data

`src/uiTest/resources/uiTestData/snapshots/` mirrors the structure from `src/test/resources/testdata/`. At minimum it contains:
- `login/initial/` — the existing login snapshot (8 elements)
- `login/error-state/` — snapshot with validation error state

---

## 4. Test Scenarios

Tests are grouped by feature area. Each scenario lists its **precondition**, **actions**, and **assertions**.

---

### 4.1 Tool Window — Visibility and Structure

**UT-01: Tool window opens on View > Tool Windows**
- Pre: IDE open, no project
- Action: Click View > Tool Windows > Page Mirror
- Assert: Tool window panel is visible; combo box shows "No snapshot loaded"; browser area shows placeholder text

**UT-02: Tool window opens with test project**
- Pre: test-project open, `.snapshots/login/initial/` present
- Action: Open a `.ts` file
- Assert: Combo box auto-populates with "login / initial"; snapshot renders in JCEF iframe

**UT-03: Snapshot combo box lists all discovered bundles**
- Pre: test-project open with two snapshot directories
- Action: Click combo box dropdown
- Assert: Both "login / initial" and "login / error-state" appear in the list

**UT-04: Selecting a snapshot from combo box loads it**
- Pre: Two snapshots discovered
- Action: Select "login / error-state" from combo box
- Assert: JCEF iframe content updates (different HTML visible)

**UT-05: Refresh button re-scans for snapshots**
- Pre: Tool window open, no snapshots found initially
- Action: Add a snapshot directory on disk, click Refresh button
- Assert: Combo box now lists the new snapshot

---

### 4.2 Snapshot Loading

**UT-06: Snapshot renders HTML content inside iframe**
- Pre: Snapshot loaded
- Action: (observe)
- Assert: JCEF component is non-empty; executing `document.querySelector('iframe')` via JS returns a non-null result

**UT-07: Layout.json elements are present in rendered output**
- Pre: Login snapshot loaded (8 elements in layout.json)
- Action: Execute JS `window.__layoutData.elements.length` in JCEF
- Assert: Returns 8

**UT-08: File watcher auto-reloads on snapshot change**
- Pre: Snapshot loaded; `autoReloadOnChange = true`
- Action: Overwrite `index.html` on disk with modified content
- Assert: Within 1 second, JCEF content reflects the new HTML

---

### 4.3 Code-to-UI Highlight Bridge

**UT-09: Moving caret to locator line highlights element in JCEF**
- Pre: Login snapshot loaded; `login.page.ts` open in editor
- Action: Move caret to line containing `page.locator('#username')`
- Assert: After ≤200 ms debounce, executing `document.querySelector('.pm-highlight')` in JCEF returns non-null

**UT-10: Caret on non-locator line clears highlight**
- Pre: Highlight active from UT-09
- Action: Move caret to a blank line
- Assert: `document.querySelector('.pm-highlight')` returns null

**UT-11: Alt+Shift+H shortcut highlights current locator**
- Pre: Caret on locator line
- Action: Press Alt+Shift+H
- Assert: Highlight overlay appears in JCEF

**UT-12: All five locator types trigger highlight**
- Pre: Login snapshot loaded
- Action: Move caret to each of: `locator()`, `getByTestId()`, `getByRole()`, `getByText()`, `getByPlaceholder()` lines
- Assert: Each triggers a non-null highlight in JCEF

---

### 4.4 Element Picker (Inspect Mode)

**UT-13: Alt+Shift+I toggles inspect mode on**
- Pre: Snapshot loaded
- Action: Press Alt+Shift+I (or click toolbar toggle)
- Assert: Green hover boxes are visible when JS `window.__inspectMode` queried in JCEF returns `true`

**UT-14: Alt+Shift+I again toggles inspect mode off**
- Pre: Inspect mode active
- Action: Press Alt+Shift+I
- Assert: `window.__inspectMode` returns `false`

**UT-15: Clicking an element in JCEF inserts locator into editor**
- Pre: Inspect mode active; cursor in a `.ts` file at an insertion point
- Action: Click on the username input element inside JCEF
- Assert: A Playwright locator string (e.g., `page.getByTestId('login-username')`) is inserted at the caret position

**UT-16: Inspect mode auto-exits after element click**
- Pre: Inspect mode active
- Action: Click an element
- Assert: `window.__inspectMode` returns `false` after insertion

---

### 4.5 Gutter Validation Annotations

**UT-17: Gutter badge shows "1" for a matched selector**
- Pre: Login snapshot loaded; `login.page.ts` open; line contains `locator('#username')`
- Action: Wait for annotator pass (DaemonCodeAnalyzer)
- Assert: Gutter icon on that line has tooltip containing "1 match"

**UT-18: Gutter badge shows "0" for unmatched selector**
- Pre: Login snapshot loaded
- Action: Open file with `locator('#nonexistent-id')`
- Assert: Gutter icon has tooltip "0 matches"

**UT-19: Gutter badge shows "2+" for multiple matches**
- Pre: Snapshot with two elements sharing class `.form-input`
- Action: Open file with `locator('.form-input')`
- Assert: Gutter icon has tooltip "2 matches" (or "N matches")

**UT-20: No gutter badges in non-.ts files**
- Pre: Login snapshot loaded
- Action: Open a `.kt` file in editor
- Assert: No Page Mirror gutter icons appear

---

### 4.6 Settings Dialog

**UT-21: Settings dialog opens via File > Settings > Tools > Page Mirror**
- Pre: IDE open
- Action: Navigate to Settings > Tools > Page Mirror
- Assert: Settings panel visible with "Snapshot Search Depth", "Auto Reload on Change", "Highlight Color", "Code Generation Style" fields

**UT-22: Changing search depth persists after restart**
- Pre: Settings dialog open
- Action: Change "Snapshot Search Depth" to 5, click OK
- Assert: After reopening settings, value is still 5

**UT-23: Changing highlight color updates JCEF immediately**
- Pre: Snapshot loaded; settings dialog open
- Action: Change highlight color to `#FF0000` (red), click Apply
- Assert: `window.__highlightColor` in JCEF returns `#FF0000`

**UT-24: Code generation style "Variable" inserts `const` declaration**
- Pre: Code gen style = "Variable"; inspect mode active
- Action: Click an element in JCEF
- Assert: Inserted code starts with `const`

**UT-25: Code generation style "Property" inserts property declaration**
- Pre: Code gen style = "Property"; inspect mode active
- Action: Click an element
- Assert: Inserted code is a class property (no `const`)

---

### 4.7 Status Bar Widget

**UT-26: Status bar shows "No snapshot" when none loaded**
- Pre: IDE open, no snapshot loaded
- Action: Observe status bar
- Assert: Widget text matches `Page Mirror: No snapshot`

**UT-27: Status bar shows snapshot name and element count when loaded**
- Pre: Login snapshot loaded (8 elements)
- Action: Observe status bar
- Assert: Widget text matches `Page Mirror: initial (8 elements)`

**UT-28: Clicking status bar widget focuses Tool Window**
- Pre: Tool Window hidden; snapshot loaded
- Action: Click the Page Mirror status bar widget
- Assert: Tool Window becomes visible and focused

---

### 4.8 Theme Support

**UT-29: JCEF switches to dark class on dark IDE theme**
- Pre: IDE theme set to Darcula/Dark
- Action: Load a snapshot
- Assert: Executing `document.body.classList.contains('dark')` in JCEF returns `true`

**UT-30: JCEF switches to light class on light IDE theme**
- Pre: IDE theme set to IntelliJ Light
- Action: Load a snapshot
- Assert: `document.body.classList.contains('dark')` returns `false`

---

## 5. Test Execution Phases

### Phase 1 — Infrastructure Setup (Prerequisite)

- [ ] Add `uiTest` source set to `build.gradle.kts`
- [ ] Add Remote Robot dependencies
- [ ] Configure `runIdeForUiTests` Gradle task
- [ ] Write `BaseUiTest` and fixture classes
- [ ] Copy snapshot test data to `src/uiTest/resources/uiTestData/`
- [ ] Verify robot server connects and can find IDE components

### Phase 2 — Core Visual Tests (High Priority)

Covers the primary value: snapshot renders and the highlight bridge works.

- [ ] UT-01, UT-02, UT-03, UT-04 (Tool Window structure)
- [ ] UT-06, UT-07 (Snapshot renders correctly in JCEF)
- [ ] UT-09, UT-10, UT-12 (Caret → highlight bridge)
- [ ] UT-26, UT-27 (Status bar accuracy)

### Phase 3 — Interactive Feature Tests (Medium Priority)

- [ ] UT-13, UT-14, UT-15, UT-16 (Element picker)
- [ ] UT-17, UT-18, UT-20 (Gutter validation)
- [ ] UT-11 (Keyboard shortcut)
- [ ] UT-28 (Status bar click)

### Phase 4 — Settings and Edge Cases (Lower Priority)

- [ ] UT-21 through UT-25 (Settings dialog)
- [ ] UT-05, UT-08 (Refresh and file watcher)
- [ ] UT-19 (Multi-match gutter badge)
- [ ] UT-29, UT-30 (Theme switching)

---

## 6. CI Integration

```yaml
# .github/workflows/ui-tests.yml (sketch)
ui-tests:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: actions/setup-java@v4
      with: { java-version: '21' }
    - name: Start IDE for UI tests
      run: ./gradlew runIdeForUiTests &
    - name: Wait for IDE to be ready
      run: ./gradlew waitForIde   # custom task polling robot-server
    - name: Run UI tests
      run: ./gradlew uiTests
    - name: Upload screenshots on failure
      if: failure()
      uses: actions/upload-artifact@v4
      with:
        path: build/reports/ui-tests/screenshots/
```

**Notes:**
- Requires a virtual display on Linux CI (`Xvfb`).
- JCEF requires additional flags in CI: `-Djcef.startup.timeout=30000`, `--no-sandbox` Chrome flags.
- Screenshot capture on failure should be built into `BaseUiTest.@AfterEach`.

---

## 7. Out of Scope

The following are intentionally excluded from UI tests (covered by existing `BasePlatformTestCase` integration tests):

- Regex correctness for locator extraction
- JSON parsing in `PickerResultHandler`
- Settings persistence logic
- `SnapshotBundle.fromDirectory()` validation
- Annotator match-count logic

UI tests focus on what can only be verified with a running IDE: rendering, keyboard interactions, JCEF content, and cross-component coordination.

---

## 8. File Checklist

```
docs/UI_tests/
  plan.md                     ← this document

src/uiTest/
  kotlin/com/github/artem/pageobjectplugin/ui/
    BaseUiTest.kt
    fixtures/
      PageMirrorToolWindowFixture.kt
      SnapshotBrowserFixture.kt
      PageMirrorSettingsFixture.kt
      GutterFixture.kt
      StatusBarFixture.kt
    tests/
      ToolWindowUiTest.kt          # UT-01 to UT-05
      SnapshotRenderingUiTest.kt   # UT-06 to UT-08
      HighlightBridgeUiTest.kt     # UT-09 to UT-12
      ElementPickerUiTest.kt       # UT-13 to UT-16
      GutterAnnotationUiTest.kt    # UT-17 to UT-20
      SettingsUiTest.kt            # UT-21 to UT-25
      StatusBarUiTest.kt           # UT-26 to UT-28
      ThemeUiTest.kt               # UT-29 to UT-30
  resources/
    uiTestData/
      snapshots/
        login/
          initial/    {index.html, layout.json, manifest.json}
          error-state/ {index.html, layout.json, manifest.json}
```
