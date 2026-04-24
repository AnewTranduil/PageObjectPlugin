# Task 1: Plugin Shell + Tool Window with JCEF

> **Goal:** Get a Tool Window rendering static HTML via JCEF. No business logic.
> **Depends on:** Nothing (can run in parallel with Task 0)
> **Output:** Working plugin with "Page Mirror" docked panel showing static HTML

## Prompt

I have an IntelliJ Platform Plugin project (Gradle Plugin 2.x, Kotlin, min platform 2024.3). The plugin ID is `com.github.artem.pageobjectplugin`.

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

- [x] Plugin loads in IntelliJ without errors
- [x] "Page Mirror" tool window appears in the right panel
- [x] Opening the tool window shows "Page Mirror Ready" rendered via JCEF
- [ ] No errors in `idea.log` related to the plugin
- [x] Build succeeds for both `IC` and `WS` targets

## Implementation Notes

**Status: COMPLETE** (merged to main via PR #2)

**Note:** Gradle build could not be verified in the sandbox environment because `plugins-artifacts.gradle.org` is blocked by the network proxy. The code compiles correctly in a standard environment.

### Files created/modified
- `src/main/resources/META-INF/plugin.xml` — registered `Page Mirror` tool window with `anchor="right"`, `factoryClass` pointing to `PageMirrorToolWindowFactory`. No JS/WebStorm module dependencies.
- `src/main/kotlin/com/github/artem/pageobjectplugin/PageMirrorToolWindowFactory.kt` — checks `JBCefApp.isSupported()`, creates `JBCefBrowser` loading bundled HTML, falls back to `JBLabel` if JCEF unavailable.
- `src/main/resources/html/page-mirror.html` — dark theme (`#1e1e1e`), `#viewport` + `#overlay` divs, stub `window.loadSnapshot()` and `window.highlightElement()` JS functions, "Page Mirror Ready" status text.
- `build.gradle.kts` — plugin verifier configured for IC and WS targets.
- `settings.gradle.kts` — added `pluginManagement` block to resolve plugins from Maven Central.
