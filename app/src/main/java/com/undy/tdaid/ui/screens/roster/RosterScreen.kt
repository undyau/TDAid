package com.undy.tdaid.ui.screens.roster

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.prefs.AppSettings
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.repo.AdgRepository
import com.undy.tdaid.data.repo.LiveRoster
import com.undy.tdaid.data.repo.LiveRosterRepository
import com.undy.tdaid.data.repo.PdgaRepository
import com.undy.tdaid.data.repo.TournamentRepository
import com.undy.tdaid.ui.AdgLiveState
import com.undy.tdaid.ui.PdgaLiveState
import com.undy.tdaid.ui.components.AdgLine
import com.undy.tdaid.ui.components.OutlineButton
import com.undy.tdaid.ui.components.PillTag
import com.undy.tdaid.ui.components.RoundStatRow
import com.undy.tdaid.ui.components.StepperRow
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Accent
import com.undy.tdaid.ui.theme.AccentTint
import com.undy.tdaid.ui.theme.Border
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.ForestTint
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.SurfaceColor
import com.undy.tdaid.ui.theme.SurfaceVariant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RosterViewModel(
    private val divisionCode: String,
    tournamentRepository: TournamentRepository,
    private val settingsRepository: SettingsRepository,
    private val liveRosterRepository: LiveRosterRepository,
    private val pdgaRepository: PdgaRepository,
    private val adgRepository: AdgRepository,
) : ViewModel() {
    private val demoGroups: List<TeeGroup> = tournamentRepository.teeGroups()
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val rosters: StateFlow<Map<String, LiveRoster>> = liveRosterRepository.rosters
    val liveLoading: StateFlow<Boolean> = liveRosterRepository.loading
    val liveLoadingStatus: StateFlow<String?> = liveRosterRepository.loadingStatus
    val liveError: StateFlow<String?> = liveRosterRepository.error
    val pdgaLoggedIn: Boolean get() = pdgaRepository.isLoggedIn

    var round by mutableStateOf(1)
        private set

    fun changeRound(r: Int) { round = r.coerceIn(1, 20) }

    fun groupsFor(rosters: Map<String, LiveRoster>): List<TeeGroup> = rosters[divisionCode]?.groups ?: demoGroups

    /** Manual fallback — normally every division is already loaded automatically right after the
     *  TD picks the tournament, but this lets them retry a division that failed, or reload at a
     *  different round than [LiveRosterRepository.loadAllDivisions] auto-picked. */
    fun loadRealRoster() {
        val tournamentId = settings.value.selectedTournamentId ?: return
        liveRosterRepository.load(tournamentId, divisionCode, round)
    }

    private val pdgaLiveMap = mutableStateMapOf<String, PdgaLiveState>()
    private val adgLiveMap = mutableStateMapOf<String, AdgLiveState>()

    fun pdgaLiveFor(playerId: String): PdgaLiveState? = pdgaLiveMap[playerId]
    fun adgLiveFor(playerId: String): AdgLiveState? = adgLiveMap[playerId]

    /** Explicit, per-player action — never runs silently — so a TD always sees which number
     *  they're checking and can judge a name mismatch before trusting the result. */
    fun checkLiveData(player: Player) {
        if (pdgaRepository.isLoggedIn) {
            pdgaLiveMap[player.id] = PdgaLiveState(loading = true)
            viewModelScope.launch {
                pdgaRepository.lookupPlayer(player.pdga.pdgaNumber)
                    .onSuccess { result ->
                        pdgaLiveMap[player.id] = if (result == null) PdgaLiveState(notFound = true) else PdgaLiveState(result = result)
                    }
                    .onFailure { e -> pdgaLiveMap[player.id] = PdgaLiveState(error = e.message ?: "Lookup failed") }
            }
        }
        adgLiveMap[player.id] = AdgLiveState(loading = true)
        viewModelScope.launch {
            adgRepository.lookupPlayerByName(player.name)
                .onSuccess { row -> adgLiveMap[player.id] = if (row == null) AdgLiveState(notFound = true) else AdgLiveState(result = row) }
                .onFailure { e -> adgLiveMap[player.id] = AdgLiveState(error = e.message ?: "Lookup failed") }
        }
    }
}

