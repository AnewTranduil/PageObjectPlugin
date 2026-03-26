# UI Tests — Run Status

**Date:** 2026-03-27
**Branch:** `worktree-fix-ui-tests`
**Overall:** 8/30 PASS, 3/30 BLOCKED, 19/30 UNTESTED

## Summary

| Test Class | Scenarios | Status |
|------------|-----------|--------|
| ToolWindowUiTest (UT-01–05) | 5 | PASS |
| SnapshotRenderingUiTest (UT-06–08) | 3 | PASS |
| HighlightBridgeUiTest (UT-09–12) | 4 | BLOCKED |
| ElementPickerUiTest (UT-13–16) | 4 | UNTESTED |
| GutterAnnotationUiTest (UT-17–20) | 4 | UNTESTED |
| SettingsUiTest (UT-21–25) | 5 | UNTESTED |
| StatusBarUiTest (UT-26–28) | 3 | UNTESTED |
| ThemeUiTest (UT-29–30) | 2 | UNTESTED |

---

## Fixes Applied During Test Runs

### Infrastructure (BaseUiTest)

- **Programmatic IDE actions:** Replaced keyboard-shortcut-driven `openFileInEditor()`, `goToLine()`, and `openToolWindow()` with `callJs` that invokes IDE APIs directly (`FileEditorManager`, `ToolWindowManager`, `LogicalPosition`). Keyboard shortcuts failed because the IDE window could be minimized/iconified.
- **Window focus:** Added `bringIdeToFront()` that de-iconifies the IDE frame and calls `toFront()`/`requestFocus()`.
- **Plugin classloader isolation:** Remote Robot's Rhino JS engine runs under the robot-server-plugin classloader, which cannot see plugin classes via `Class.forName()`. All service access uses `PluginManagerCore.getPlugin(pluginId).getPluginClassLoader().loadClass(...)`.
- **Plugin ID:** Actual ID is `com.github.artem.pageobjectplugin` (not `com.example.pagemirror` from CLAUDE.md).

### PageMirrorToolWindowFixture

- `InternalDecorator` → `InternalDecoratorImpl` (2024.3 class rename)
- `@accessiblename='Page Mirror'` → `contains(@accessiblename, 'Page Mirror')` (actual value is `"Page Mirror Tool Window"`)
- `ComboBox` → `JComboBox`
- `selectedSnapshotName()`: Changed to safe null-check JS with `callJs<String>`
- `selectSnapshot()` and `allSnapshotNames()`: Replaced keyboard-driven dropdown with `callJs` model access via plugin classloader

### SnapshotBrowserFixture

- JCEF XPaths: Prioritized `JBCefOsrComponent` (2024.3 class name)
- `layoutElementCount()`: Queries SnapshotService via plugin classloader, reads layout.json with Gson
- `isHighlightVisible()`: Checks `__service.isHighlightActive()` via plugin classloader
- `isInspectModeActive()`: Stub returning `false` (JCEF JS state cannot be queried via `callJs`)

### SnapshotService (plugin source)

- Added `isHighlightActive: Boolean` field with setter in `highlightElement()`/`clearHighlight()`

### Test Classes

- All test `@BeforeEach` methods now call `openToolWindow()` since the tool window doesn't auto-open in the test IDE.

---

## Detailed Test Results

### ToolWindowUiTest — 5/5 PASS

| Test | ID | Result |
|------|----|--------|
| tool window is visible | UT-01 | PASS |
| tool window opens with test project | UT-02 | PASS |
| snapshot combo box lists discovered bundles | UT-03 | PASS |
| selecting a snapshot loads it | UT-04 | PASS |
| refresh button rescans snapshots | UT-05 | PASS |

### SnapshotRenderingUiTest — 3/3 PASS

| Test | ID | Result |
|------|----|--------|
| snapshot renders HTML content | UT-06 | PASS |
| layout json elements are present | UT-07 | PASS |
| file watcher auto reloads on change | UT-08 | PASS |

### HighlightBridgeUiTest — 1/4 PASS, 3 BLOCKED

| Test | ID | Result | Notes |
|------|----|--------|-------|
| caret on getByTestId line triggers highlight | UT-09 | BLOCKED | `isHighlightActive` added to SnapshotService but running IDE has old code |
| caret on non locator line clears highlight | UT-10 | BLOCKED | Same — needs IDE restart |
| alt shift H shortcut triggers highlight | UT-11 | BLOCKED | Same — needs IDE restart |
| all locator types trigger highlight | UT-12 | PASS | (passed on first run before service changes) |

**Blocker:** The IDE must be restarted from the worktree directory to pick up the `isHighlightActive` field in SnapshotService.

---

## Untested Classes — Known Risks

### ElementPickerUiTest (UT-13–16)

- `isInspectModeActive()` is a stub returning `false`. Tests UT-13 and UT-14 (toggle inspect mode) will fail unless inspect mode state can be queried.
- UT-15 (click element inserts locator) depends on JCEF click coordinates mapping to an element — may need coordinate tuning.
- UT-16 (auto-exit after click) depends on `isInspectModeActive()` working.

### GutterAnnotationUiTest (UT-17–20)

- Relies on `GutterFixture.allIconTooltips()` — XPath for gutter icons may need updating for 2024.3.
- Annotator results depend on DaemonCodeAnalyzer completing — timing-sensitive.

### SettingsUiTest (UT-21–25)

- `PageMirrorSettingsFixture.open()` navigates Settings dialog via XPaths — class names (`SearchTextField`, `DialogRootPane`, tree path classes) may differ in 2024.3.
- Settings fields (search depth spinner, color text field, combo box) need correct XPaths.

### StatusBarUiTest (UT-26–28)

- `StatusBarFixture.find()` uses XPath for `IdeStatusBarImpl` — may need class name update.
- Widget text extraction depends on the status bar widget being registered and visible.

### ThemeUiTest (UT-29–30)

- Theme switching via Settings dialog — same XPath risks as SettingsUiTest.
- `callJs` on JCEF `executeJavaScript` is fire-and-forget — theme class check (`document.body.classList.contains('dark')`) returns `false` always. Needs IDE-side state query instead.
