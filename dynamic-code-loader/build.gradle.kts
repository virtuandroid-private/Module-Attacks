plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val dynamicCodeLibraryProject = ":dynamic-code-library"

//abstract class EncryptFileTask : DefaultTask() {
//    @get:InputFile
//    abstract val inputFile: RegularFileProperty
//
//    @get:OutputFile
//    abstract val outputFile: RegularFileProperty
//
//    // Encryption key passed as an input string (e.g., a 16, 24, or 32-byte secret)
//    @get:Input
//    abstract val secretKey: Property<String>
//
//    @TaskAction
//    fun encrypt() {
//        val inFile = inputFile.get().asFile
//        val outFile = outputFile.get().asFile
//
//        val rawKey = secretKey.get().toByteArray(Charsets.UTF_8)
//        require(rawKey.size in setOf(16, 24, 32)) {
//            "Secret key must be 16, 24, or 32 bytes long for AES."
//        }
//
//        val iv = ByteArray(12).apply {
//            SecureRandom().nextBytes(this)
//        }
//
//        val secretKeySpec: SecretKey = SecretKeySpec(rawKey, "AES")
//        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
//        val gcmSpec = GCMParameterSpec(128, iv) // 128-bit authentication tag
//        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmSpec)
//
//        val plainBytes = inFile.readBytes()
//        val encryptedBytes = cipher.doFinal(plainBytes)
//
//        outFile.parentFile.mkdirs()
//        outFile.writeBytes(iv + encryptedBytes)
//
//        logger.lifecycle("Successfully encrypted ${inFile.name} -> ${outFile.name}")
//    }
//}

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
            assets.directories.add(
                layout.buildDirectory.dir("generated/assets").get().asFile.absolutePath
            )
        }
    }
}

val copyPluginApk = tasks.register<Copy>("copyPluginApk") {
    val pluginProject = project(dynamicCodeLibraryProject)
    val assembleTask = pluginProject.tasks.named("build")

    dependsOn(assembleTask)

    val sourceDir = pluginProject.layout.buildDirectory.dir("intermediates/apk/debug")
    val targetDir = layout.buildDirectory.dir("generated/pre-assets")

    inputs.dir(sourceDir)
    outputs.dir(targetDir)

    from(sourceDir) {
        include("*.apk")
        rename { "dynamic.apk" }
    }
    into(targetDir)
}

// afterEvaluate required due to dynamic task registration
afterEvaluate {
    tasks.named("generateDebugAssets") {
        dependsOn(copyPluginApk)
    }
}

tasks.named("clean") {
    dependsOn("$dynamicCodeLibraryProject:clean")
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