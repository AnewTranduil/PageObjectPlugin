# Task 19 — Feature Demo Trace Viewer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a `demoReport` Gradle task + `@Feature` annotation + CI-on-`demo`-label pipeline that renders a self-contained HTML trace viewer from existing Task 13c trace bundles.

**Architecture:** Three sequential PRs. PR 1 adds the `@Feature` annotation and writes the tag into existing `trace.json`. PR 2 adds a `buildSrc` renderer + static viewer template. PR 3 adds the Gradle task (package-heuristic test selection) and the CI workflow triggered by `[demo:<tag>]` PR title.

**Tech Stack:** Kotlin, JUnit 5, Gradle (IPG 2.x), kotlinx.serialization (already used by `TraceBundle`), vanilla HTML/JS/CSS for the viewer, GitHub Actions.

**Key existing facts (verified):**
- `TraceTest.feature: String? = null` already exists in `src/uiTest/.../support/TraceBundle.kt:32`. PR 1 only needs to populate it.
- `TraceBundleExtension` is registered on `BaseUiTest` via `@ExtendWith` (`BaseUiTest.kt:27`). We can add a second `@ExtendWith(FeatureTagListener::class)` there.
- `TraceBundleExtension` serializes the bundle in `afterTestExecution` → a `BeforeEachCallback` that stores the tag in the same `ExtensionContext` store namespace will be visible there.

---

## PR 1 — Annotation plumbing

### Task 1.1: `@Feature` annotation

**Files:**
- Create: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/annotations/Feature.kt`

- [ ] **Step 1: Create the annotation file**

```kotlin
package com.github.artem.pageobjectplugin.ui.annotations

/**
 * Tags a UI test (or a whole test class) with a feature name. Consumed by
 * `FeatureTagListener` — the tag is written into `trace.json` and used by the
 * `demoReport` Gradle task to select tests for the PR demo bundle.
 *
 * Method-level `@Feature` wins over a class-level one on the same test.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Feature(val tag: String)
```

- [ ] **Step 2: Commit**

```bash
git add src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/annotations/Feature.kt
git commit -m "feat(task-19): add @Feature annotation for UI tests"
```

---

### Task 1.2: `FeatureTagListener` extension

**Files:**
- Create: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/support/FeatureTagListener.kt`

- [ ] **Step 1: Create the listener**

```kotlin
package com.github.artem.pageobjectplugin.ui.support

import com.github.artem.pageobjectplugin.ui.annotations.Feature
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace

/**
 * Reads `@Feature` from the current test method (preferred) or declaring class
 * and stores the tag under [NS]/[KEY] so [TraceBundleExtension] can embed it in
 * `trace.json`. Method-level wins over class-level.
 */
