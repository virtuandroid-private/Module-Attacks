package com.virtualxposed.dynamiccode

import android.content.Context
import android.widget.Toast
import androidx.annotation.Keep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Keep
class Sample {
    @Keep
    fun init(context: Context) {
        println("Running dynamic code in ${context.packageName}!")
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "Toast created from dynamic code!", Toast.LENGTH_LONG).show()
        }
    }
}