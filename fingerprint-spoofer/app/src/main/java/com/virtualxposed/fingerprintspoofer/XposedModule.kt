@file:JvmName("XposedModule") // Prevent kotlin from renaming the file
package com.virtualxposed.fingerprintspoofer

import android.app.Application
import android.content.Context
import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "FingerprintSpoofer"

        private fun log(message: String) {
            XposedBridge.log("$TAG: $message")
        }
    }

    override fun handleLoadPackage(params: XC_LoadPackage.LoadPackageParam?) {
        log("Loaded malicious module $TAG version ${BuildConfig.VERSION_NAME} to package: ${params?.packageName}")

        if (params == null) return

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