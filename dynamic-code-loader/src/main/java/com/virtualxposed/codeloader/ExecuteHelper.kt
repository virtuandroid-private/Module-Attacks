package com.virtualxposed.codeloader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object ExecuteHelper {
    /** Get the dynamic code and copy it to an unencrypted file for running.
     * @param encrypted If true it will use the encrypted APK file to run */
    fun getAssetApkFile(context: Context, encrypted: Boolean): File? {
        val apkName =
            if (encrypted) BuildConfig.ENCRYPTED_FILE_NAME else BuildConfig.DYNAMIC_FILE_NAME
        return getAssetFile(context, apkName, encrypted)
    }

    fun getAssetNativeFile(context: Context): File? {
        val arch = Build.SUPPORTED_ABIS[0]
        val soFileName = "$arch${'_'}${BuildConfig.SO_FILE_NAME}"
        return getAssetFile(context, soFileName, false)
    }

    private fun getAssetFile(context: Context, fileName: String, encrypted: Boolean): File? {
        fun copy(from: InputStream, to: OutputStream) {
            if (encrypted) {
                decryptFile(from, to, BuildConfig.ENCRYPTION_KEY)
            } else {
                from.copyTo(to)
            }
        }

//        val file = File.createTempFile(fileName, ".tmp", context.cacheDir)
        val file = File(context.cacheDir, fileName)
        file.setWritable(true)
        if (context.packageName == BuildConfig.APPLICATION_ID) {
            file.parentFile?.mkdirs()
            copy(context.assets.open(fileName), file.outputStream())
        } else {
            val packages =
                context.packageManager.getInstalledApplications(0)

            val app = packages.firstOrNull { app ->
                app.packageName == BuildConfig.APPLICATION_ID
            } ?: return null

            val apk = File(app.sourceDir)

            ZipFile(apk).use { zip ->
                val entry = zip.getEntry("assets/${fileName}")

                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        copy(input, file.outputStream())
                    }
                }
            }
        }

        file.setReadOnly() // New Android security policy
        return file
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
        } finally {
            file.delete()
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    suspend fun executeAndroidNativeLibrary(context: Context, file: File) {
        runCatching {
            System.load(file.absolutePath)
            file.delete()

            withContext(Dispatchers.Main) {
                SampleNative().initNative(context)
            }
        }
    }
}