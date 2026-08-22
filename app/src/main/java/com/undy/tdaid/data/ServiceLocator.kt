package com.undy.tdaid.data

import android.content.Context
import com.undy.tdaid.data.local.AppDatabase
import com.undy.tdaid.data.local.BioNotesRepository
import com.undy.tdaid.data.local.RoomBioNotesRepository
import com.undy.tdaid.data.prefs.DataStoreSettingsRepository
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.repo.AdgRepository
import com.undy.tdaid.data.repo.FakeTournamentRepository
import com.undy.tdaid.data.repo.PdgaRepository
import com.undy.tdaid.data.repo.RealAdgRepository
import com.undy.tdaid.data.repo.RealPdgaRepository
import com.undy.tdaid.data.repo.TournamentRepository

/**
 * Small hand-rolled dependency container. No DI framework — this app is one module with a
 * handful of repositories, and a real container would be pure overhead here.
 */
object ServiceLocator {
    lateinit var tournamentRepository: TournamentRepository private set
    lateinit var pdgaRepository: PdgaRepository private set
    lateinit var adgRepository: AdgRepository private set
    lateinit var settingsRepository: SettingsRepository private set
    lateinit var bioNotesRepository: BioNotesRepository private set

    fun init(context: Context) {
        if (::tournamentRepository.isInitialized) return
        tournamentRepository = FakeTournamentRepository()
        pdgaRepository = RealPdgaRepository()
        adgRepository = RealAdgRepository()
        settingsRepository = DataStoreSettingsRepository(context.applicationContext)
        bioNotesRepository = RoomBioNotesRepository(AppDatabase.get(context).bioNoteDao())
    }
}
