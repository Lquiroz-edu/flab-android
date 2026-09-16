package com.lquiroz.flab.core

import android.app.Application

class FLabApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FLabCore.start(this)
        CrashGuard.install(this)
    }
}
