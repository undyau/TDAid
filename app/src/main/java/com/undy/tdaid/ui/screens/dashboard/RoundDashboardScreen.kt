package com.undy.tdaid.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.model.Division
import com.undy.tdaid.data.prefs.AppSettings
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.remote.PdgaDivisionMeta
import com.undy.tdaid.data.repo.LiveRosterRepository
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.components.SectionLabel
import com.undy.tdaid.ui.components.StepperRow
import com.undy.tdaid.ui.PdgaAttribution
import com.undy.tdaid.ui.formatRelative
import com.undy.tdaid.ui.openPdgaUrl
import com.undy.tdaid.ui.pdgaCoursePath
import com.undy.tdaid.ui.pdgaEventPath
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.BgPaper
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.ForestTint
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.SurfaceColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val settingsRepository: SettingsRepository,
    private val liveRosterRepository: LiveRosterRepository,
) : ViewModel() {
    val eventDivisions = liveRosterRepository.eventDivisions
    val eventCourses = liveRosterRepository.eventCourses
    val liveLoading = liveRosterRepository.loading
    val liveLoadingStatus = liveRosterRepository.loadingStatus
    val profilePrefetchStatus = liveRosterRepository.profilePrefetchStatus
    val lastLoadedAtMillis = liveRosterRepository.lastLoadedAtMillis
    val loadedTournamentId = liveRosterRepository.loadedTournamentId
    val liveError = liveRosterRepository.error

    /** Real divisions (with real player counts) once [loadAllDivisions][LiveRosterRepository.loadAllDivisions]
     *  has found them for the selected event — empty until then. */
    fun divisionsFor(eventDivisions: List<PdgaDivisionMeta>): List<Division> =
        eventDivisions.map { Division(it.code, it.name, starterCount = it.playerCount) }

    var selectedDivision by mutableStateOf("")
        private set

    val settings = settingsRepository.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings(),
    )

    fun selectDivision(code: String) { selectedDivision = code }

    fun incInterval() = viewModelScope.launch {
        settingsRepository.setAnnounceInterval(settings.value.announceIntervalMin + 1)
    }

    fun decInterval() = viewModelScope.launch {
        settingsRepository.setAnnounceInterval(settings.value.announceIntervalMin - 1)
    }

    /** Re-fetches every division from PDGA Live. No-op without a real tournament selected —
     *  there's nothing real to sync. */
    fun refreshSync() {
        val tournamentId = settings.value.selectedTournamentId ?: return
        liveRosterRepository.loadAllDivisions(tournamentId)
    }
}

