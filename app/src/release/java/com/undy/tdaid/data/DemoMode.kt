package com.undy.tdaid.data

import android.content.Context
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.repo.LiveRosterRepository

/** Release builds never run the fictional screenshot event — see the debug source set. */
object DemoMode {
    @Suppress("UNUSED_PARAMETER")
    fun liveRosterRepositoryOrNull(context: Context, settingsRepository: SettingsRepository): LiveRosterRepository? = null
}
