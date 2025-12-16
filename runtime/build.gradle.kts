plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
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
