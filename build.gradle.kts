import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

group = "ai.sterling"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                //implementation("androidx.compose.ui:ui-tooling-preview:1.1.1")

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.swing)

                implementation(libs.lifecycle.viewmodel)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
                // implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlin_version")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "mancala"
            packageVersion = "1.0.0"
        }
    }
}
