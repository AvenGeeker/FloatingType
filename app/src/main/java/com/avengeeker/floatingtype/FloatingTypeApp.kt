package com.avengeeker.floatingtype

import android.app.Application
import com.avengeeker.floatingtype.core.ServiceLocator

class FloatingTypeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
