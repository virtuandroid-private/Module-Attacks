plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.virtualxposed.maliciousmodule"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.virtualxposed.maliciousmodule"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "2.0.3"

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
}

val apkFile = project.layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
val virtualXposedPackage = "io.va.exposed64"
val tmpApkPath = "/data/local/tmp/${project.name}.apk"
val internalApkPath = "/data/user/0/$virtualXposedPackage/cache/${project.name}.apk"

// TODO Move to gradle dependency?
val restartXposed = tasks.register<Exec>(
    "restartXposed",
) {
    commandLine(
        "adb", "shell", "am", "broadcast",
        "-a", "$virtualXposedPackage.CMD",
        "--es", "cmd", "reboot",
        "-n", "$virtualXposedPackage/.dev.CmdReceiver"
    )
}
val pushApkToTmp = tasks.register<PushApkTask>(
    "pushApkToTmp",
) {
    dependsOn(tasks.getByName("assembleDebug"))

    apk.set(apkFile)
    destination.set(tmpApkPath)
}

val pushApkToXposed = tasks.register<Exec>(
    "pushApkToXposed",
) {
    dependsOn(pushApkToTmp)

    // We want to install to Xposed without popup dialog
    // To do this safely we must "authenticate" as ADB.
    // The best way of doing this is by simply pushing the APK into the app cache directory.
    commandLine(
        "adb", "shell", "run-as", virtualXposedPackage,
        "cp $tmpApkPath $internalApkPath",
    )
}


abstract class PushApkTask : Exec() {
    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:Input
    abstract val destination: Property<String>

    @TaskAction
    fun push() {
        val apk = apk.get().asFile
        check(apk.exists()) {
            "APK not found: ${apk.absolutePath}"
        }

        commandLine(
            "adb",
            "push",
            apk.absolutePath,
            destination.get()
        )
        super.exec()
    }
}


tasks.register<Exec>(
    "installToXposed",
) {
    description = "Install the module to VirtualXposed instantly."

    dependsOn(pushApkToXposed)
    finalizedBy(restartXposed)

    val installArgs = listOf(
        "adb", "shell", "am", "start",
        "-a", "android.intent.action.VIEW",
        "-c", "android.intent.category.DEFAULT",
        "-d", "file://${internalApkPath}",
        "-t", "application/vnd.android.package-archive",
        "-n", "${virtualXposedPackage}/vxp.installer"
    )

    commandLine(installArgs)
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