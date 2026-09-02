package com.virtualxposed.guestattacker

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Field
import kotlin.io.path.absolutePathString
import kotlin.io.path.readSymbolicLink

object Utils {
    val TAG = "GuestAttacker"

    fun log(value: Any?) {
        println("$TAG: $value")
    }

    fun toast(context: Context, value: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(
                context,
                value,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    data class FileDescriptorInfo(
        val pid: Int, val fdNumber: Int, val targetPath: String
    )

    @SuppressLint("NewApi")
    fun getOpenFileDescriptors(pid: Int): List<FileDescriptorInfo> {
        val fdDir = File("/proc/$pid/fd")
        val files = fdDir.listFiles() ?: return emptyList()

        val fdList = files.mapNotNull { fd ->
            runCatching {
                val fdNumber = fd.name.toInt()
                val targetPath = fd.toPath().readSymbolicLink().absolutePathString()
                FileDescriptorInfo(pid, fdNumber, targetPath)
            }.getOrNull()
        }

        return fdList
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

    @SuppressLint("DiscouragedPrivateApi")
    /** Call this before other methods on the throwable, otherwise it will be null! */
    fun getBacktrace(throwable: Throwable): List<Class<*>>? {
        return try {
            val field: Field = Throwable::class.java.getDeclaredField("backtrace")
            field.isAccessible = true

            val backtrace = field.get(throwable) as? Array<*>
            backtrace?.filterIsInstance<Class<*>>()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}