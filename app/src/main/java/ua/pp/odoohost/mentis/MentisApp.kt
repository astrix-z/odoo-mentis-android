package ua.pp.odoohost.mentis

import android.app.Application

class MentisApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