class FeatureTagListener : BeforeEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        val tag = resolveTag(context) ?: return
        context.getStore(NS).put(KEY, tag)
    }

    private fun resolveTag(context: ExtensionContext): String? {
        val methodTag = context.testMethod
            .map { it.getAnnotation(Feature::class.java) }
            .orElse(null)
            ?.tag
        if (methodTag != null) return methodTag
        return context.testClass
            .map { it.getAnnotation(Feature::class.java) }
            .orElse(null)
            ?.tag
    }

    companion object {
        val NS: Namespace = Namespace.create(FeatureTagListener::class.java)
        const val KEY: String = "featureTag"

        fun readTag(context: ExtensionContext): String? =
            context.getStore(NS).get(KEY) as? String
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/support/FeatureTagListener.kt
git commit -m "feat(task-19): add FeatureTagListener to capture @Feature tag per test"
```

---

### Task 1.3: Wire tag into `trace.json`

**Files:**
- Modify: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/support/TraceBundleExtension.kt` (inside `writeBundle`, where `TraceTest(...)` is built — lines ~147-152)
- Modify: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/BaseUiTest.kt` (add `@ExtendWith(FeatureTagListener::class)`)

- [ ] **Step 1: Register the listener on `BaseUiTest`**

Edit `BaseUiTest.kt`. Add import and a second `@ExtendWith`:

```kotlin
import com.github.artem.pageobjectplugin.ui.support.FeatureTagListener
```

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(RetryOnceExtension::class)
@ExtendWith(TraceBundleExtension::class)
@ExtendWith(FeatureTagListener::class)
abstract class BaseUiTest {
```

- [ ] **Step 2: Populate `TraceTest.feature`**

In `TraceBundleExtension.writeBundle`, change the `TraceTest(...)` construction to read the tag:

```kotlin
val bundle = TraceBundle(
    test = TraceTest(
        className = context.testClass.map { it.name }.orElse("Unknown"),
        method = method,
        displayName = context.displayName,
        feature = FeatureTagListener.readTag(context),
    ),
    // ... rest unchanged
)
```

- [ ] **Step 3: Commit**

```bash
git add src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/BaseUiTest.kt \
        src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/support/TraceBundleExtension.kt
git commit -m "feat(task-19): embed @Feature tag into trace.json"
```

---

### Task 1.4: Unit test for `FeatureTagListener`

**Files:**
- Create: `src/test/kotlin/com/github/artem/pageobjectplugin/ui/support/FeatureTagListenerTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.artem.pageobjectplugin.ui.support

import com.github.artem.pageobjectplugin.ui.annotations.Feature
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import java.lang.reflect.Method
import java.util.Optional

private class FakeStore : ExtensionContext.Store {
    private val map = mutableMapOf<Any, Any?>()
    override fun get(key: Any): Any? = map[key]
    override fun <V : Any?> get(key: Any, requiredType: Class<V>): V? =
        @Suppress("UNCHECKED_CAST") (map[key] as V?)
    override fun <K : Any?, V : Any?> getOrComputeIfAbsent(
        key: K, defaultCreator: java.util.function.Function<K, V>,
    ): Any = map.getOrPut(key as Any) { defaultCreator.apply(key) } as Any
    override fun <K : Any?, V : Any?> getOrComputeIfAbsent(
        key: K, defaultCreator: java.util.function.Function<K, V>, requiredType: Class<V>,
    ): V = @Suppress("UNCHECKED_CAST") (getOrComputeIfAbsent(key, defaultCreator) as V)
    override fun put(key: Any, value: Any?) { map[key] = value }
    override fun remove(key: Any): Any? = map.remove(key)
    override fun <V : Any?> remove(key: Any, requiredType: Class<V>): V? =
        @Suppress("UNCHECKED_CAST") (map.remove(key) as V?)
}

private class FakeContext(
    private val method: Method?,
    private val klass: Class<*>?,
) : ExtensionContext {
    private val store = FakeStore()
    override fun getStore(namespace: ExtensionContext.Namespace): ExtensionContext.Store = store
    override fun getTestMethod(): Optional<Method> = Optional.ofNullable(method)
    override fun getTestClass(): Optional<Class<*>> = Optional.ofNullable(klass)
    // All other members: throw — not used by FeatureTagListener.
    override fun getParent() = Optional.empty<ExtensionContext>()
    override fun getRoot(): ExtensionContext = this
    override fun getUniqueId() = "fake"
    override fun getDisplayName() = "fake"
    override fun getTags() = emptySet<String>()
    override fun getElement() = Optional.empty<java.lang.reflect.AnnotatedElement>()
    override fun getTestInstance() = Optional.empty<Any>()
    override fun getTestInstances() = Optional.empty<org.junit.jupiter.api.extension.TestInstances>()
    override fun getTestInstanceLifecycle() = Optional.empty<org.junit.jupiter.api.TestInstance.Lifecycle>()
    override fun getExecutionException() = Optional.empty<Throwable>()
    override fun getConfigurationParameter(key: String) = Optional.empty<String>()
    override fun <T : Any?> getConfigurationParameter(
        key: String, transformer: java.util.function.Function<String, T>,
    ) = Optional.empty<T>()
    override fun publishReportEntry(map: MutableMap<String, String>) {}
    override fun getExecutableInvoker() =
        throw UnsupportedOperationException("not used")
}

@Feature("class-level")
private class ClassTagged {
    fun plain() {}

    @Feature("method-level")
    fun overridden() {}
}

private class Untagged {
    fun plain() {}
}

class FeatureTagListenerTest {

    private val listener = FeatureTagListener()

    @Test
    fun `class-level tag is captured`() {
        val ctx = FakeContext(ClassTagged::class.java.getDeclaredMethod("plain"), ClassTagged::class.java)
        listener.beforeEach(ctx)
        assertEquals("class-level", FeatureTagListener.readTag(ctx))
    }

    @Test
    fun `method-level tag overrides class-level`() {
        val ctx = FakeContext(ClassTagged::class.java.getDeclaredMethod("overridden"), ClassTagged::class.java)
        listener.beforeEach(ctx)
        assertEquals("method-level", FeatureTagListener.readTag(ctx))
    }

    @Test
    fun `untagged test has no tag`() {
        val ctx = FakeContext(Untagged::class.java.getDeclaredMethod("plain"), Untagged::class.java)
        listener.beforeEach(ctx)
        assertNull(FeatureTagListener.readTag(ctx))
    }
}
```

- [ ] **Step 2: Run the test**

Run: `./gradlew test --tests "com.github.artem.pageobjectplugin.ui.support.FeatureTagListenerTest"`
Expected: PASS (three tests).

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/com/github/artem/pageobjectplugin/ui/support/FeatureTagListenerTest.kt
git commit -m "test(task-19): unit tests for FeatureTagListener tag resolution"
```

---

### Task 1.5: Open PR 1

- [ ] **Step 1: Push and open PR**

```bash
git push -u origin HEAD
gh pr create --title "task-19 PR1: @Feature annotation + trace.json tag" --body "$(cat <<'EOF'
## Summary
- Adds `@Feature(tag)` annotation for UI tests
- Adds `FeatureTagListener` that resolves method-then-class tag into the JUnit store
- `TraceBundleExtension` now embeds the tag into `trace.json` via the existing `TraceTest.feature` field

First of three PRs for Task 19. No renderer or Gradle task yet.

## Test plan
- [x] New unit tests for `FeatureTagListener` pass locally
- [ ] `test-report` CI job green
EOF
)"
```

- [ ] **Step 2: Wait for CI**

Follow the Test Loop in `CLAUDE.md`: wait for `test-report`, read `claude-summary.md`, fix any regressions, push, repeat until green.

---

## PR 2 — Renderer + viewer template

### Task 2.1: Static viewer template files

**Files:**
- Create: `src/main/resources/demo-viewer/index.html`
- Create: `src/main/resources/demo-viewer/app.js`
- Create: `src/main/resources/demo-viewer/styles.css`

- [ ] **Step 1: Write `index.html`**

```html
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Page Mirror — Feature Demo</title>
<style>/*__STYLES__*/</style>
</head>
<body>
<header id="summary"></header>
<main>
  <aside id="test-list"></aside>
  <section id="timeline"></section>
  <section id="details"></section>
