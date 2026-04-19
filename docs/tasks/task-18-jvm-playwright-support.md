# Task 18: Java/Kotlin Playwright Support

> **Goal:** Recognize Playwright Java API locators in `.java` and `.kt` files (highlight + gutter) AND ship `snapshot-saver-jvm` — a Kotlin JVM library that produces spec-v1 bundles from `com.microsoft.playwright.Page`.
> **Depends on:** Task 16 (introduces `LocatorExtractor` interface + registry), `docs/snapshot-bundle-spec.md`.
> **Output:** `JvmLocatorExtractor.kt`, new Gradle artifact `packages/playwright-snapshot-saver-jvm/`.

## Motivation

Playwright's Java API is used by a substantial Selenium-migration crowd and shares the same surface with Kotlin test suites. This task closes the language gap for the JVM ecosystem and finishes Track A. Because the plugin itself is Kotlin/JVM, the saver can reuse the plugin's existing Jsoup dependency for HTML inlining — no new dependencies.

## Key Files

### Plugin side
- `src/main/kotlin/com/github/artem/pageobjectplugin/locators/LocatorExtractorRegistry.kt` — register new extractor for `.java`, `.kt`.
- New: `src/main/kotlin/com/github/artem/pageobjectplugin/locators/JvmLocatorExtractor.kt`
- New: `src/test/kotlin/com/github/artem/pageobjectplugin/locators/JvmLocatorExtractorTest.kt`
- `src/main/kotlin/com/github/artem/pageobjectplugin/settings/PageMirrorSettings.kt` — add `.java`, `.kt` to default `fileExtensions`.

### Saver package
- New: `packages/playwright-snapshot-saver-jvm/` (own `build.gradle.kts`, included via `settings.gradle.kts`).
  - Published coordinates: `com.github.artem.pageobjectplugin:snapshot-saver-jvm`
  - `src/main/kotlin/.../SnapshotSaver.kt` — `SnapshotSaver.save(page, name, options)`
  - `src/main/kotlin/.../HtmlInliner.kt` — Jsoup-based inliner
  - `src/main/kotlin/.../ManifestGenerator.kt`
  - `src/main/kotlin/.../junit5/SnapshotSaverExtension.kt` — JUnit 5 extension analog of the TS reporter
  - `src/test/` — JUnit 5 integration tests using `com.microsoft.playwright`

## JVM Locator Patterns

- `page.getByTestId("...")`
- `page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("..."))`
- `page.getByLabel("...")`
- `page.getByPlaceholder("...")`
- `page.getByText("...")`
- `page.locator("...")`
- Both Java (`new Page.GetByRoleOptions()`) and Kotlin (`GetByRoleOptions { name = "..." }`) idioms.
- Variable-bound receivers (`this.page.locator(...)`, `pageObject.submitButton()` — latter is out of scope since it requires cross-file resolution, handled by Task 16's registry-level convention of field references).

## Saver API

```kotlin
// Kotlin
SnapshotSaver.save(page, name = "login/initial") {
    outputDir = File(".snapshots")
    screenshot = ScreenshotFormat.WEBP
    extraSelectors += "[data-custom]"
}

// Java
SnapshotSaver.save(page, "login/initial", new SaveOptions().setOutputDir(new File(".snapshots")));
```

## Steps

1. Implement `JvmLocatorExtractor` (regex-based, per `CLAUDE.md`). Unit tests for each pattern in both Java and Kotlin syntax.
2. Register extractor in `LocatorExtractorRegistry` for `.java`, `.kt`.
3. Add `.java`, `.kt` to default `fileExtensions` in settings.
4. Scaffold `packages/playwright-snapshot-saver-jvm/` as a sibling Gradle project, included via root `settings.gradle.kts`. Kotlin JVM, Java 17 target.
5. Implement `HtmlInliner` in Kotlin using Jsoup (port of TS version, verified against the shared fixture page via golden-file test).
6. Implement `ManifestGenerator` emitting spec-v1 JSON via `kotlinx.serialization`.
7. Implement `SnapshotSaver.save()` against `com.microsoft.playwright.Page`. Options class mirrors the TS API.
8. Implement `SnapshotSaverExtension` as a JUnit 5 `TestWatcher` + `ParameterResolver` — auto-captures on failure when registered.
9. Integration tests: JUnit 5 suite that launches Playwright Java, visits a fixture page, calls the saver, asserts bundle layout.

## Verification

- **Plugin**: unit tests for `JvmLocatorExtractor` pass; manual smoke on a `.java` and `.kt` Playwright test file — gutter and highlight work.
- **Saver**: `./gradlew :playwright-snapshot-saver-jvm:test` green; bundles byte-compatible with Python + TS savers for the shared fixture (minus timestamp + driver field).
- All three language savers (TS, Python, JVM) produce bundles that load identically in the plugin tool window.

## Out of Scope

- Cross-file resolution of Page Object field references (requires PSI, breaks the no-JS/TS-module rule for JVM files too — future work).
- JUnit 4 support.
- Publishing to Maven Central.
