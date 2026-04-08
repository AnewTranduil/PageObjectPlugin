import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.changelog") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

changelog {
    version = providers.gradleProperty("pluginVersion")
    groups.set(listOf("Added", "Changed", "Fixed", "Removed"))
    repositoryUrl = "https://github.com/AnewTranduil/PageObjectPlugin"
}

val remoteRobotVersion = "0.11.23"

// ── UI Test source set ────────────────────────────────────────────────────────
val uiTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

configurations {
    named("uiTestImplementation") { extendsFrom(configurations.implementation.get()) }
    named("uiTestRuntimeOnly") { extendsFrom(configurations.runtimeOnly.get()) }
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    // JetBrains hosted dependencies (Remote Robot)
    maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.assertj:assertj-core:3.25.3")

    // UI test client — version must match robotServerPlugin() resolved version
    "uiTestImplementation"("com.intellij.remoterobot:remote-robot:$remoteRobotVersion")
    "uiTestImplementation"("com.intellij.remoterobot:remote-fixtures:$remoteRobotVersion")
    "uiTestImplementation"("org.junit.jupiter:junit-jupiter:5.10.1")
    "uiTestImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
    "uiTestImplementation"("com.squareup.okhttp3:logging-interceptor:4.12.0")
    "uiTestImplementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    "uiTestRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = "243"
        }

        changeNotes = provider {
            with(changelog) {
                renderItem(
                    (getOrNull(providers.gradleProperty("pluginVersion").get()) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    org.jetbrains.changelog.Changelog.OutputType.HTML,
                )
            }
        }
    }

    publishing {
        // Configure token via PUBLISH_TOKEN environment variable
    }

    pluginVerification {
        ides {
            create(IntelliJPlatformType.IntellijIdeaCommunity, providers.gradleProperty("platformVersion").get())
            create(IntelliJPlatformType.WebStorm, providers.gradleProperty("platformVersion").get())
        }
    }
}

// ── Remote Robot tasks ───────────────────────────────────────────────────────

/**
 * Launches a sandboxed IDE instance with the robot-server-plugin loaded.
 * Keep this running while executing the `uiTest` task in a second terminal.
 *
 * Usage:
 *   Terminal 1: ./gradlew runIdeForUiTests
 *   Terminal 2: ./gradlew uiTest
 */
