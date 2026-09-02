package com.virtualxposed.guestattacker

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.core.net.toUri

object MissingHook {
    fun startCall(context: Context): Boolean {
        val intent = Intent().apply {
            action = Intent.ACTION_CALL
            // Fake phone number: https://en.wikipedia.org/wiki/Fictitious_telephone_number?useskin=vector#Sweden
            data = "tel:0701740605".toUri()
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            return true
        } catch (_: Throwable) {
            return false
        }
    }
}