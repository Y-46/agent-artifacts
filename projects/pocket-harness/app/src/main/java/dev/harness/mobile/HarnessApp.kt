package dev.harness.mobile

import android.app.Application
import dev.harness.mobile.core.AppContainer

class HarnessApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}