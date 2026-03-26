import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

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
                        "-Xmx2g",
                    )
                }
                // Open the test-project on IDE startup so tests don't need to navigate
                // the Welcome screen — IntelliJ accepts a project path as a program argument.
                args(rootDir.resolve("test-project").absolutePath)
            }
            plugins {
                robotServerPlugin(remoteRobotVersion)
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.0"
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
        systemProperty("ui.test.project.dir", rootDir.resolve("test-project").absolutePath)
    }
}
