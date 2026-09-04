package com.virtualxposed.guestattacker

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import com.virtualxposed.guestattacker.StorageBypass.VICTIM_APP
import com.virtualxposed.guestattacker.Utils.log
import com.virtualxposed.guestattacker.Utils.toast
import com.virtualxposed.victim.IPrivateService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

object IPC {
    private val privateService = MutableStateFlow<IPrivateService?>(null)

    fun sendMessage(context: Context): Boolean {
        val responseMessage = privateService.value?.sendMessage("Attack message!")
        if (responseMessage == null) {
            toast(context, "No response from Victim app.")
        } else {
            toast(context, "Got response from Victim app: $responseMessage")
        }
        return responseMessage != null
    }

    suspend fun messageVictimApp(context: Context): Boolean {
        val service = privateService
        if (service.value != null) {
            return sendMessage(context)
        }

        val intent = Intent().apply {
            this.setPackage(VICTIM_APP)
            this.setComponent(
                ComponentName(
                    VICTIM_APP, "$VICTIM_APP.PrivateService"
                )
            )
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?, service: IBinder?
            ) {
                privateService.value = IPrivateService.Stub.asInterface(service)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
            }
        }

        runCatching {
            val iServiceConnection = getIServiceConnection(
                context, connection, BIND_AUTO_CREATE.toLong()
            )
            if (iServiceConnection == null) {
                log("No iServiceConnection")
                toast(context, "Unable to connect to Victim app. Is it installed?")
                return false
            }
            bindServiceIntended(
                context, intent, iServiceConnection, BIND_AUTO_CREATE, 0
            )
        }

        // Wait for 10 seconds to start the service
        withTimeoutOrNull(10.seconds) {
            service.filterNotNull().first()
        }

        return sendMessage(context)
    }


    private fun bindServiceRaw(
        context: Context,
        intent: Intent,
        iServiceConnection: Any,
        flags: Int,
        userId: Int = 0,
    ): Int {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod =
                activityThreadClass.getDeclaredMethod("currentActivityThread")
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            val getApplicationThreadMethod =
                activityThreadClass.getDeclaredMethod("getApplicationThread")
            val applicationThread =
                getApplicationThreadMethod.invoke(currentActivityThread) as IInterface

            val iServiceConnectionClass = Class.forName("android.app.IServiceConnection")

            val classLoader = MainApplication.hostClassLoader ?: return -1
            val vActivityManagerClass =
                classLoader.loadClass("com.lody.virtual.client.ipc.VActivityManager")
            val staticGetter = vActivityManagerClass.getMethod("get")
            val vActivityManager = staticGetter.invoke(null)

            val bindServiceMethod = vActivityManager.javaClass.getMethod(
                "bindService",
                IBinder::class.java,
                IBinder::class.java,
                Intent::class.java,
                String::class.java,
                iServiceConnectionClass,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            val resolvedType = intent.resolveTypeIfNeeded(context.contentResolver)

            val result = bindServiceMethod.invoke(
                vActivityManager,
                applicationThread.asBinder(),
                null,
                intent,
                resolvedType,
                iServiceConnection,
                flags,
                userId
            )

            result as Int
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }

    @SuppressLint("SoonBlockedPrivateApi")
    private fun getIServiceConnection(
        context: Context, connection: ServiceConnection, flags: Long
    ): Any? {
        return try {
            var implContext = context
            while (implContext is android.content.ContextWrapper) {
                implContext = implContext.baseContext
            }

            val contextImplClass = Class.forName("android.app.ContextImpl")
            val packageInfoField =
                contextImplClass.getDeclaredField("mPackageInfo").apply { isAccessible = true }
            val loadedApk = packageInfoField.get(implContext)

            val mainHandlerMethod = contextImplClass.getDeclaredMethod("getMainThreadHandler")
                .apply { isAccessible = true }
            val handler = mainHandlerMethod.invoke(implContext) as Handler

            val loadedApkClass = Class.forName("android.app.LoadedApk")
            val getServiceDispatcherMethod = loadedApkClass.getDeclaredMethod(
                "getServiceDispatcher",
                ServiceConnection::class.java,
                Context::class.java,
                Handler::class.java,
                Long::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val dispatcher = getServiceDispatcherMethod.invoke(
                loadedApk, connection, implContext, handler, flags
            )

            dispatcher
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun bindServiceIntended(
        context: Context,
        intent: Intent,
        iServiceConnection: Any,
        flags: Int,
        userId: Int = 0
    ): Int {
        return try {
            val amClass = Class.forName("android.app.ActivityManager")

            val getServiceMethod =
                amClass.getDeclaredMethod("getService").apply { isAccessible = true }
            val iActivityManager = getServiceMethod.invoke(null)

            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThreadMethod =
                activityThreadClass.getDeclaredMethod("currentActivityThread")
            val currentActivityThread = currentActivityThreadMethod.invoke(null)

            val getApplicationThreadMethod =
                activityThreadClass.getDeclaredMethod("getApplicationThread")
            val applicationThread = getApplicationThreadMethod.invoke(currentActivityThread)

            val iApplicationThreadClass = Class.forName("android.app.IApplicationThread")
            val iServiceConnectionClass = Class.forName("android.app.IServiceConnection")

            val bindServiceMethod = iActivityManager.javaClass.getMethod(
                "bindService",
                iApplicationThreadClass,
                IBinder::class.java,
                Intent::class.java,
                String::class.java,
                iServiceConnectionClass,
                Long::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType
            )

            val resolvedType = intent.resolveTypeIfNeeded(context.contentResolver)
            val packageName = context.packageName

            val result = bindServiceMethod.invoke(
                iActivityManager,
                applicationThread,
                null,
                intent,
                resolvedType,
                iServiceConnection,
                flags.toLong(),
                packageName,
                userId
            )

            result as Int
        } catch (e: Exception) {
            e.printStackTrace()
            -1
        }
    }
}

