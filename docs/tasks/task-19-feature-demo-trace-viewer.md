# Task 19: Feature Demo — Playwright-Style Trace Viewer

> **Status:** DONE. Shipped files (paths adjusted from the plan):
> - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/annotations/Feature.kt`
> - `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/support/FeatureTagListener.kt`
>   (a JUnit-Jupiter `BeforeEachCallback`, not a `TestExecutionListener`)
> - `buildSrc/src/main/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoReportRenderer.kt`
>   and `DemoTestSelector.kt`
> - `src/main/resources/demo-viewer/{index.html,app.js,styles.css}`
> - `.github/workflows/demo.yml`
>
> Reviewers receive the demo bundle via the `reports.artemon.cloud`
> dashboard (see `CLAUDE.md` "Report Dashboard Access"). demo.yml
> uploads the report zip with `curl` — there is no `actions/upload-artifact`
> step or PR-comment step. `demoReport` is invoked with `-PfeatureName=all`.
> Xvfb (`:99`) is installed and started before the IDE launches.
>
> **Goal:** Produce a self-contained HTML trace viewer (Playwright-style) per PR that lets reviewers step through every UI test exercising a changed or added feature — timeline, action log, before/after DOM snapshots, screenshots — triggered by adding a `demo` label on a PR.
> **Depends on:** Task 13c (trace bundle + `trace.json` schema), Task 14 (CI reporting, artifact upload).
> **Output:** `./gradlew demoReport -PfeatureName=<tag>` Gradle task, `@Feature("name")` annotation, CI workflow triggered by `demo` label, `build/reports/demo/<feature>/index.html` artifact.

## Motivation

Reviewing a UI-heavy PR today means pulling the branch, running tests locally, and eyeballing screenshots. That's friction. We want a reviewer to click a link on the PR and instantly walk through every test scenario that covers the changed feature — happy path and negative cases — with the same fidelity Playwright's trace viewer offers its users. This is reviewer ergonomics, not a testing feature.

Crucially, this task does **not** build a parallel capture pipeline. It reuses the exact trace bundles Task 13c already produces and adds: (1) a `@Feature("tag")` annotation to select tests, (2) a Gradle task to filter + package, (3) a self-contained HTML renderer, (4) CI wiring for the `demo` label.

## Key Files

- `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/TraceBundleExtension.kt` — reuse; extend to record `@Feature` tag in `trace.json`.
- New: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/annotations/Feature.kt`
- New: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/support/FeatureTagListener.kt` — JUnit 5 `TestExecutionListener` that writes the tag into the trace.
- `build.gradle.kts` — new `demoReport` task.
- New: `buildSrc/src/main/kotlin/DemoReportRenderer.kt` — reads trace bundles, emits self-contained HTML.
- New: `src/main/resources/demo-viewer/` — static HTML+JS+CSS template for the viewer (embedded into the rendered output).
- `.github/workflows/demo.yml` — triggered by `pull_request` with `demo` label.

## `@Feature` Annotation

```kotlin
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Feature(val tag: String)
```

Tests opt in: either a whole class is tagged, or an individual method. `FeatureTagListener` reads the annotation and injects `test.feature` into the generated `trace.json` (schema already defined in Task 13c).

## Test Selection Logic

When `demoReport` runs, it includes a UI test if **either** holds:
1. The test has `@Feature("<tag>")` matching `-PfeatureName=<tag>`, **OR**
2. The test's covered source files intersect `-PchangedFiles=...` (or, if not provided, files from `git diff origin/main...HEAD`).

Both sources are unioned so:
- **New features**: authors add `@Feature("new-foo")` to the tests they create — demo task enforces **≥ 2 scenarios** for the tag (fails otherwise) so happy-path + negative cases are both present.
- **Updated features**: pre-existing tests carrying the tag automatically run alongside tests touched by the diff, letting reviewers confirm nothing regressed.

Covered-files detection: use the existing Kotlin/Java coverage plugin (`kover` or `jacoco`) to map test → touched sources from a prior run, or fall back to a heuristic (test class package ↔ source package).

## Gradle Task

```
./gradlew demoReport -PfeatureName=highlight-all
./gradlew demoReport -PfeatureName=highlight-all -PchangedFiles=src/main/kotlin/.../Foo.kt,...
```

Steps the task performs:
1. Enumerate UI tests and select per the logic above.
2. Run them with `-PcaptureAllTraces=true` so passing tests also produce bundles.
3. Enforce the ≥2-scenarios rule for a tag.
4. Aggregate the produced `build/reports/uiTest/traces/<test>/` directories.
5. Emit `build/reports/demo/<feature>/index.html`.

## Trace Viewer (`index.html`)

Self-contained: single HTML file with inlined CSS + JS + base64-encoded screenshots and DOM snapshots (so it's trivially shareable as a PR comment link to a CI artifact, no relative-path breakage).

UI layout:
- Left pane: list of tests with pass/fail status.
- Center: timeline scrubber showing steps (from `StepRecorder`) with screenshots on hover.
- Right: details for the selected step — DOM diff before/after, failure stack trace if any, link to source file.
- Top: summary counts (N tests, M steps, X failures) + feature tag + git SHA.

Implementation is vanilla JS — no framework, no build step — reading the `trace.json` files embedded as a single JSON blob in the HTML.

## CI Wiring (`.github/workflows/demo.yml`)

- Trigger: `pull_request` with type `labeled`, when the label is `demo`.
- Job:
  1. Checkout with full history.
  2. Compute changed files from `git diff origin/${{ github.base_ref }}...HEAD`.
  3. Run `./gradlew demoReport -PchangedFiles=... -PfeatureName=<derived-from-label-suffix-or-PR-title>`.
  4. Upload `build/reports/demo/**` as an artifact.
  5. Post a single PR comment linking the artifact. **No automatic runs on every push** — `demo` label must be re-added to regenerate.

## Steps

1. Add `@Feature` annotation and `FeatureTagListener`; extend `TraceBundleExtension` to write the tag into `trace.json`.
2. Implement `DemoReportRenderer` in `buildSrc`: read trace bundles, template the self-contained HTML viewer.
3. Write the static viewer template (`resources/demo-viewer/{index.html, app.js, styles.css}`) against the Task 13c `trace.json` schema.
4. Register the `demoReport` Gradle task in `build.gradle.kts`. Implement test-selection logic. Enforce ≥2-scenarios rule.
5. Add `.github/workflows/demo.yml` with the trigger above.
6. Write a smoke test: a fixture UI test tagged `@Feature("smoke")`, run `demoReport`, assert the HTML exists and contains the test's step names.

## Verification

- `./gradlew demoReport -PfeatureName=smoke` produces `build/reports/demo/smoke/index.html`.
- Opening the HTML in a browser shows the tests, timeline, screenshots, and DOM snapshots.
- Running with a tag that has only one scenario fails with a clear error demanding happy + negative coverage.
- A PR labeled `demo` in CI uploads the artifact and posts a linking comment.
- Removing the `demo` label and re-adding it regenerates the artifact.

## Out of Scope

- Live/interactive DOM editing in the viewer.
- Cross-PR diffing of trace bundles.
- Non-UI test inclusion (unit/npm) — demo is UI-only.
