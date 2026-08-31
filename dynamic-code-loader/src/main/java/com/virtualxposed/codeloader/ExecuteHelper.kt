package com.virtualxposed.codeloader

import android.content.Context
import java.io.File
import dalvik.system.DexClassLoader

class ExecuteHelper(val context: Context) {
    private var apkFile: File? = null
    private val apkFileName = "dynamic.apk"

    fun init() {
        if (apkFile == null) {
            val apkFile = File(context.cacheDir, apkFileName)
            apkFile.setWritable(true)
            context.assets.open(apkFileName).copyTo(apkFile.outputStream())
            apkFile.setReadOnly() // New Android security policy
            this.apkFile = apkFile
        }
    }

    fun executeAndroidLibrary(context: Context) {
        val apkFile = this.apkFile ?: return
        val optimizedDexOutputDir = context.codeCacheDir

        val classLoader = DexClassLoader(
            apkFile.absolutePath,
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