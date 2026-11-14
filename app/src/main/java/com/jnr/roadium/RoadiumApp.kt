package com.jnr.roadium

import android.app.Application

class RoadiumApp : Application() {
    lateinit var controller: RoadiumController
        private set

    override fun onCreate() {
        super.onCreate()
        controller = RoadiumController(this)
    }
}
