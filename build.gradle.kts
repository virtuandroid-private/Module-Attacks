plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
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

subprojects {
    val apkFile = project.layout.buildDirectory.file("outputs/apk/debug/${project.name}-debug.apk")
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
}