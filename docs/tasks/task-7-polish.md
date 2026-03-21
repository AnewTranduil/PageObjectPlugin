# Task 7: Refinements and Polish

> **Goal:** Add settings, keyboard shortcuts, status bar, theme support, and notifications.
> **Depends on:** Tasks 5 and 6
> **Output:** A polished plugin that feels complete and professional

## Prompt

Extend the Page Mirror IntelliJ plugin (Kotlin, 2024.3+). All core features are implemented. Add polish:

1. **Settings page** (Settings > Tools > Page Mirror):
   - Snapshot search depth: how many parent directories to check for `.snapshots/` (default: 3)
   - Auto-reload on file change: on/off toggle (default: on)
   - Highlight color picker with presets (default: blue `#3B82F6`)
   - Code generation default style: "Property" vs "Variable" (default: Property)
   - Store in a `PersistentStateComponent` with sensible defaults

2. **PageMirrorConfigurable.kt:**
   - Implement `Configurable` for the settings UI
   - Use standard IntelliJ forms (DSL or `FormBuilder`)
   - Register in `plugin.xml` under `<extensions> <applicationConfigurable>`

3. **Status bar widget:**
   - Show "Page Mirror: login/initial (12 elements)" in the IDE status bar when a snapshot is loaded
   - Show "Page Mirror: No snapshot" in gray when nothing is loaded
   - Click the widget to open/focus the Tool Window
   - Icon: green dot when snapshot loaded, gray dot when not

4. **Keyboard shortcuts** (register in `plugin.xml` with keymap defaults):
   - `Alt+Shift+I`: Toggle inspect/picker mode in Page Mirror
   - `Alt+Shift+H`: Highlight current line's selector in Page Mirror (one-shot, doesn't need caret tracking)
   - Make sure they appear in Settings > Keymap > Plugins > Page Mirror

5. **Notifications:**
   - When a snapshot is auto-reloaded after a test run: balloon notification "Snapshot updated: login/error-state"
   - When snapshot loading fails: error notification with a "View Log" action that opens idea.log
   - Use `NotificationGroupManager` with group ID "Page Mirror"

6. **Theme support:**
   - Detect whether the IDE is using a dark or light theme
   - Pass a CSS class (`theme-dark` or `theme-light`) to the JCEF HTML page
   - Update `page-mirror.html` styles: dark theme uses `#1e1e1e` bg, light theme uses `#ffffff` bg
   - Listen for theme changes via `LafManagerListener` and update dynamically

## Acceptance Criteria

- [ ] Settings page appears under Tools > Page Mirror with all options functional
- [ ] Changing highlight color in settings updates the highlight box color immediately
- [ ] Status bar shows current snapshot name; clicking opens Tool Window
- [ ] `Alt+Shift+I` toggles inspect mode (visible in Keymap settings)
- [ ] `Alt+Shift+H` highlights current selector (works even if Tool Window was closed)
- [ ] Balloon notification appears after auto-reload
- [ ] Switching IDE to Light theme updates the Page Mirror background
- [ ] All settings persist across IDE restarts
