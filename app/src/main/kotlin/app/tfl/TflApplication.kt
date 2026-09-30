package app.tfl

import android.app.Application
import app.tfl.core.session.AutoLock
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TflApplication : Application() {

    @Inject
    lateinit var autoLock: AutoLock

    override fun onCreate() {
        super.onCreate()
        autoLock.install(this)
    }
}
