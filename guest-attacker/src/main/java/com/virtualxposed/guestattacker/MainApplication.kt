package com.virtualxposed.guestattacker

import android.app.Application
import android.content.Context
import com.virtualxposed.guestattacker.Utils.getBacktrace

class MainApplication : Application() {
    companion object {
        var hostClassLoader: ClassLoader? = null
    }

    override fun attachBaseContext(base: Context?) {
        // Turns out that all throwable store a backtrace object which contains references to the backtrace classes
        // This lets us get references to the parent classloader, which allows us to break the sandbox.
        // private transient Object backtrace;
        val throwable = Throwable()
        val backtrace = getBacktrace(throwable)
        // Get com.lody.virtual.client.VClientImpl
        val hostClass = backtrace?.firstOrNull { it.name.contains("com.lody.virtual")}
        hostClassLoader = hostClass?.classLoader

        super.attachBaseContext(base)
    }
}