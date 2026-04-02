# Task 12: Settings Panel — Migrate to Kotlin UI DSL v2

> **Goal:** Replace the manual Swing layout in the settings panel with IntelliJ's Kotlin UI DSL v2 for proper label alignment, consistent spacing, and theme integration.
> **Depends on:** Task 7
> **Output:** Rewritten `PageMirrorConfigurable.kt` using `com.intellij.ui.dsl.builder.panel`

## Motivation

The current settings panel (`Settings > Tools > Page Mirror`) is built with raw Swing: a `BoxLayout.Y_AXIS` root panel and `FlowLayout.LEFT` rows created by a manual `row()` helper. This produces:

- **Misaligned labels** — each row sizes its label independently, so input fields are jagged
- **Inconsistent checkbox padding** — the auto-reload checkbox is added directly to the BoxLayout, offset from the labeled rows
- **No vertical pinning** — components stretch to fill the entire settings panel height instead of anchoring to the top
- **Non-standard appearance** — doesn't match the form grid used by other IntelliJ settings pages

IntelliJ's Kotlin UI DSL v2 (`com.intellij.ui.dsl.builder`) solves all of these. It provides:

- Automatic label-column alignment across all rows
- Consistent spacing and theme-aware rendering
- Built-in `DialogPanel` with automatic `isModified()` / `apply()` / `reset()` via property bindings

The project targets platform 2024.3+ (currently 2025.1.3), so UI DSL v2 is fully available.

---

## Current Implementation

**File:** `src/main/kotlin/com/github/artem/pageobjectplugin/settings/PageMirrorConfigurable.kt`

```kotlin
val panel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(row("Snapshot search depth:", searchDepthSpinner!!))
    add(autoReloadCheckbox!!)
    add(row("Highlight color:", highlightColorField!!))
    add(row("Code generation style:", codeGenStyleCombo!!))
}

private fun row(label: String, component: JComponent): JPanel {
    return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JLabel(label))
        add(component)
    }
}
```

Manual `isModified()`, `apply()`, `reset()` methods compare/copy each field individually.

---

## Target Implementation

Replace `createComponent()` with a `panel {}` DSL block returning a `DialogPanel`. Bind each setting to `PageMirrorSettings.State` properties. Delegate `isModified()`, `apply()`, `reset()` to the `DialogPanel`.

```kotlin
private lateinit var panel: DialogPanel

override fun createComponent(): JComponent {
    val settings = PageMirrorSettings.getInstance(project)
    panel = panel {
        row("Snapshot search depth:") {
            spinner(1..10, 1)
                .bindIntValue(settings.state::snapshotSearchDepth)
        }
        row {
            checkBox("Auto-reload on file change")
                .bindSelected(settings.state::autoReloadOnChange)
        }
        row("Highlight color:") {
            textField()
                .bindText(settings.state::highlightColor)
        }
        row("Code generation style:") {
            comboBox(listOf("Property", "Variable"))
                .bindItem(settings.state::codeGenStyle.toNullableProperty())
        }
    }
    return panel
}

override fun isModified() = panel.isModified()
override fun apply() { panel.apply() }
override fun reset() { panel.reset() }
```

> **Note:** The exact binding API may need adjustment based on the platform version's DSL surface. The above is the target pattern — verify against the 2025.1 SDK.

---

## Files to Modify

| File | Changes |
|------|---------|
| `settings/PageMirrorConfigurable.kt` | Rewrite `createComponent()` with UI DSL v2 `panel {}`. Remove manual field variables, `row()` helper, and manual `isModified`/`apply`/`reset` logic. Delegate to `DialogPanel`. |

## Files NOT Modified

| File | Reason |
|------|--------|
| `settings/PageMirrorSettings.kt` | No changes — `State` data class and persistence are fine as-is |
| `ui/fixtures/PageMirrorSettingsFixture.kt` | UI tests are blocked (see `docs/UI_tests/diagnostic-report.md`). UI DSL v2 wraps components in extra panels, which may break XPath locators. Update deferred until UI tests are unblocked. |
| `integration/SettingsPersistenceTest.kt` | Tests the `PageMirrorSettings` service, not the Configurable UI — unaffected |

---

## Acceptance Criteria

- [ ] Settings panel uses Kotlin UI DSL v2 (`com.intellij.ui.dsl.builder.panel`)
- [ ] Labels are aligned in a consistent column
- [ ] Components are pinned to the top of the panel (no vertical stretching)
- [ ] All 4 settings (search depth, auto-reload, highlight color, code gen style) are present and functional
- [ ] `isModified()` / `apply()` / `reset()` work correctly via `DialogPanel` delegation
- [ ] `./gradlew test` passes (SettingsPersistenceTest unaffected)
- [ ] `./gradlew buildPlugin` succeeds
- [ ] Manual verification: Settings > Tools > Page Mirror renders a clean, aligned form
