import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.intellij.platform")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
}

group = "com.canbaycay"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // The IDE version to compile against. This downloads the platform SDK
        // so Gradle can resolve API classes during compilation.
        // useInstaller must be false for Rider — the installer distribution is not supported.
        // See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1852
        rider("2025.3.3") { useInstaller = false }
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // Minimum IDE build the plugin is compatible with (243 = 2024.3.x).
            // This is written into plugin.xml and checked by the Marketplace and IDEs.
            sinceBuild = "243"
            // No upper bound — the plugin is compatible with all future versions.
            untilBuild = provider { null }
        }
    }

    // Marketplace requires plugins to be signed. Set these env vars before running signPlugin/publishPlugin.
    signing {
        certificateChainFile = file(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKeyFile = file(providers.environmentVariable("PRIVATE_KEY"))
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

kotlin {
    jvmToolchain(21)
}
