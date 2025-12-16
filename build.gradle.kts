buildscript {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("org.jetbrains.compose:compose-gradle-plugin:1.5.12")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:12.1.0")
    }
}

allprojects {
    group = "com.example.expression"
    version = "1.0.0"
}

subprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
