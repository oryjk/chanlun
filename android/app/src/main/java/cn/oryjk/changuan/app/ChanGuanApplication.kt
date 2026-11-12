package cn.oryjk.changuan.app

import android.app.Application

class ChanGuanApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
