package com.virtualxposed.codeloader

import android.content.Context
import java.io.File
import dalvik.system.DexClassLoader
import java.util.zip.ZipFile

object ExecuteHelper {
    const val APK_FILE_NAME = "dynamic.apk"

    fun getAssetFile(context: Context): File? {
        val apkFile = File(context.cacheDir, APK_FILE_NAME)
        apkFile.setWritable(true)
        if (context.packageName == BuildConfig.APPLICATION_ID) {
            apkFile.parentFile?.mkdirs()
            context.assets.open(APK_FILE_NAME).copyTo(apkFile.outputStream())
        } else {
            val packages =
                context.packageManager.getInstalledApplications(0)

            val app = packages.firstOrNull { app ->
                app.packageName == BuildConfig.APPLICATION_ID
            } ?: return null

            val apk = File(app.sourceDir)

            ZipFile(apk).use { zip ->
                val entry = zip.getEntry("assets/${APK_FILE_NAME}")

                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        input.copyTo(apkFile.outputStream())
                    }
                }
            }
        }

        apkFile.setReadOnly() // New Android security policy
        return apkFile
    }

    fun executeAndroidLibrary(context: Context, file: File) {
        val optimizedDexOutputDir = context.codeCacheDir

        val classLoader = DexClassLoader(
            file.absolutePath,
            optimizedDexOutputDir.absolutePath,
            null,
            context.classLoader
        )

        try {
            val loadedClass = classLoader.loadClass("com.virtualxposed.dynamiccode.Sample")
            val instance = loadedClass.getDeclaredConstructor().newInstance()
            val initMethod = loadedClass.getMethod("init", Context::class.java)
            initMethod.invoke(instance, context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}