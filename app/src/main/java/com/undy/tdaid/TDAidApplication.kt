package com.undy.tdaid

import android.app.Application
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.notify.NotificationHelper

class TDAidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.createChannel(this)
    }
}
