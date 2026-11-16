package com.cerimoniarios.app

import android.app.Application
import android.content.Context
import com.cerimoniarios.app.support.SupportRepository

class CerimoniariosApp : Application() {
    val preferences by lazy { AppPreferences(this) }
    val support by lazy { SupportRepository(this, preferences) }

    override fun onCreate() {
        super.onCreate()
        support.connect()
    }
}

val Context.app: CerimoniariosApp
    get() = applicationContext as CerimoniariosApp
