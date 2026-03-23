import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    id("idea")
    application

    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.bundles.implementation)
    runtimeOnly(libs.slf4j.simple)
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(25)
        @Suppress("UnstableApiUsage")
        vendor = JvmVendorSpec.JETBRAINS
    }
}

application {
    mainClass = "io.github.valine3gdev.mcguildlink.app.AppKt"
}

tasks {
    named<JavaExec>("run") {
        jvmArgs = listOf("-XX:+AllowEnhancedClassRedefinition")
    }
    withType<Test>().configureEach {
        useJUnitPlatform()

        testLogging {
            events(
                TestLogEvent.FAILED,
                TestLogEvent.PASSED,
                TestLogEvent.SKIPPED
            )
        }
    }
}

idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
