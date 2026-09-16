package com.narvyn.suraksha

import android.app.Activity
import android.os.Build
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Register only while an in-app destination can be popped. Dialogs retain their own Back. */
internal class AppBackNavigation(private val activity:Activity,private val back:()->Unit) {
    private var callback:OnBackInvokedCallback?=null
    fun enabled(value:Boolean) {
        if(Build.VERSION.SDK_INT<33)return
        if(value&&callback==null){
            val next=OnBackInvokedCallback{back()}
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,next)
            callback=next
        }else if(!value)close()
    }
    fun close(){if(Build.VERSION.SDK_INT>=33)callback?.let{activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)};callback=null}
}
