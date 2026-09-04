package com.virtualxposed.codeloader

import android.content.Context
import androidx.annotation.Keep

@Keep
class SampleNative {
    @Keep
    external fun initNative(context: Context)
}