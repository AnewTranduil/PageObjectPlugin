# Task 1: Plugin Shell + Tool Window with JCEF

> **Goal:** Get a Tool Window rendering static HTML via JCEF. No business logic.
> **Depends on:** Nothing (can run in parallel with Task 0)
> **Output:** Working plugin with "Page Mirror" docked panel showing static HTML

## Prompt

I have an IntelliJ Platform Plugin project (Gradle Plugin 2.x, Kotlin, min platform 2024.3). The plugin ID is `com.example.pagemirror`.

Create the plugin shell with these requirements:

1. **plugin.xml:**
   - Register a Tool Window called "Page Mirror" with `anchor="right"`
   - Use a `ToolWindowFactory` implementation
   - Do NOT declare any dependency on JavaScript or WebStorm modules. The plugin must work in IntelliJ Community, Ultimate, and WebStorm.

2. **PageMirrorToolWindowFactory.kt:**
   - Check `JBCefApp.isSupported()` before creating the browser
   - If JCEF is not supported, show a `JBLabel` with a message
   - If supported, create a `JBCefBrowser` and load a bundled HTML file
   - The HTML file path: `/html/page-mirror.html` (bundled in resources)

3. **src/main/resources/html/page-mirror.html:**
   - A minimal page that says "Page Mirror Ready" with a dark theme
   - Include a `<div id="viewport">` where the snapshot HTML will be injected later
   - Include a `<div id="overlay">` absolutely positioned for highlight boxes
   - Include a `<script>` section with stub functions:
     ```js
     window.loadSnapshot = function(html, layoutJson) {};
     window.highlightElement = function(selector) {};
     ```
   - Style: dark bg (`#1e1e1e`), monospace font for metadata

4. **build.gradle.kts:** Make sure the `intellijPlatform` block targets 2024.3 and the plugin verifier runs against IC (Community) and WS (WebStorm).

Do NOT implement any snapshot loading logic yet.
Do NOT add any listeners for file changes or editor events.
Keep it minimal: factory, JCEF browser, static HTML.

## Acceptance Criteria

- [ ] Plugin loads in IntelliJ without errors
- [ ] "Page Mirror" tool window appears in the right panel
- [ ] Opening the tool window shows "Page Mirror Ready" rendered via JCEF
- [ ] No errors in `idea.log` related to the plugin
- [ ] Build succeeds for both `IC` and `WS` targets
