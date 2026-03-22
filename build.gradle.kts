plugins {
    id("org.jetbrains.kotlin.jvm") version providers.gradleProperty("kotlinVersion").get()
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("org.jsoup:jsoup:1.17.2")

    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        instrumentationTools()
        pluginVerifier()
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        group = providers.gradleProperty("pluginGroup")
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
            ide("IC", providers.gradleProperty("platformVersion").get())
            ide("WS", providers.gradleProperty("platformVersion").get())
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "9.0"
    }
}
