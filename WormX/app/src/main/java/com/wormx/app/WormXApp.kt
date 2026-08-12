package com.wormx.app

import android.app.Application
import com.google.android.gms.ads.MobileAds

class WormXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize ads SDK once, app-wide
        MobileAds.initialize(this)
    }
}
