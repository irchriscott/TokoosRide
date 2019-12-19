package com.portail.tokoosride

import android.app.Application
import android.content.Context
import androidx.multidex.MultiDex

class TokoosRideApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }

}