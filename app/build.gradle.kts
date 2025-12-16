plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
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
    implementation(project(":runtime"))
    implementation(compose.desktop.currentOs)
    implementation("io.insert-koin:koin-core:3.5.3")
}

compose.desktop {
    application {
        mainClass = "com.example.expression.app.MainKt"
    }
}

tasks.test {
    useJUnitPlatform()
}
