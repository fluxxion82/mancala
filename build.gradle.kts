import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

group = "ai.sterling"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "mancala"
        browser {}
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.io.core)
                implementation(libs.lifecycle.viewmodel)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("io.kotlintest:kotlintest-runner-junit5:3.3.2")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-api:5.5.2")
                implementation("org.junit.jupiter:junit-jupiter-params:5.5.2")
            }
        }
        val wasmJsMain by getting {
            // No extra deps; wasm-side actuals only need stdlib + browser globals.
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "ai.sterling.mancala.resources"
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

// Run once after retraining the model to regenerate the cross-platform weight blob:
//   ./gradlew convertWeights
// Reads model_source/mancala_weights.npz (the artifact from the Python training run)
// and writes the flat .bin into commonMain composeResources so it ships to every target.
val convertWeights by tasks.registering(JavaExec::class) {
    description = "Convert model_source/mancala_weights.npz → commonMain composeResources/files/mancala_weights.bin"
    group = "build"

    val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
    classpath = jvmMainCompilation.output.allOutputs + jvmMainCompilation.runtimeDependencyFiles
    mainClass.set("ai.sterling.build.WeightsConverterKt")
    dependsOn(jvmMainCompilation.compileTaskProvider)

    val npzIn = layout.projectDirectory.file("model_source/mancala_weights.npz")
    val binOut = layout.projectDirectory.file("src/commonMain/composeResources/files/mancala_weights.bin")
    inputs.file(npzIn)
    outputs.file(binOut)
    args(npzIn.asFile.absolutePath, binOut.asFile.absolutePath)
}
