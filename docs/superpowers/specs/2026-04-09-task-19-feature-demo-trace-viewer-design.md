# Task 19 — Feature Demo Trace Viewer: Execution Design

**Source task:** `docs/tasks/task-19-feature-demo-trace-viewer.md`
**Date:** 2026-04-09
**Shape:** Three sequential PRs.

## Goal

Ship the `@Feature` → `demoReport` → CI-on-`demo`-label pipeline in three reviewable PRs, reusing Task 13c trace bundles. Reviewers click an artifact link on a labeled PR and walk every UI scenario covering the feature.

## Decisions

- **Split:** 3 PRs (annotation plumbing → renderer + viewer template → Gradle task + CI).
- **Test-to-source mapping:** package heuristic only. No kover/jacoco. Test class package matches package of any changed source file.
- **Self-contained HTML:** no size cap. Inline everything (CSS, JS, base64 screenshots, DOM snapshots).
- **Feature tag source in CI:** PR title prefix `[demo:<tag>]`. Plain `demo` label with no matching title → fail with PR comment.

---

## PR 1 — Annotation plumbing

**New files**
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/annotations/Feature.kt`
  ```kotlin
  @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
  @Retention(AnnotationRetention.RUNTIME)
  annotation class Feature(val tag: String)
  ```
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/FeatureTagListener.kt` — JUnit 5 extension. `BeforeEachCallback` reads `@Feature` from the test method (preferred) or declaring class, stores the tag in the `ExtensionContext` store under a stable key.

**Modified files**
- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/TraceBundleExtension.kt` — on finalize, pull the tag from the store and write `test.feature` into `trace.json`. If no tag, omit the field (Task 13c schema allows optional).
- `BaseUiTest` (or existing `@ExtendWith` site) — register `FeatureTagListener` so every UI test picks it up without opt-in.

**Extension ordering:** `FeatureTagListener` must run before `TraceBundleExtension` finalizes. If `TraceBundleExtension` writes in `AfterEachCallback`, `FeatureTagListener`'s `BeforeEachCallback` ordering is sufficient because the store persists across callbacks. Validate via the smoke test below.

**Tests**
- New unit/integration test: one fixture UI test tagged `@Feature("smoke")`. Run it, parse the produced `trace.json`, assert `feature == "smoke"`.
- One untagged fixture test asserting `feature` field is absent (or null).

**Out of scope for PR 1:** renderer, Gradle task, CI workflow.

---

## PR 2 — Renderer + viewer template

**New files**
- `src/main/resources/demo-viewer/index.html` — shell page. Contains a `<script>window.__TRACE_DATA__ = /*__INJECT__*/</script>` placeholder, inlined `<style>` and `<script>` tags sourced from siblings.
- `src/main/resources/demo-viewer/app.js` — vanilla JS. Reads `window.__TRACE_DATA__`. Renders:
  - Top: summary (N tests / M steps / X failures), feature tag, git SHA.
  - Left pane: test list with pass/fail icon.
  - Center: step timeline from `StepRecorder` entries; hovering a step shows its screenshot; clicking selects it.
  - Right: selected step details — before/after DOM (rendered in sandboxed `<iframe srcdoc>`), stack trace for failures, source file link.
- `src/main/resources/demo-viewer/styles.css` — layout + theming.
- `buildSrc/src/main/kotlin/DemoReportRenderer.kt` — pure function:
  ```kotlin
  fun render(
      bundles: List<Path>,      // each is build/reports/uiTest/traces/<test>/
      outputDir: Path,          // build/reports/demo/<feature>/
      featureTag: String,
      gitSha: String,
  ): Path                        // returns outputDir/index.html
  ```
  Steps: load `demo-viewer/*` from `buildSrc` resources, walk each bundle dir, base64-encode `screenshots/*`, `dom.html` before/after pairs, assemble a JSON array of test records, substitute `/*__INJECT__*/`, write `index.html`.

**Tests (buildSrc)**
- Feed a synthetic bundle dir (hand-crafted minimal `trace.json`, one fake screenshot, one `dom.html`).
- Assert output file exists, is non-empty, contains the step names and the feature tag as substrings.
- Assert the emitted JSON blob parses.

**Out of scope for PR 2:** Gradle task that invokes the renderer, CI.

---

## PR 3 — `demoReport` Gradle task + CI workflow

**Modified files**
- `build.gradle.kts` — register `demoReport` task:
  1. Read required property `featureName`. Fail with a clear message if missing.
  2. Read optional `changedFiles`. If absent, shell out to `git diff --name-only origin/main...HEAD`.
  3. **Selection:** union of
     - tests with `@Feature(featureName)` (discover via classpath scan or `--tests` filter pass, TBD in plan),
     - tests whose class package equals the package of any changed source file under `src/main/kotlin/`.
  4. Run the selected subset of `uiTest` with `-PcaptureAllTraces=true`.
  5. Enforce ≥2 distinct test methods matched for the tag. Fail with message listing what was found.
  6. Call `DemoReportRenderer.render(...)` → `build/reports/demo/<featureName>/index.html`.

**New file**
- `.github/workflows/demo.yml`:
  - Trigger: `pull_request` types: `[labeled]`, filter `if: github.event.label.name == 'demo'`.
  - Steps:
    1. Checkout with `fetch-depth: 0`.
    2. Parse PR title against regex `^\[demo:([a-z0-9-]+)\]`. If no match → post PR comment ("demo label requires `[demo:<tag>]` prefix in PR title") and fail.
    3. Compute changed files: `git diff --name-only origin/${{ github.base_ref }}...HEAD`.
    4. `./gradlew demoReport -PfeatureName=$TAG -PchangedFiles=$FILES`.
    5. Upload `build/reports/demo/**` as artifact `demo-report-${{ github.sha }}`.
    6. Post single PR comment with artifact link.
  - No `push` trigger — re-adding the label is the only way to regenerate.

**Tests**
- Integration test: fixture with two `@Feature("smoke")` scenarios (happy + negative). Run `./gradlew demoReport -PfeatureName=smoke`. Assert `build/reports/demo/smoke/index.html` exists and contains both scenarios' step names.
- Integration test: tag with one scenario → task fails with the ≥2 error.
- Manual verification of CI workflow on a throwaway PR.

## Risks & Notes

- **Package heuristic coarseness.** Changes in shared utilities select many tests. Acceptable for v1; document in PR 3 description.
- **Extension ordering.** If tag ends up missing from `trace.json`, inspect JUnit 5 extension registration order and consider `@Order` on `FeatureTagListener`.
- **Large HTML.** No cap is explicit user choice; warn in PR 2 description that demo artifacts can be tens of MB for long tests.
- **PR title parsing.** Keep regex strict and anchored; a single bash step with clear failure output.

## Out of Scope

Same as source task: live DOM editing, cross-PR diffing, non-UI tests.
