# Task 13d: UI Tests — Page Object Pattern Refactor

> **Goal:** Refactor the flat Remote Robot fixture layer into a layered structure (`locators/` + `pages/` + `flows/`) so UI tests read as high-level scenarios, XPaths live in exactly one place, and `BaseUiTest` shrinks to pure lifecycle code.
> **Depends on:** Task 13a (unblock), Task 13b (polling helpers), Task 13c (trace bundle — `step()` integration)
> **Output:** New `locators/`, `pages/`, `flows/` packages under `src/uiTest/kotlin/…`; `ToolWindowUiTest` and `SettingsUiTest` rewritten as reference examples; `CLAUDE.md` layering rule documented.

## Motivation

The existing fixtures (`PageMirrorToolWindowFixture`, `SnapshotBrowserFixture`, `PageMirrorSettingsFixture`, `GutterFixture`, `StatusBarFixture`) are already a partial Page Object implementation — they encapsulate component XPaths and expose action methods. But:

- **XPaths are hardcoded inline** in each fixture instead of an extractable locators layer, so updating an IntelliJ chrome selector means touching many files.
- **No composite pages** exist — flows that span multiple fixtures (load snapshot → assert highlight → change settings) are duplicated across tests.
- **`BaseUiTest` has grown to 271 lines** and contains IDE-navigation helpers (`openFileInEditor`, caret movement, etc.) that belong in a Page class, not the base.
- The plugin's own product is a Page Object generator — it's fitting that its test suite be an exemplar of the pattern.

## Key Files

- Existing, to be refactored:
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/BaseUiTest.kt:40-271`
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/PageMirrorToolWindowFixture.kt`
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/SnapshotBrowserFixture.kt`
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/PageMirrorSettingsFixture.kt`
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/GutterFixture.kt`
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/fixtures/StatusBarFixture.kt`
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ToolWindowUiTest.kt`
  - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/SettingsUiTest.kt`
- New (as-shipped: everything lives under the `ui/` sub-package —
  `.../pageobjectplugin/ui/locators/`, `.../ui/pages/`, `.../ui/flows/`,
  and the moved `.../ui/tests/{ToolWindowUiTest,SettingsUiTest}.kt`
  targets):
  ```
  src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/
    locators/
      IntelliJLocators.kt      # IDE chrome XPaths (IdeFrameImpl, IdeStatusBar, ToolWindow headers)
      PageMirrorLocators.kt    # Plugin XPaths (tool window combo, refresh button, settings fields)
    pages/
      PluginToolWindowPage.kt  # Composes tool window + browser + status bar
      PluginSettingsPage.kt    # Settings dialog open → edit → apply
      EditorPage.kt            # File open, caret move, gutter read (moved out of BaseUiTest)
    flows/
      SnapshotLoadFlow.kt      # "open file → wait for auto-discovery → assert highlight"
      SettingsChangeFlow.kt    # "open settings → set value → apply → assert effect"
  ```
- `CLAUDE.md` — add layering rule to the "Common Pitfalls" or a new "UI Test Conventions" section.

## Layering Rule

> **Tests** call **Flows** or **Pages** — never raw fixtures.
> **Flows** orchestrate multiple **Pages**.
> **Pages** compose one or more **Fixtures** and call `StepRecorder.step("...")` for each logical action.
> **Fixtures** contain no literal XPath strings — all locators come from the `locators/` package.

## Steps

1. Extract every literal XPath from the five fixtures into `IntelliJLocators` and `PageMirrorLocators`. Each locator is a `val` returning `Locator` (Remote Robot's type). Group by component with comments.
2. Create the three `pages/` classes. Each page takes a `RemoteRobot` in its constructor, holds references to the fixtures it needs, and exposes high-level methods that wrap fixture calls in `StepRecorder.step("...")` (from Task 13c).
3. Move `openFileInEditor`, caret-movement, gutter-read helpers from `BaseUiTest` into `EditorPage`. `BaseUiTest` should shrink to: `@BeforeAll` lifecycle, `TraceBundleExtension` registration, `RemoteRobot` accessor. Target: **< 100 lines**.
4. Create the two `flows/` classes as thin Kotlin functions/objects that chain page calls.
5. Rewrite `ToolWindowUiTest` and `SettingsUiTest` against the new Page/Flow layer as reference examples. Remaining UI test classes stay on the old API for now — migrate incrementally in follow-up work. Keep fixtures themselves as thin adapters so both styles coexist.
6. Update `CLAUDE.md` with the layering rule above.

## Verification

- `grep -rE "byXpath\(|By\.xpath\(" src/uiTest/kotlin/.../fixtures/` returns only references to constants from `locators/*.kt` (no inline literal XPaths in fixtures).
- `BaseUiTest.kt` is under 100 lines.
- `ToolWindowUiTest` and `SettingsUiTest` contain zero direct fixture instantiations — they only call pages or flows.
- All 30 existing UI scenarios still pass.
- A new reader can follow a test from scenario to page to fixture to locator without hunting.

## Out of Scope

- Migrating all remaining UI test classes (`SnapshotBrowserUiTest`, `GutterUiTest`, etc.) — tracked as follow-up.
- Parameterized tests / shared test-data abstraction.
- Parallel execution / sharding.
