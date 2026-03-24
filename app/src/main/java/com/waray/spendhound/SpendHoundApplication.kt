package com.waray.spendhound

import android.app.Application

class SpendHoundApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DeclareDatabase.initialize(this)
    }
}
