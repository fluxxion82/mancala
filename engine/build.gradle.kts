import java.io.FileInputStream
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

// Compose-free engine: rules, model, search (alpha-beta + PUCT-MCTS), neural-net
// inference, weight parsing, and the UI-agnostic GameSession. Must not depend on
// org.jetbrains.compose / androidx.compose / lifecycle so non-Compose-UI front ends
// (e.g. a Mosaic terminal UI) can use it.
plugins {
    kotlin("multiplatform")
}

group = "ai.sterling"
version = "1.0-SNAPSHOT"

kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // Karma's webpack bundle fails to load ES-module wasm test binaries
            // ("Cannot use 'import.meta' outside a module"); the engine has no DOM
            // dependencies, so its wasm tests run on Node instead.
            testTask { enabled = false }
        }
        nodejs()
    }

    sourceSets {
        commonMain.dependencies {
            // Public API exposes StateFlow / SharedFlow / CoroutineScope.
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// The trained weights live in the UI module's Compose Resources (that path is what
// the website serves). Engine tests read the same file straight from disk.
tasks.withType<Test>().configureEach {
    systemProperty(
        "mancala.weights",
        rootProject.file("src/commonMain/composeResources/files/mancala_weights.bin").absolutePath,
    )
}

// MANCALA_WEIGHTS_VERSION: a short SHA-256 of the uncompressed weights shipped by the UI module.
// Front ends use it as the cache key for downloaded weights, so it lives here, public, where any
// UI (Compose or terminal) can read it.
val generateWeightsVersion by tasks.registering {
    description = "Generate MancalaWeightsVersion.kt with a SHA-256 of the uncompressed weights"
    group = "build"

    val binIn = rootProject.layout.projectDirectory.file("src/commonMain/composeResources/files/mancala_weights.bin")
    val outDir = layout.buildDirectory.dir("generated/source/weightsVersion/commonMain")
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
        val short = digest.digest().joinToString("") { "%02x".format(it) }.substring(0, 16)
        val file = outDir.get().asFile.resolve("ai/sterling/loading/MancalaWeightsVersion.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package ai.sterling.loading
            |
            |const val MANCALA_WEIGHTS_VERSION: String = "$short"
            |
            """.trimMargin(),
        )
    }
}

kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generateWeightsVersion)
}
