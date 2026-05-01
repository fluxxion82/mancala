import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
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

// Gzips mancala_weights.bin → mancala_weights.bin.gz next to it. The wasmJs target
// fetches the .gz via raw fetch() and decompresses with the browser's native
// DecompressionStream; JVM/desktop still reads the uncompressed .bin.
val compressWeights by tasks.registering {
    description = "Gzip src/commonMain/composeResources/files/mancala_weights.bin → mancala_weights.bin.gz"
    group = "build"

    val binIn = layout.projectDirectory.file("src/commonMain/composeResources/files/mancala_weights.bin")
    val gzOut = layout.projectDirectory.file("src/commonMain/composeResources/files/mancala_weights.bin.gz")
    inputs.file(binIn)
    outputs.file(gzOut)

    doLast {
        val src = binIn.asFile
        val dst = gzOut.asFile
        dst.parentFile.mkdirs()
        // Maximum compression — this runs once per retraining, so spend the CPU.
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { rawOut ->
                object : GZIPOutputStream(rawOut) {
                    init { def.setLevel(Deflater.BEST_COMPRESSION) }
                }.use { gzip -> input.copyTo(gzip) }
            }
        }
        logger.lifecycle("compressWeights: ${src.length()} → ${dst.length()} bytes")
    }
}

// Hashes the uncompressed weights so we can derive a version string for the
// IndexedDB cache key. The .bin contents fully determine the hash — retraining
// changes it, which forces a fresh download and overwrites the stale cache entry.
val generateWeightsVersion by tasks.registering {
    description = "Generate MancalaWeightsVersion.kt with a SHA-256 of the uncompressed weights"
    group = "build"

    val binIn = layout.projectDirectory.file("src/commonMain/composeResources/files/mancala_weights.bin")
    val outDir = layout.buildDirectory.dir("generated/source/weightsVersion/commonMain/ai/sterling/loading")
    inputs.file(binIn)
    outputs.dir(outDir)

    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(binIn.asFile).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        val short = hex.substring(0, 16)
        val out = outDir.get().asFile
        out.mkdirs()
        out.resolve("MancalaWeightsVersion.kt").writeText(
            """
            |package ai.sterling.loading
            |
            |internal const val MANCALA_WEIGHTS_VERSION: String = "$short"
            |
            """.trimMargin(),
        )
    }
}

// Wire generated source dir + ensure resources contain the gzipped + versioned blobs
// before any Kotlin compilation or resource processing runs.
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateWeightsVersion.map { layout.buildDirectory.dir("generated/source/weightsVersion/commonMain").get().asFile })
}

tasks.matching { it.name.startsWith("compile") && it.name.contains("Kotlin") }.configureEach {
    dependsOn(generateWeightsVersion)
}

tasks.matching {
    // Cover all per-target processResources tasks so compressWeights runs before
    // the .gz lands in the build output.
    it.name == "processResources" ||
        it.name.endsWith("ProcessResources") ||
        it.name.endsWith("ProcessAllResources") ||
        it.name == "wasmJsProcessResources" ||
        it.name == "jvmProcessResources"
}.configureEach {
    dependsOn(compressWeights)
}

// Compose Resources copies composeResources/ into a generated dir first; depend on
// compressWeights so the .gz exists before that copy runs.
tasks.matching { it.name.startsWith("copyNonXmlValueResourcesFor") || it.name.startsWith("prepareComposeResources") }.configureEach {
    dependsOn(compressWeights)
}