</main>
<script>window.__TRACE_DATA__ = /*__DATA__*/ null;</script>
<script>/*__APP__*/</script>
</body>
</html>
```

- [ ] **Step 2: Write `styles.css`**

```css
:root { color-scheme: light dark; font-family: system-ui, sans-serif; }
body { margin: 0; display: flex; flex-direction: column; height: 100vh; }
header#summary {
  padding: 8px 16px; border-bottom: 1px solid #8884;
  display: flex; gap: 24px; align-items: baseline;
}
header#summary .tag { font-weight: 600; }
header#summary .sha { font-family: monospace; opacity: 0.7; }
main { display: grid; grid-template-columns: 240px 1fr 360px; flex: 1; min-height: 0; }
aside#test-list, section#timeline, section#details {
  overflow: auto; padding: 8px; border-right: 1px solid #8884;
}
section#details { border-right: none; }
.test-item { padding: 6px 8px; cursor: pointer; border-radius: 4px; }
.test-item:hover { background: #8882; }
.test-item.selected { background: #3b82f633; }
.test-item .status { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 6px; }
.test-item .status.passed { background: #22c55e; }
.test-item .status.failed { background: #ef4444; }
.test-item .status.aborted { background: #f59e0b; }
.step { padding: 6px 8px; cursor: pointer; border-bottom: 1px solid #8882; }
.step.selected { background: #3b82f633; }
.step .label { font-weight: 500; }
.step .dur { opacity: 0.6; font-size: 0.85em; margin-left: 8px; }
.step.has-error { color: #ef4444; }
#details img, #details iframe {
  max-width: 100%; border: 1px solid #8884; border-radius: 4px;
}
#details pre { white-space: pre-wrap; font-size: 0.85em; }
```

- [ ] **Step 3: Write `app.js`**

```javascript
(function () {
  var data = window.__TRACE_DATA__;
  if (!data) {
    document.body.textContent = 'No trace data.';
    return;
  }

  var header = document.getElementById('summary');
  var totalSteps = data.tests.reduce(function (acc, t) { return acc + (t.steps || []).length; }, 0);
  var failures = data.tests.filter(function (t) { return t.status === 'failed'; }).length;
  header.innerHTML =
    '<span class="tag">' + escapeHtml(data.feature) + '</span>' +
    '<span>' + data.tests.length + ' tests</span>' +
    '<span>' + totalSteps + ' steps</span>' +
    '<span>' + failures + ' failures</span>' +
    '<span class="sha">' + escapeHtml(data.gitSha || '') + '</span>';

  var listEl = document.getElementById('test-list');
  var timelineEl = document.getElementById('timeline');
  var detailsEl = document.getElementById('details');

  var selectedTestIdx = 0;
  var selectedStepIdx = 0;

  function render() {
    listEl.innerHTML = '';
    data.tests.forEach(function (t, i) {
      var el = document.createElement('div');
      el.className = 'test-item' + (i === selectedTestIdx ? ' selected' : '');
      el.innerHTML =
        '<span class="status ' + t.status + '"></span>' +
        escapeHtml(t.displayName || t.method);
      el.onclick = function () { selectedTestIdx = i; selectedStepIdx = 0; render(); };
      listEl.appendChild(el);
    });

    var test = data.tests[selectedTestIdx];
    timelineEl.innerHTML = '';
    (test.steps || []).forEach(function (s, i) {
      var el = document.createElement('div');
      el.className = 'step' + (i === selectedStepIdx ? ' selected' : '') + (s.error ? ' has-error' : '');
      el.innerHTML =
        '<span class="label">' + escapeHtml(s.label) + '</span>' +
        '<span class="dur">' + s.durationMs + 'ms</span>';
      el.onclick = function () { selectedStepIdx = i; render(); };
      timelineEl.appendChild(el);
    });

    var step = (test.steps || [])[selectedStepIdx];
    detailsEl.innerHTML = '';
    if (step && step.screenshotDataUri) {
      var img = document.createElement('img');
      img.src = step.screenshotDataUri;
      detailsEl.appendChild(img);
    }
    if (test.failure) {
      var pre = document.createElement('pre');
      pre.textContent = test.failure.stack;
      detailsEl.appendChild(pre);
    }
    if (test.domHtmlDataUri) {
      var iframe = document.createElement('iframe');
      iframe.src = test.domHtmlDataUri;
      iframe.style.width = '100%';
      iframe.style.height = '300px';
      iframe.setAttribute('sandbox', '');
      detailsEl.appendChild(iframe);
    }
  }

  function escapeHtml(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  render();
})();
```

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/demo-viewer/
git commit -m "feat(task-19): add static demo-viewer template (HTML/CSS/JS)"
```

---

### Task 2.2: `DemoReportRenderer` in `buildSrc`

**Files:**
- Create: `buildSrc/src/main/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoReportRenderer.kt`

**Note on template access:** `buildSrc` cannot depend on `src/main/resources/`. Read the template files directly from the project tree via a caller-supplied path (`templateDir: Path`). The Gradle task in PR 3 will pass `rootProject.file("src/main/resources/demo-viewer").toPath()`.

- [ ] **Step 1: Write the failing test**

Create `buildSrc/src/test/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoReportRendererTest.kt`:

```kotlin
package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class DemoReportRendererTest {

    @Test
    fun `renders self-contained HTML with test data`(@TempDir tmp: Path) {
        val bundle = tmp.resolve("traces/SampleUiTest__logs_in")
        Files.createDirectories(bundle.resolve("screenshots"))
        bundle.resolve("screenshots/step-1.png").writeText("fake-png-bytes")
        bundle.resolve("trace.json").writeText(
            """
            {
              "version":1,
              "test":{"className":"SampleUiTest","method":"logs_in","displayName":"logs in","feature":"smoke"},
              "startedAt":"2026-04-09T00:00:00Z","durationMs":1234,"status":"passed","flaky":false,
              "steps":[{"index":0,"label":"click login","at":"2026-04-09T00:00:00Z","durationMs":42,
                        "screenshot":"screenshots/step-1.png","error":null}],
              "artifacts":{"ideaLog":null,"dom":null,"jcefConsole":null,"threads":null}
            }
            """.trimIndent(),
        )

        val templateDir = tmp.resolve("template").also { Files.createDirectories(it) }
        templateDir.resolve("index.html").writeText(
            "<html><style>/*__STYLES__*/</style><script>window.__TRACE_DATA__ = /*__DATA__*/ null;</script>" +
                "<script>/*__APP__*/</script></html>",
        )
        templateDir.resolve("styles.css").writeText("body{}")
        templateDir.resolve("app.js").writeText("console.log('ok');")

        val out = tmp.resolve("out")
        val result = DemoReportRenderer.render(
            bundles = listOf(bundle),
            outputDir = out,
            featureTag = "smoke",
            gitSha = "deadbeef",
            templateDir = templateDir,
        )

        assertTrue(Files.exists(result))
        val html = Files.readString(result)
        assertTrue(html.contains("click login"), "step label must be present")
        assertTrue(html.contains("smoke"), "feature tag must be present")
        assertTrue(html.contains("deadbeef"), "git sha must be present")
        assertTrue(html.contains("data:image/png;base64,"), "screenshot must be base64-inlined")
        assertTrue(html.contains("console.log('ok');"), "app.js must be inlined")
        assertFalse(html.contains("/*__DATA__*/"), "placeholder must be substituted")
        assertFalse(html.contains("/*__STYLES__*/"), "styles placeholder must be substituted")
        assertFalse(html.contains("/*__APP__*/"), "app placeholder must be substituted")
    }
}
```

- [ ] **Step 2: Run the test — expect compile failure**

Run: `./gradlew :buildSrc:test`
Expected: FAIL (`DemoReportRenderer` does not exist).

- [ ] **Step 3: Implement `DemoReportRenderer`**

```kotlin
package com.github.artem.pageobjectplugin.buildtools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Renders a self-contained Playwright-style trace viewer HTML from a set of
 * Task 13c trace bundle directories. All assets (CSS, JS, screenshots, DOM
 * snapshots) are inlined — the output is a single `index.html` shareable as a
 * CI artifact.
 */
object DemoReportRenderer {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun render(
        bundles: List<Path>,
        outputDir: Path,
        featureTag: String,
        gitSha: String,
        templateDir: Path,
    ): Path {
        Files.createDirectories(outputDir)

        val testsJson = buildJsonArray {
            bundles.forEach { bundleDir ->
                val traceFile = bundleDir.resolve("trace.json")
                if (!Files.exists(traceFile)) return@forEach
                val trace = json.parseToJsonElement(Files.readString(traceFile)) as? JsonObject
                    ?: return@forEach
                add(transformTest(trace, bundleDir))
            }
        }

        val dataBlob = buildJsonObject {
            put("feature", featureTag)
            put("gitSha", gitSha)
            put("tests", testsJson)
        }

        val template = Files.readString(templateDir.resolve("index.html"))
        val styles = Files.readString(templateDir.resolve("styles.css"))
        val app = Files.readString(templateDir.resolve("app.js"))

        val html = template
            .replace("/*__STYLES__*/", styles)
            .replace("/*__APP__*/", app)
            .replace("/*__DATA__*/ null", json.encodeToString(JsonElement.serializer(), dataBlob))

        val out = outputDir.resolve("index.html")
        Files.writeString(out, html)
        return out
    }

    private fun transformTest(trace: JsonObject, bundleDir: Path): JsonElement {
        val testObj = trace["test"] as? JsonObject
        val className = (testObj?.get("className") as? JsonPrimitive)?.content ?: "Unknown"
        val method = (testObj?.get("method") as? JsonPrimitive)?.content ?: "unknown"
        val displayName = (testObj?.get("displayName") as? JsonPrimitive)?.content ?: method
        val status = (trace["status"] as? JsonPrimitive)?.content ?: "unknown"

        val stepsIn = (trace["steps"] as? kotlinx.serialization.json.JsonArray) ?: emptyList()
        val stepsOut = buildJsonArray {
            stepsIn.forEach { stepEl ->
                val step = stepEl as? JsonObject ?: return@forEach
                val screenshotRel = (step["screenshot"] as? JsonPrimitive)?.contentOrNull()
                val dataUri = screenshotRel?.let { rel ->
                    val p = bundleDir.resolve(rel)
                    if (Files.exists(p)) toDataUri(p, "image/png") else null
                }
                add(buildJsonObject {
                    put("index", (step["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0)
                    put("label", (step["label"] as? JsonPrimitive)?.content ?: "")
                    put("durationMs", (step["durationMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L)
                    put("error", (step["error"] as? JsonPrimitive)?.contentOrNull())
                    if (dataUri != null) put("screenshotDataUri", dataUri)
                })
            }
        }

        val artifacts = trace["artifacts"] as? JsonObject
        val domRel = (artifacts?.get("dom") as? JsonPrimitive)?.contentOrNull()
        val domDataUri = domRel?.let { rel ->
            val p = bundleDir.resolve(rel)
            if (Files.exists(p)) toDataUri(p, "text/html") else null
        }

        val failureEl = trace["failure"] as? JsonObject

        return buildJsonObject {
            put("className", className)
            put("method", method)
            put("displayName", displayName)
            put("status", status)
            put("steps", stepsOut)
            if (failureEl != null) put("failure", failureEl)
            if (domDataUri != null) put("domHtmlDataUri", domDataUri)
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this is JsonPrimitive && this.content == "null") null else this.content

    private fun toDataUri(path: Path, mime: String): String {
        val bytes = Files.readAllBytes(path)
        return "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    }
}
```

- [ ] **Step 4: Ensure `buildSrc` has kotlinx-serialization**

Check `buildSrc/build.gradle.kts`. If it does not already include kotlinx-serialization, add:

```kotlin
plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version "<match root version>"
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:<match root version>")
}
```

If the file already has these, skip. Confirm by reading the file before editing.

- [ ] **Step 5: Run the test**

Run: `./gradlew :buildSrc:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add buildSrc/src/main/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoReportRenderer.kt \
        buildSrc/src/test/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoReportRendererTest.kt \
        buildSrc/build.gradle.kts
git commit -m "feat(task-19): DemoReportRenderer renders self-contained viewer HTML"
```

---

### Task 2.3: Open PR 2

- [ ] **Step 1: Push and open PR**

```bash
git push -u origin HEAD
gh pr create --title "task-19 PR2: DemoReportRenderer + static viewer template" --body "$(cat <<'EOF'
## Summary
- Adds static viewer under `src/main/resources/demo-viewer/` (HTML/CSS/JS, vanilla, no build step)
- Adds `DemoReportRenderer` in `buildSrc`: walks Task 13c trace bundles, base64-inlines screenshots/DOM, emits a single `index.html`
- Unit test covers placeholder substitution, step labels, feature tag, git SHA, and screenshot inlining

Second of three PRs for Task 19. Not yet invoked by any Gradle task.

## Test plan
- [x] `./gradlew :buildSrc:test` passes locally
- [ ] `test-report` CI job green
EOF
)"
```

- [ ] **Step 2: Wait for CI and iterate until green**

---

## PR 3 — `demoReport` Gradle task + CI workflow

### Task 3.1: Gradle task skeleton

**Files:**
- Modify: `build.gradle.kts` (register new task near the existing `uiTest`/`runIdeForUiTests` task definitions)

- [ ] **Step 1: Add the `demoReport` task**

Append this task registration block. Read the file first to find the correct insertion point (after other custom task definitions, near line 108 area per `CLAUDE.md`).

```kotlin
tasks.register("demoReport") {
    group = "verification"
    description = "Run selected UI tests and render a self-contained feature-demo HTML trace viewer."

    doLast {
        val featureName = (project.findProperty("featureName") as? String)
            ?: error("demoReport requires -PfeatureName=<tag>")

        val changedFilesProp = project.findProperty("changedFiles") as? String
        val changedFiles: List<String> = if (!changedFilesProp.isNullOrBlank()) {
            changedFilesProp.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            gitDiffChangedFiles()
        }

        val selection = com.github.artem.pageobjectplugin.buildtools
            .DemoTestSelector.select(
                projectDir = projectDir.toPath(),
                featureTag = featureName,
                changedFiles = changedFiles,
            )
        val selectedTests = selection.selected
        val taggedCount = selection.taggedCount

        if (taggedCount < 2) {
            error(
                "demoReport requires at least 2 scenarios tagged @Feature(\"$featureName\") " +
                    "(found $taggedCount). Add a happy-path AND a negative-case test."
            )
        }

        val uiTestTask = tasks.named("uiTest").get()
        uiTestTask.setProperty("ui.test.captureAllTraces", "true")
        // Re-invoke uiTest with the filter. `--tests` filters can be specified
        // on the command line; in a doLast we exec a nested Gradle run.
        val gradlew = if (System.getProperty("os.name").startsWith("Windows")) "gradlew.bat" else "./gradlew"
        val testsArgs = selectedTests.flatMap { listOf("--tests", it) }
        exec {
            commandLine = listOf(gradlew, "uiTest", "-PcaptureAllTraces=true") + testsArgs
        }

        val traceRoot = layout.buildDirectory.dir("reports/uiTest/traces").get().asFile.toPath()
        val bundles = java.nio.file.Files.list(traceRoot).use { stream ->
            stream.filter { java.nio.file.Files.isDirectory(it) }.toList()
        }

        val gitSha = providers.exec {
            commandLine("git", "rev-parse", "HEAD")
        }.standardOutput.asText.get().trim()

        val outDir = layout.buildDirectory.dir("reports/demo/$featureName").get().asFile.toPath()
        com.github.artem.pageobjectplugin.buildtools.DemoReportRenderer.render(
            bundles = bundles,
            outputDir = outDir,
            featureTag = featureName,
            gitSha = gitSha,
            templateDir = rootProject.file("src/main/resources/demo-viewer").toPath(),
        )

        logger.lifecycle("demoReport: wrote $outDir/index.html")
    }
}

fun gitDiffChangedFiles(): List<String> {
    val baseRef = System.getenv("DEMO_BASE_REF") ?: "origin/main"
    return providers.exec {
        commandLine("git", "diff", "--name-only", "$baseRef...HEAD")
    }.standardOutput.asText.get()
        .lines().map { it.trim() }.filter { it.isNotEmpty() }
}
```

- [ ] **Step 2: Commit (compilation will fail — `DemoTestSelector` missing, fixed next task)**

Don't commit yet — proceed to Task 3.2 before compiling.

---

### Task 3.2: `DemoTestSelector` — tag + package-heuristic

**Files:**
- Create: `buildSrc/src/main/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoTestSelector.kt`
- Create: `buildSrc/src/test/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoTestSelectorTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class DemoTestSelectorTest {

    private fun seed(root: Path) {
        val uiTestDir = root.resolve("src/uiTest/kotlin/com/example/foo")
        Files.createDirectories(uiTestDir)
        uiTestDir.resolve("AlphaUiTest.kt").writeText(
            """
            package com.example.foo
            import com.github.artem.pageobjectplugin.ui.annotations.Feature
            class AlphaUiTest {
                @Feature("smoke") fun happy() {}
                @Feature("smoke") fun negative() {}
                fun untagged() {}
            }
            """.trimIndent(),
        )
        val uiTestDir2 = root.resolve("src/uiTest/kotlin/com/example/bar")
        Files.createDirectories(uiTestDir2)
        uiTestDir2.resolve("BetaUiTest.kt").writeText(
            """
            package com.example.bar
            class BetaUiTest { fun logsIn() {} }
            """.trimIndent(),
        )
        Files.createDirectories(root.resolve("src/main/kotlin/com/example/bar"))
    }

    @Test
    fun `tag-matched tests are selected and counted`(@TempDir root: Path) {
        seed(root)
        val result = DemoTestSelector.select(
            projectDir = root,
            featureTag = "smoke",
            changedFiles = emptyList(),
        )
        assertEquals(2, result.taggedCount)
        assertTrue(result.selected.any { it.endsWith("AlphaUiTest.happy") })
        assertTrue(result.selected.any { it.endsWith("AlphaUiTest.negative") })
    }

    @Test
    fun `changed files select tests by package heuristic`(@TempDir root: Path) {
        seed(root)
        val result = DemoTestSelector.select(
            projectDir = root,
            featureTag = "smoke",
            changedFiles = listOf("src/main/kotlin/com/example/bar/Thing.kt"),
        )
        // Tag still required to have 2 scenarios (uses 'smoke') — that's fine, AlphaUiTest has 2.
        assertTrue(result.selected.any { it.contains("BetaUiTest") })
        assertTrue(result.selected.any { it.contains("AlphaUiTest") })
    }

    @Test
    fun `insufficient tagged scenarios surfaces in count`(@TempDir root: Path) {
        val ui = root.resolve("src/uiTest/kotlin/com/example/solo")
        Files.createDirectories(ui)
        ui.resolve("SoloUiTest.kt").writeText(
            """
            package com.example.solo
            import com.github.artem.pageobjectplugin.ui.annotations.Feature
            class SoloUiTest { @Feature("lone") fun only() {} }
            """.trimIndent(),
        )
        val result = DemoTestSelector.select(
            projectDir = root,
            featureTag = "lone",
            changedFiles = emptyList(),
        )
        assertEquals(1, result.taggedCount)
    }
}
```

- [ ] **Step 2: Run test — expect fail**

Run: `./gradlew :buildSrc:test --tests "*DemoTestSelectorTest*"`
Expected: FAIL (class missing).

- [ ] **Step 3: Implement `DemoTestSelector`**

```kotlin
package com.github.artem.pageobjectplugin.buildtools

import java.nio.file.Files
import java.nio.file.Path

/**
 * Selects UI test methods for the `demoReport` task by (1) scanning
 * `src/uiTest/kotlin` for `@Feature("<tag>")` annotations and (2) including
 * test classes whose package matches the package of any changed source file
 * under `src/main/kotlin`.
 *
 * Scanning is done via regex over Kotlin source — no reflection, no compile
 * step. The regex is intentionally conservative; if a test uses `@Feature` in
 * an unusual form (e.g., constant reference) it will not be picked up, and
 * authors should inline the literal tag.
 */
object DemoTestSelector {

    data class Result(val selected: List<String>, val taggedCount: Int)

    private val featureRegex = Regex("""@Feature\(\s*"([^"]+)"\s*\)""")
    private val packageRegex = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val classRegex = Regex("""class\s+(\w+)""")
    private val funRegex = Regex("""fun\s+(\w+)\s*\(""")

    fun select(projectDir: Path, featureTag: String, changedFiles: List<String>): Result {
        val uiTestRoot = projectDir.resolve("src/uiTest/kotlin")
        if (!Files.isDirectory(uiTestRoot)) return Result(emptyList(), 0)

        val changedPackages: Set<String> = changedFiles
            .filter { it.startsWith("src/main/kotlin/") && it.endsWith(".kt") }
            .mapNotNull { rel ->
                val withoutRoot = rel.removePrefix("src/main/kotlin/")
                val slash = withoutRoot.lastIndexOf('/')
                if (slash <= 0) null else withoutRoot.substring(0, slash).replace('/', '.')
            }
            .toSet()

        val selected = linkedSetOf<String>()
        var taggedCount = 0

        Files.walk(uiTestRoot).use { stream ->
            stream.filter { it.toString().endsWith(".kt") && Files.isRegularFile(it) }.forEach { file ->
                val text = Files.readString(file)
                val pkg = packageRegex.find(text)?.groupValues?.get(1) ?: return@forEach
                val className = classRegex.find(text)?.groupValues?.get(1) ?: return@forEach
                val fqn = "$pkg.$className"

                // Class-level tag
                val classTag = featureRegex.findAll(text.substringBefore("class "))
                    .map { it.groupValues[1] }.firstOrNull()

                // Walk function declarations; match per-method tag
                val funcTags = mutableMapOf<String, String?>()
                val funcPositions = funRegex.findAll(text)
                    .map { it.groupValues[1] to it.range.first }
                    .toList()
                funcPositions.forEach { (name, pos) ->
                    val lookBack = text.substring((pos - 200).coerceAtLeast(0), pos)
                    val mTag = featureRegex.find(lookBack)?.groupValues?.get(1)
                    funcTags[name] = mTag ?: classTag
                }

                funcTags.forEach { (fn, tag) ->
                    if (tag == featureTag) {
                        selected.add("$fqn.$fn")
                        taggedCount++
                    }
                }

                if (changedPackages.any { pkg == it || pkg.startsWith("$it.") }) {
                    selected.add(fqn)
                }
            }
        }

        return Result(selected.toList(), taggedCount)
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :buildSrc:test --tests "*DemoTestSelectorTest*"`
Expected: PASS (three tests).

- [ ] **Step 5: Compile root project**

Run: `./gradlew help`
Expected: configuration succeeds (proves the new `demoReport` task in `build.gradle.kts` resolves `DemoTestSelector` from `buildSrc`).

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts \
        buildSrc/src/main/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoTestSelector.kt \
        buildSrc/src/test/kotlin/com/github/artem/pageobjectplugin/buildtools/DemoTestSelectorTest.kt
git commit -m "feat(task-19): demoReport Gradle task with package-heuristic test selection"
```

---

### Task 3.3: Smoke fixture tests + end-to-end verification

**Files:**
- Create: `src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/tests/DemoSmokeUiTest.kt`

- [ ] **Step 1: Create two tagged scenarios**

Use the existing `ToolWindowUiTest` as a structural reference for page/flow composition (per CLAUDE.md UI Test Conventions).

```kotlin
package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import org.junit.jupiter.api.Test

/**
 * Minimal two-scenario fixture used to exercise the `demoReport` pipeline
 * end-to-end. Both scenarios are trivial — they exist to produce two tagged
 * trace bundles so the ≥2-scenarios rule is satisfied.
 */
@Feature("smoke")
class DemoSmokeUiTest : BaseUiTest() {

    @Test
    fun happyPath() {
        takeScreenshot("smoke happy path")
    }

    @Test
    fun negativePath() {
        takeScreenshot("smoke negative path")
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/uiTest/kotlin/com/github/artem/pageobjectplugin/ui/tests/DemoSmokeUiTest.kt
git commit -m "test(task-19): DemoSmokeUiTest fixture with two @Feature(\"smoke\") scenarios"
```

---

### Task 3.4: GitHub Actions workflow

**Files:**
- Create: `.github/workflows/demo.yml`

- [ ] **Step 1: Write the workflow**

```yaml
name: demo-report

on:
  pull_request:
    types: [labeled]

jobs:
  demo-report:
    if: github.event.label.name == 'demo'
    runs-on: ubuntu-latest
    permissions:
      contents: read
      pull-requests: write
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Parse feature tag from PR title
        id: tag
        env:
          PR_TITLE: ${{ github.event.pull_request.title }}
        run: |
          set -euo pipefail
          if [[ "$PR_TITLE" =~ ^\[demo:([a-z0-9-]+)\] ]]; then
            echo "tag=${BASH_REMATCH[1]}" >> "$GITHUB_OUTPUT"
          else
            echo "tag=" >> "$GITHUB_OUTPUT"
          fi

      - name: Fail if tag missing
        if: steps.tag.outputs.tag == ''
        uses: actions/github-script@v7
        with:
          script: |
            await github.rest.issues.createComment({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number,
              body: 'demo-report: the `demo` label requires the PR title to start with `[demo:<tag>]` (e.g. `[demo:highlight-all] ...`). Re-add the label after fixing the title.',
            });
            core.setFailed('Missing [demo:<tag>] prefix in PR title.');

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Compute changed files
        id: changed
        env:
          BASE_REF: ${{ github.base_ref }}
        run: |
          set -euo pipefail
          git fetch origin "$BASE_REF" --depth=1
          FILES=$(git diff --name-only "origin/$BASE_REF...HEAD" | paste -sd, -)
          echo "files=$FILES" >> "$GITHUB_OUTPUT"

      - name: Run demoReport
        env:
          DEMO_BASE_REF: origin/${{ github.base_ref }}
        run: |
          ./gradlew demoReport \
            -PfeatureName=${{ steps.tag.outputs.tag }} \
            -PchangedFiles="${{ steps.changed.outputs.files }}"

      - name: Upload demo report
        id: upload
        uses: actions/upload-artifact@v4
        with:
          name: demo-report-${{ github.event.pull_request.head.sha }}
          path: build/reports/demo/**

      - name: Comment artifact link
        uses: actions/github-script@v7
        with:
          script: |
            const runUrl = `${context.serverUrl}/${context.repo.owner}/${context.repo.repo}/actions/runs/${context.runId}`;
            await github.rest.issues.createComment({
              owner: context.repo.owner,
              repo: context.repo.repo,
              issue_number: context.issue.number,
              body: `📽️ Demo report for \`${{ steps.tag.outputs.tag }}\` uploaded — download artifact \`demo-report-${{ github.event.pull_request.head.sha }}\` from [this workflow run](${runUrl}). Re-add the \`demo\` label to regenerate.`,
            });
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/demo.yml
git commit -m "ci(task-19): demo-report workflow triggered by demo PR label"
```

---

### Task 3.5: Open PR 3

- [ ] **Step 1: Push and open PR**

```bash
git push -u origin HEAD
gh pr create --title "task-19 PR3: demoReport Gradle task + CI workflow" --body "$(cat <<'EOF'
## Summary
- `./gradlew demoReport -PfeatureName=<tag>` selects tests by `@Feature` tag or package heuristic from changed files, runs them with `-PcaptureAllTraces=true`, and renders `build/reports/demo/<tag>/index.html`
- Enforces ≥2 scenarios per tag
- `.github/workflows/demo.yml` triggers on `demo` label, parses `[demo:<tag>]` from PR title, uploads the artifact, posts a PR comment
- `DemoSmokeUiTest` fixture provides two `@Feature("smoke")` scenarios for end-to-end verification
- Package heuristic is intentionally coarse: a change under `src/main/kotlin/com/foo/` selects tests under `src/uiTest/kotlin/com/foo/`. Shared-util changes will select broadly — acceptable for v1.

Final PR for Task 19.

## Test plan
- [x] `:buildSrc:test` passes locally (selector + renderer)
- [ ] `./gradlew demoReport -PfeatureName=smoke` produces `build/reports/demo/smoke/index.html` in CI
- [ ] Adding `demo` label to a PR with `[demo:smoke]` title uploads an artifact and posts a comment
- [ ] `test-report` CI job green
EOF
)"
```

- [ ] **Step 2: Verify the workflow on a throwaway label toggle**

Add the `demo` label to this PR (title already matches `[demo:smoke]` — if not, rename first). Confirm the workflow runs, the artifact is uploaded, and the PR comment appears. Remove + re-add the label to confirm regeneration.

- [ ] **Step 3: Wait for `test-report` CI job and iterate until green**

---

## Notes for the executor

- **Never bypass the Test Loop.** After each PR, push and wait for `test-report` before declaring done.
- **Do not delete failing tests** — fix the root cause.
- **No Claude attribution** in commits or PR bodies (per `CLAUDE.md`).
- **Follow UI Test Conventions** from `CLAUDE.md` when modifying any file under `src/uiTest/`: tests call Flows/Pages, use `Wait.pollUntil*`, no `Thread.sleep`.
- **`DemoReportRenderer.JsonPrimitive.contentOrNull()`** is a private helper — when kotlinx-serialization 1.6+ adds native `contentOrNull`, delete the helper.