@Composable
fun RosterScreen(divisionCode: String, onBack: () -> Unit, onEditBio: (String) -> Unit) {
    val vm = rememberViewModel {
        RosterViewModel(
            divisionCode,
            ServiceLocator.tournamentRepository,
            ServiceLocator.settingsRepository,
            ServiceLocator.liveRosterRepository,
            ServiceLocator.pdgaRepository,
            ServiceLocator.adgRepository,
        )
    }
    var expandedId by remember { mutableStateOf<String?>(null) }
    val settings by vm.settings.collectAsState()
    val rosters by vm.rosters.collectAsState()
    val liveLoading by vm.liveLoading.collectAsState()
    val liveLoadingStatus by vm.liveLoadingStatus.collectAsState()
    val liveError by vm.liveError.collectAsState()
    val activeLiveRoster = rosters[divisionCode]
    val groups = vm.groupsFor(rosters)
    val isLive = activeLiveRoster != null
    val totalPlayers = groups.sumOf { it.players.size }

    Column(
        Modifier.fillMaxSize().background(com.undy.tdaid.ui.theme.BgPaper)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Forest).padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Cream)
            }
            Column {
                Text(
                    "$divisionCode · " + (if (isLive) "Round ${activeLiveRoster.round} Starters (Live)" else "Round 2 Starters"),
                    color = Cream,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp),
                )
                Text("$totalPlayers players", color = Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (settings.selectedTournamentId != null && !isLive) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Real PDGA event selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.5.sp),
                            color = Ink,
                        )
                        if (liveLoading) {
                            Text(
                                liveLoadingStatus ?: "Loading…",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = InkMuted,
                            )
                        } else {
                            Text(
                                "$divisionCode isn't loaded yet — the demo roster below is shown until then.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = InkMuted,
                            )
                            StepperRow(
                                label = "Round",
                                subtitle = "Which round to load",
                                value = vm.round,
                                unit = "",
                                onDecrement = { vm.changeRound(vm.round - 1) },
                                onIncrement = { vm.changeRound(vm.round + 1) },
                            )
                            OutlineButton(
                                text = "Load Real Starters & Tee Times",
                                onClick = vm::loadRealRoster,
                            )
                        }
                        liveError?.let {
                            Text(it, color = Accent, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                        }
                    }
                }
            }

            groups.forEach { group ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(group.time, color = ForestDark, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.5.sp))
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.height(1.dp).weight(1f).background(Border))
                        }
                        group.players.forEach { player ->
                            PlayerRow(
                                player = player,
                                expanded = expandedId == player.id,
                                onToggle = { expandedId = if (expandedId == player.id) null else player.id },
                                onEditBio = { onEditBio(player.id) },
                                pdgaLoggedIn = vm.pdgaLoggedIn,
                                pdgaLive = vm.pdgaLiveFor(player.id),
                                adgLive = vm.adgLiveFor(player.id),
                                onCheckLive = { vm.checkLiveData(player) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(
    player: Player,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEditBio: () -> Unit,
    pdgaLoggedIn: Boolean,
    pdgaLive: PdgaLiveState?,
    adgLive: AdgLiveState?,
    onCheckLive: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceColor).animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(ForestTint),
                contentAlignment = Alignment.Center,
            ) {
                Text(player.initials, color = ForestDark, style = MaterialTheme.typography.titleLarge.copy(fontSize = 12.sp))
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(player.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp), color = Ink)
                Text("PDGA #${player.pdga.pdgaNumber} · Rating ${player.pdga.rating}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = InkMuted)
            }
            Icon(Icons.Filled.CheckCircle, contentDescription = "Cached", tint = Forest, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 13.dp).padding(bottom = 13.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
                Text(
                    "Member since ${player.pdga.memberSince} · Recent: ${player.recentResult}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = InkMuted,
                )
                if (player.round1 != null && player.overall != null) {
                    RoundStatRow(round1 = player.round1, overall = player.overall)
                }
                AdgLine(player.adg)
                LiveDataCheck(
                    player = player,
                    pdgaLoggedIn = pdgaLoggedIn,
                    pdgaLive = pdgaLive,
                    adgLive = adgLive,
                    onCheckLive = onCheckLive,
                )
                Text(player.bio, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = Ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillTag(
                        text = "Cached for offline use",
                        containerColor = ForestTint,
                        contentColor = ForestDark,
                    )
                    if (player.hasCustomNotes) {
                        Spacer(Modifier.width(7.dp))
                        PillTag(
                            text = "TD notes saved",
                            containerColor = com.undy.tdaid.ui.theme.AccentTint,
                            contentColor = ForestDark,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onEditBio) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit bio", tint = InkMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Explicit, per-player button that hits the real PDGA API (if logged in) and the real ADG
 *  Tour leaderboard, showing genuine live results rather than this row's cached demo data.
 *  A PDGA name mismatch is surfaced rather than silently swapped in, since this roster's PDGA
 *  numbers are demo placeholders, not necessarily the real player's actual assigned number. */
@Composable
private fun LiveDataCheck(
    player: Player,
    pdgaLoggedIn: Boolean,
    pdgaLive: PdgaLiveState?,
    adgLive: AdgLiveState?,
    onCheckLive: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val busy = pdgaLive?.loading == true || adgLive?.loading == true
        Row(
            Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(SurfaceVariant)
                .clickable(enabled = !busy, onClick = onCheckLive)
                .padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (busy) "Checking live data…" else "Check Live PDGA/ADG Data",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.5.sp),
                color = ForestDark,
            )
        }

        pdgaLive?.let { live ->
            when {
                live.result != null -> {
                    val mismatch = live.nameMismatch(player.name)
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                            .background(if (mismatch) AccentTint else ForestTint).padding(9.dp),
                    ) {
                        if (mismatch) {
                            Text(
                                "PDGA #${player.pdga.pdgaNumber} is registered to ${live.result.firstName} ${live.result.lastName} — verify the number",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = ForestDark,
                            )
                        } else {
                            Text(
                                "Live PDGA: Rating ${live.result.rating ?: "—"} · ${live.result.membershipStatus} member",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = ForestDark,
                            )
                        }
                    }
                }
                live.notFound -> Text(
                    "No real PDGA record for #${player.pdga.pdgaNumber}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = InkMuted,
                )
                live.error != null -> Text(
                    live.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Accent,
                )
                else -> Unit
            }
        } ?: if (!pdgaLoggedIn) {
            Text(
                "Log in to PDGA in Data Sources to check this player's real record",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = InkMuted,
            )
        } else Unit

        adgLive?.let { live ->
            when {
                live.result != null -> Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(AccentTint).padding(9.dp),
                ) {
                    Text(
                        "Live ADG: #${live.result.rank} in ${live.result.division} · ${live.result.points} pts",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = ForestDark,
                    )
                }
                live.notFound -> Text(
                    "\"${player.name}\" isn't in the current ADG Tour standings",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = InkMuted,
                )
                live.error != null -> Text(
                    live.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Accent,
                )
                else -> Unit
            }
        }
    }
}
