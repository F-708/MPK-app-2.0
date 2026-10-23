package com.politex.adminplus

import com.politex.owner.OwnerApplication
import com.politex.ui.FlavorOverlay

class AdminPlusApplication : OwnerApplication() {
    override fun onCreate() {
        super.onCreate()
        FlavorOverlay.root = { content -> AdminPlusRoot(content = content) }
        FlavorOverlay.collegeMenuExtras = { AdminPlusMenuExtras() }
    }
}
