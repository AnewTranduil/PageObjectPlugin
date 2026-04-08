plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    // Runtime-only kotlinx-serialization-json (NO compiler plugin):
    // we only use Json.parseToJsonElement / buildJsonObject / JsonElement,
    // none of which need codegen. Pulling in the
    // `org.jetbrains.kotlin.plugin.serialization` Gradle plugin would
    // conflict with `kotlin-dsl`'s embedded Kotlin compiler (Gradle 9.4
    // ships Kotlin ~2.0.21 and the serialization plugin's BOM forces a
    // newer kotlin-gradle-plugin-api that has no matching Gradle variant).
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