@Composable
fun RoundDashboardScreen(
    onEnterFieldMode: () -> Unit,
    onOpenDataSources: () -> Unit,
    onOpenRoster: (String) -> Unit,
    onSelectTournament: () -> Unit,
) {
    val vm = rememberViewModel {
        DashboardViewModel(ServiceLocator.settingsRepository, ServiceLocator.liveRosterRepository)
    }
    val settings by vm.settings.collectAsState()
    val eventDivisions by vm.eventDivisions.collectAsState()
    val eventCourses by vm.eventCourses.collectAsState()
    val liveLoading by vm.liveLoading.collectAsState()
    val liveLoadingStatus by vm.liveLoadingStatus.collectAsState()
    val profilePrefetchStatus by vm.profilePrefetchStatus.collectAsState()
    val lastLoadedAtMillis by vm.lastLoadedAtMillis.collectAsState()
    val loadedTournamentId by vm.loadedTournamentId.collectAsState()
    val liveError by vm.liveError.collectAsState()
    val divisions = vm.divisionsFor(eventDivisions)
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(divisions) {
        if (divisions.none { it.code == vm.selectedDivision }) {
            divisions.firstOrNull()?.let { vm.selectDivision(it.code) }
        }
    }

    // Auto-loads every real division's starters & tee times for the selected event — right after
    // picking one, and also after a fresh app restart with one already selected (the in-memory
    // cache doesn't survive that). Compares against loadedTournamentId (an identity check on
    // what's actually loaded) rather than "is eventDivisions empty" — settings can briefly lag
    // behind a rapid tournament switch, and a plain emptiness check can't tell a load that hasn't
    // started yet from one that already finished for the wrong (stale) tournament; an identity
    // mismatch keeps re-triggering until the loaded data genuinely matches the current selection.
    // liveError still guards against retry-looping on a real, persistent failure.
    val tournamentId = settings.selectedTournamentId
    LaunchedEffect(tournamentId, loadedTournamentId, liveLoading, liveError) {
        if (tournamentId != null && tournamentId != loadedTournamentId && !liveLoading && liveError == null) {
            ServiceLocator.liveRosterRepository.loadAllDivisions(tournamentId)
        }
    }

    Column(
        Modifier.fillMaxSize().background(BgPaper)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Forest).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TDAid", color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp))
            Spacer(Modifier.width(10.dp))
            Text(
                "SETUP MODE",
                color = Cream.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenDataSources) {
                Icon(Icons.Filled.Link, contentDescription = "Data sources", tint = Cream.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                val realTournament = settings.selectedTournamentName != null
                val displayedName = settings.selectedTournamentName ?: "No tournament selected"
                val displayedDates = settings.selectedTournamentDates ?: "Tap to search real PDGA events"
                Column {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor)
                            .clickable(onClick = onSelectTournament)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            val tournamentId = settings.selectedTournamentId
                            Text(
                                displayedName,
                                style = if (tournamentId != null) {
                                    MaterialTheme.typography.titleLarge.copy(textDecoration = TextDecoration.Underline)
                                } else {
                                    MaterialTheme.typography.titleLarge
                                },
                                modifier = if (tournamentId != null) {
                                    Modifier.clickable { openPdgaUrl(context, pdgaEventPath(tournamentId)) }
                                } else {
                                    Modifier
                                },
                            )
                            Text(
                                displayedDates + (settings.selectedTournamentLocation?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                color = InkMuted,
                            )
                        }
                        Icon(Icons.Filled.ExpandMore, contentDescription = "Select tournament", tint = InkMuted)
                    }
                    if (realTournament) {
                        Row(Modifier.padding(top = 5.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    liveLoadingStatus != null -> "Real PDGA event · $liveLoadingStatus"
                                    profilePrefetchStatus != null -> "Real PDGA event · $profilePrefetchStatus"
                                    eventDivisions.isNotEmpty() -> "Real PDGA event · real divisions, starters & tee times loaded"
                                    liveError != null -> "Real PDGA event · $liveError"
                                    else -> "Real PDGA event · loading real divisions…"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                color = InkMuted,
                                modifier = Modifier.weight(1f),
                            )
                            if (liveError != null && settings.selectedTournamentId != null) {
                                Text(
                                    "Retry",
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 10.5.sp),
                                    color = ForestDark,
                                    modifier = Modifier.clickable {
                                        ServiceLocator.liveRosterRepository.loadAllDivisions(settings.selectedTournamentId!!)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ForestTint)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Forest))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (settings.selectedTournamentId != null) {
                            lastLoadedAtMillis?.let { "Synced with PDGA · ${formatRelative(it, nowTick)}" } ?: "Not synced yet"
                        } else {
                            "Not synced — no tournament selected"
                        },
                        color = ForestDark,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.5.sp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.refreshSync(); nowTick = System.currentTimeMillis() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh sync", tint = ForestDark, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (eventCourses.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel("Course", modifier = Modifier.padding(bottom = 10.dp))
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            eventCourses.forEach { course ->
                                Text(
                                    course.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp, textDecoration = TextDecoration.Underline),
                                    color = Ink,
                                    modifier = Modifier.clickable { openPdgaUrl(context, pdgaCoursePath(course.courseId)) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Divisions", modifier = Modifier.padding(bottom = 10.dp))
                    if (divisions.isEmpty()) {
                        Text(
                            "Select a real PDGA tournament above to load its divisions.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = InkMuted,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            divisions.forEach { division ->
                                DivisionRow(
                                    division = division,
                                    selected = division.code == vm.selectedDivision,
                                    onClick = { vm.selectDivision(division.code); onOpenRoster(division.code) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp),
                ) {
                    StepperRow(
                        label = "Pre-announce interval",
                        subtitle = "How early to surface a card before its tee time",
                        value = settings.announceIntervalMin,
                        unit = "min · before tee time",
                        onDecrement = vm::decInterval,
                        onIncrement = vm::incInterval,
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(text = "Enter Field Mode", onClick = onEnterFieldMode)
                    Text(
                        "Downloads tee times, ratings & bios for offline use",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = InkMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (settings.selectedTournamentId != null) {
                item {
                    PdgaAttribution(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = InkMuted)
                }
            }
        }
    }
}

@Composable
private fun DivisionRow(division: Division, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) ForestTint else SurfaceColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(Forest),
            contentAlignment = Alignment.Center,
        ) {
            Text(division.code, color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 11.sp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(division.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp), color = Ink)
            Text(
                "${division.starterCount} real starter${if (division.starterCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = InkMuted,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = InkMuted)
    }
}
