plugins {
    id("java")
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.moiseskerschner"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Ultimate (not Community) because TerminalOutputModel / reworked-terminal APIs and the
        // bundled GitLab plugin we reverse-engineered are Ultimate-only. Matches the user's real
        // IDE (Ultimate). 2026.1.4 chosen because it's already cached locally.
        intellijIdeaUltimate("2026.1.4")
        bundledPlugin("org.jetbrains.plugins.terminal")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
