import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// ── UI Test source set ────────────────────────────────────────────────────────
val uiTest by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}

val robotServerPlugin: Configuration by configurations.creating { isTransitive = false }

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

    // Robot server plugin zip — extracted and loaded into IDE sandbox at test time
    robotServerPlugin("com.intellij.remoterobot:robot-server-plugin:0.11.22@zip")

    // UI test client
    "uiTestImplementation"("com.intellij.remoterobot:remote-robot:0.11.22")
    "uiTestImplementation"("com.intellij.remoterobot:remote-fixtures:0.11.22")
    "uiTestImplementation"("org.junit.jupiter:junit-jupiter:5.10.1")
    "uiTestImplementation"("com.squareup.okhttp3:okhttp:4.12.0")
    "uiTestImplementation"("com.squareup.okhttp3:logging-interceptor:4.12.0")
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

/** Extracts robot-server-plugin zip so it can be passed via -Dplugin.path. */
val extractRobotPlugin by tasks.registering(Sync::class) {
    group = "intellij platform"
    description = "Extracts the Remote Robot server plugin for IDE sandbox installation"
    from(provider { zipTree(robotServerPlugin.singleFile) })
    into(layout.buildDirectory.dir("robot-server-plugin"))
}

tasks {
    wrapper {
        gradleVersion = "9.0"
    }

    /**
     * Launches a sandboxed IDE instance with the robot-server-plugin loaded.
     * Keep this running while executing the `uiTest` task in a second terminal.
     *
     * Usage:
     *   Terminal 1: ./gradlew runIdeForUiTests
     *   Terminal 2: ./gradlew uiTest
     */
    register<RunIdeTask>("runIdeForUiTests") {
        dependsOn(extractRobotPlugin)

        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
        systemProperty("idea.trust.all.projects", "true")
        systemProperty("eap.require.license", "false")
        jvmArgs("-Xmx2g")

        // Open the test-project on IDE startup so tests don't need to navigate
        // the Welcome screen — IntelliJ accepts a project path as a program argument.
        args(rootDir.resolve("test-project").absolutePath)

        doFirst {
            // Locate the extracted plugin directory (one subdir inside the zip root)
            // and add it via -Dplugin.path so IntelliJ loads it alongside sandbox plugins.
            val pluginBase = layout.buildDirectory.dir("robot-server-plugin").get().asFile
            val pluginDir = pluginBase.listFiles()?.firstOrNull { it.isDirectory }
                ?: pluginBase  // fallback: point to the base if structure is flat
            jvmArgs("-Dplugin.path=${pluginDir.absolutePath}")
        }
    }

    /** Runs UI tests — requires `runIdeForUiTests` already listening on port 8082. */
    register<Test>("uiTest") {
        description = "Runs UI (end-to-end) tests against a running IDE instance on port 8082."
        group = "verification"
        testClassesDirs = sourceSets["uiTest"].output.classesDirs
        classpath = sourceSets["uiTest"].runtimeClasspath
        useJUnitPlatform()
        systemProperty("robot-server.url", System.getProperty("robot-server.url", "http://localhost:8082"))
        systemProperty("ui.test.project.dir", rootDir.resolve("test-project").absolutePath)
    }
}
