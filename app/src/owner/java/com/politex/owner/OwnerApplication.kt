package com.politex.owner

import com.politex.BuildConfig
import com.politex.MpkApplication
import com.politex.ui.FlavorOverlay

open class OwnerApplication : MpkApplication() {
    override fun onCreate() {
        super.onCreate()
        FlavorOverlay.root = { content -> OwnerRoot(content = content) }
        if (BuildConfig.FLAVOR == "adminplus") {
            FlavorOverlay.collegeMenuExtras = { OwnerMenuExtras() }
        }
    }
}
