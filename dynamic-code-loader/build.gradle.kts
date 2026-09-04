import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object BuildConstants {
    // Sample key for demo
    const val ENCRYPTION_KEY = "0123456789012345"
    const val LIBRARY_PROJECT = ":dynamic-code-library"
    const val NORMAL_APK_NAME = "dynamic.apk"
    const val ENCRYPTED_APK_NAME = "encrypted.apk"
    const val SO_FILE_NAME = "libdynamic.so"
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}



abstract class EncryptFileTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    // Encryption key passed as an input string (e.g., a 16, 24, or 32-byte secret)
    @get:Input
    abstract val secretKey: Property<String>

    @TaskAction
    fun encrypt() {
        val inFile = inputFile.get().asFile
        val outFile = outputFile.get().asFile

        val rawKey = secretKey.get().toByteArray(Charsets.UTF_8)
        require(rawKey.size in setOf(16, 24, 32)) {
            "Secret key must be 16, 24, or 32 bytes long for AES."
        }

        val iv = ByteArray(12).apply {
            SecureRandom().nextBytes(this)
        }

        val secretKeySpec: SecretKey = SecretKeySpec(rawKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit authentication tag
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec)

        val plainBytes = inFile.readBytes()
        val encryptedBytes = cipher.doFinal(plainBytes)

        outFile.parentFile.mkdirs()
        // It is fine if IV is known
        outFile.writeBytes(iv + encryptedBytes)

        logger.lifecycle("Successfully encrypted ${inFile.name} -> ${outFile.name}")
    }
}

android {
    namespace = "com.virtualxposed.codeloader"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.virtualxposed.codeloader"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        all {
            buildConfigField("String", "DYNAMIC_FILE_NAME", "\"${BuildConstants.NORMAL_APK_NAME}\"")
            buildConfigField(
                "String",
                "ENCRYPTED_FILE_NAME",
                "\"${BuildConstants.ENCRYPTED_APK_NAME}\""
            )
            buildConfigField("String", "ENCRYPTION_KEY", "\"${BuildConstants.ENCRYPTION_KEY}\"")
            buildConfigField("String", "SO_FILE_NAME", "\"${BuildConstants.SO_FILE_NAME}\"")
        }

        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            // Include the directory where the built APK will reside
            assets.directories.addAll(
                listOf(
                    // Different dirs to make Gradle happy, otherwise they generate implicit dependencies
                    layout.buildDirectory.dir("generated/apk-assets").get().asFile.absolutePath,
                    layout.buildDirectory.dir("generated/so-assets").get().asFile.absolutePath
                )
            )
        }
    }
}

val copyPluginApk = tasks.register<Copy>("copyPluginApk") {
    val pluginProject = project(BuildConstants.LIBRARY_PROJECT)
    val assembleTask = pluginProject.tasks.named("assembleDebug")

    dependsOn(assembleTask)

    // The apk can be found in either of these
    val sourceDirs = arrayOf(
        pluginProject.layout.buildDirectory.dir("intermediates/apk/debug"),
        pluginProject.layout.buildDirectory.dir("outputs/apk/debug")
    )

    from(sourceDirs) {
        include("*.apk")
        rename { BuildConstants.NORMAL_APK_NAME }
    }

    into(layout.buildDirectory.dir("generated/apk-assets"))
}

val copyPluginNativeCode = tasks.register<Copy>("copyPluginNativeCode") {
    val pluginProject = project(BuildConstants.LIBRARY_PROJECT)
    val assembleTask = pluginProject.tasks.named("mergeDebugNativeLibs")

    dependsOn(assembleTask)

    val sourceDir =
        pluginProject.layout.buildDirectory.dir("intermediates/merged_native_libs/debug/mergeDebugNativeLibs/out/lib")
    val targetDir = layout.buildDirectory.dir("generated/so-assets")

    from(sourceDir) {
        include("**/${BuildConstants.SO_FILE_NAME}")

        eachFile {
            // Extracts the parent folder name (e.g., "x86", "arm64-v8a")
            val arch = file.parentFile.name
            // Renames "libdynamic.so" -> "x86_libdynamic.so"
            name = "${arch}_$name"
            // Flattens the output into targetDir instead of keeping the subfolder structure
            path = name
        }
    }
    into(targetDir)

    // Disables empty directory preservation since files are flattened to root targetDir
    includeEmptyDirs = false
}


val encryptPluginApk = tasks.register<EncryptFileTask>("encryptPluginApk") {
    dependsOn(copyPluginApk)

    secretKey = BuildConstants.ENCRYPTION_KEY
    outputFile = layout.buildDirectory.dir("generated/apk-assets")
        .map { it.file(BuildConstants.ENCRYPTED_APK_NAME) }

    inputFile = copyPluginApk.flatMap { copyTask ->
        layout.dir(provider { copyTask.destinationDir }).map { dir ->
            dir.file(BuildConstants.NORMAL_APK_NAME)
        }
    }
}


// afterEvaluate required due to dynamic task registration
afterEvaluate {
    tasks.named("preBuild") {
        dependsOn(encryptPluginApk)
        dependsOn(copyPluginNativeCode)
    }
}

tasks.named("clean") {
    dependsOn("${BuildConstants.LIBRARY_PROJECT}:clean")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    compileOnly(libs.exposed.xposedapi)
    compileOnly(libs.okhttp)
}