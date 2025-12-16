plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation("io.insert-koin:koin-core:3.5.3")
    testImplementation(kotlin("test"))
    testImplementation(project(":core"))
}

tasks.test {
    useJUnitPlatform()
}