intellijPlatformTesting {
    runIde {
        register("runIdeForUiTests") {
            task {
                jvmArgumentProviders += CommandLineArgumentProvider {
                    listOf(
                        "-Drobot-server.port=8082",
                        "-Dide.mac.message.dialogs.as.sheets=false",
                        "-Djb.privacy.policy.text=<!--999.999-->",
                        "-Djb.consents.confirmation.enabled=false",
                        "-Didea.trust.all.projects=true",
                        "-Deap.require.license=false",
                        "-Dide.show.tips.on.startup.default.value=false",
                        "-Dide.browser.jcef.sandbox.enable=false",
                        // Expose JCEF Chrome DevTools Protocol so TraceBundleExtension
                        // can subscribe to Runtime.consoleAPICalled and capture the
                        // tool window's console output on test failure.
                        "-Dide.browser.jcef.debug.port=9222",
                        "-Djava.awt.headless=false",
                        "-Dsun.java2d.xrender=false",
                        "-Xmx2g",
                    )
                }
                // Open the test-project on IDE startup so tests don't need to navigate
                // the Welcome screen — IntelliJ accepts a project path as a program argument.
                args(rootDir.resolve("packages/test-project").absolutePath)
            }
            plugins {
                robotServerPlugin(remoteRobotVersion)
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.4"
    }

    /** Runs UI tests — requires `runIdeForUiTests` already listening on port 8082. */
    register<Test>("uiTest") {
        description = "Runs UI (end-to-end) tests against a running IDE instance on port 8082."
        group = "verification"
        testClassesDirs = sourceSets["uiTest"].output.classesDirs
        classpath = sourceSets["uiTest"].runtimeClasspath
        useJUnitPlatform()
        // Required for Retrofit/GSON reflection on JDK 17+
        jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
        systemProperty("robot-server.url", System.getProperty("robot-server.url", "http://localhost:8082"))
        systemProperty("ui.test.project.dir", rootDir.resolve("packages/test-project").absolutePath)
        // -PcaptureAllTraces=true forces TraceBundleExtension to write a bundle
        // for every test (passing or failing). Default writes only on failure.
        systemProperty(
            "ui.test.captureAllTraces",
            providers.gradleProperty("captureAllTraces").orElse("false").get(),
        )
        // Sandbox path so TraceBundleExtension can locate idea.log under
        // build/idea-sandbox/system/log/ regardless of IPG layout changes.
        systemProperty(
            "ui.test.sandbox.dir",
            layout.buildDirectory.dir("idea-sandbox").get().asFile.absolutePath,
        )
        // Keep the trace index in sync with every uiTest run. `finalizedBy`
        // so the index is regenerated even on test failure.
        finalizedBy("generateTraceIndex")
    }

    /**
     * Scans `build/reports/uiTest/traces/<Class>__<method>/` and produces a
     * centralized `index.html` tabulating every trace bundle with links into
     * each bundle's artifacts (trace.json, idea.log, dom.html, jcef-console,
     * threads.txt). Runs via the `uiTest` source set classpath so it reuses
     * the existing kotlinx-serialization setup.
     */
    register<JavaExec>("generateTraceIndex") {
        description = "Generates build/reports/uiTest/traces/index.html from trace.json bundles."
        group = "verification"
        classpath = sourceSets["uiTest"].runtimeClasspath
        mainClass.set("com.github.artem.pageobjectplugin.ui.support.TraceIndexGeneratorKt")
        args = listOf(
            layout.buildDirectory.dir("reports/uiTest/traces").get().asFile.absolutePath,
        )
        // Never fail the build just because the index couldn't render.
        isIgnoreExitValue = true
    }

    // ── Task 14: CI test reporting + Claude inner loop ────────────────────
    //
    // Runs the Playwright suite from the snapshot-saver package. The JSON
    // reporter (configured in `packages/playwright-snapshot-saver/playwright.config.ts`)
    // writes machine-readable results to `test-results/results.json`, which
    // the aggregator below consumes.
    register<Exec>("playwrightTest") {
        description = "Runs Playwright tests under packages/playwright-snapshot-saver."
        group = "verification"
        workingDir = rootDir.resolve("packages/playwright-snapshot-saver")
        commandLine("npx", "playwright", "test")
    }

    /**
     * Parses every test surface (unit, uiTest, playwright) and writes
     * `build/reports/claude-summary.{json,md}`. Lives in `buildSrc/` so it
     * has zero dependency on the IntelliJ Platform classpath and can run
     * even when no test source set has compiled. See
     * `docs/tasks/task-14-ci-test-reporting.md` for the schema and the
     * "Test Loop" section in `CLAUDE.md` for how it's consumed.
     */
    register("aggregateTestReport") {
        description = "Aggregates test results into build/reports/claude-summary.{json,md}."
        group = "verification"
        val rootDirFile = rootDir
        val buildDirFile = layout.buildDirectory.get().asFile
        doLast {
            val gitSha: String? = System.getenv("GITHUB_SHA")?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    ProcessBuilder("git", "rev-parse", "HEAD")
                        .directory(rootDirFile)
                        .redirectErrorStream(true)
                        .start()
                        .inputStream.bufferedReader().readText().trim().ifEmpty { null }
                }.getOrNull()
            val summary = com.github.artem.pageobjectplugin.buildtools.ClaudeSummaryGenerator.run(
                rootDir = rootDirFile,
                buildDir = buildDirFile,
                gitSha = gitSha,
            )
            logger.lifecycle(
                "claude-summary: ${summary.totals.passed} passed, ${summary.totals.failed} failed, " +
                    "${summary.totals.skipped} skipped, ${summary.totals.flaky} flaky " +
                    "→ build/reports/claude-summary.{json,md}",
            )
            if (summary.totals.failed > 0) {
                throw GradleException(
                    "${summary.totals.failed} test(s) failed — see build/reports/claude-summary.md",
                )
            }
        }
    }

    /**
     * Developer entry point. Runs every test surface and then aggregates.
     * The `whenReady` hook below makes the per-suite tasks tolerate
     * failures so the aggregator always runs and emits the summary, while
     * `aggregateTestReport` itself fails the build if any test failed.
     *
     * The canonical "did my change break anything?" loop is the CI run for
     * the branch (see CLAUDE.md "Test Loop"). This task is only for
     * iterating on a single suite locally.
     */
    register("testReport") {
        description = "Runs all tests and emits build/reports/claude-summary.{json,md}."
        group = "verification"
        dependsOn("test", "uiTest", "playwrightTest")
        finalizedBy("aggregateTestReport")
    }
}

// When `testReport` is the entry point, let every per-suite task complete
// (even on failure) so the aggregator gets to read all three sets of XML
// / JSON outputs. Standalone `./gradlew test` (or `uiTest`, etc.) is
// unaffected and still fails loudly.
gradle.taskGraph.whenReady {
    if (hasTask(":testReport")) {
        tasks.named<Test>("test") { ignoreFailures = true }
        tasks.named<Test>("uiTest") { ignoreFailures = true }
        tasks.named<Exec>("playwrightTest") { isIgnoreExitValue = true }
    }
}
