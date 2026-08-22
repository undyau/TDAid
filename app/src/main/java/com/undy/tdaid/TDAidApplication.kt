package com.undy.tdaid

import android.app.Application
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.notify.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TDAidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        NotificationHelper.createChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            ServiceLocator.bioNotesRepository.seedIfEmpty()
        }
    }
}
