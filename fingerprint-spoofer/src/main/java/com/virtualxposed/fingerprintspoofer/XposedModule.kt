@file:JvmName("XposedModule") // Prevent kotlin from renaming the file
package com.virtualxposed.fingerprintspoofer

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "FingerprintSpoofer"
        const val VICTIM_APP = "com.virtualxposed.victim"

        private fun log(message: String) {
            XposedBridge.log("$TAG: $message")
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

        log("Loaded malicious module $TAG version ${BuildConfig.VERSION_NAME} to package: ${params?.packageName}")

        hookFingerprint(params)
    }

    fun hookFingerprint(params: XC_LoadPackage.LoadPackageParam) {
        try {
            val buildClass = XposedHelpers.findClass(
                "android.os.Build",
                params.classLoader
            )

            XposedHelpers.setStaticObjectField(
                buildClass,
                "FINGERPRINT",
                "SPOOFED FINGERPRINT"
            )

            XposedHelpers.setStaticObjectField(
                buildClass,
                "MODEL",
                "SPOOFED MODEL"
            )

            XposedHelpers.setStaticObjectField(
                buildClass,
                "BRAND",
                "SPOOFED BRAND"
            )

            log("installed")
        } catch (t: Throwable) {
            log("error: $t")
        }
    }

}