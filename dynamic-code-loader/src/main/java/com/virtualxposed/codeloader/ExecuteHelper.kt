package com.virtualxposed.codeloader

import android.content.Context
import java.io.File
import dalvik.system.DexClassLoader
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ExecuteHelper {
    const val APK_FILE_NAME = BuildConfig.DYNAMIC_FILE_NAME
    const val ENCRYPTED_APK_FILE_NAME = BuildConfig.ENCRYPTED_FILE_NAME

    /** Get the dynamic code and copy it to an unencrypted file for running.
     * @param encrypted If true it will use the encrypted APK file to run */
    fun getAssetFile(context: Context, encrypted: Boolean): File? {
        val apkName = if (encrypted) ENCRYPTED_APK_FILE_NAME else APK_FILE_NAME

        fun copy(from: InputStream, to: OutputStream) {
            if (encrypted) {
                decryptFile(from, to, BuildConfig.ENCRYPTION_KEY)
            } else {
                from.copyTo(to)
            }
        }

        val apkFile = File(context.cacheDir, apkName)
        apkFile.setWritable(true)
        if (context.packageName == BuildConfig.APPLICATION_ID) {
            apkFile.parentFile?.mkdirs()
            copy(context.assets.open(apkName), apkFile.outputStream())
        } else {
            val packages =
                context.packageManager.getInstalledApplications(0)

            val app = packages.firstOrNull { app ->
                app.packageName == BuildConfig.APPLICATION_ID
            } ?: return null

            val apk = File(app.sourceDir)

            ZipFile(apk).use { zip ->
                val entry = zip.getEntry("assets/${apkName}")

                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        copy(input, apkFile.outputStream())
                    }
                }
            }
        }

        apkFile.setReadOnly() // New Android security policy
        return apkFile
    }

    fun decryptFile(encryptedFile: InputStream, outputFile: OutputStream, secretKey: String) {
        val rawKey = secretKey.toByteArray(Charsets.UTF_8)
        require(rawKey.size in setOf(16, 24, 32)) {
            "Secret key must be 16, 24, or 32 bytes long for AES."
        }

        val fileBytes = encryptedFile.readBytes()
        require(fileBytes.size >= 12) {
            "File is too short to contain a valid 12-byte IV."
        }

        // Extract the 12-byte IV prepended during encryption
        val iv = fileBytes.copyOfRange(0, 12)
        val cipherText = fileBytes.copyOfRange(12, fileBytes.size)

        val secretKeySpec: SecretKey = SecretKeySpec(rawKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)

        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec)
        val decryptedBytes = cipher.doFinal(cipherText)

        outputFile.write(decryptedBytes)
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