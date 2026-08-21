package com.undy.tdaid

import android.app.Application
import com.undy.tdaid.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TDAidApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        CoroutineScope(Dispatchers.IO).launch {
            ServiceLocator.bioNotesRepository.seedIfEmpty()
        }
    }
}
