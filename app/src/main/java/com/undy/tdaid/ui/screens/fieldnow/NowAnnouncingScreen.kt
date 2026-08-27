package com.undy.tdaid.ui.screens.fieldnow

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.prefs.AppSettings
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.model.asScoreLabel
import com.undy.tdaid.data.repo.LiveRoster
import com.undy.tdaid.data.repo.LiveRosterRepository
import com.undy.tdaid.data.repo.mergeGroupsAcrossDivisions
import com.undy.tdaid.notify.TeeAlarmScheduler
import com.undy.tdaid.ui.components.AdgLine
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.components.OverallStatRow
import com.undy.tdaid.ui.components.RoundStatRow
import com.undy.tdaid.ui.components.ScoreChip
import com.undy.tdaid.ui.components.SectionLabel
import com.undy.tdaid.ui.PdgaAttribution
import com.undy.tdaid.ui.PdgaLinkIcon
import com.undy.tdaid.ui.formatRelative
import com.undy.tdaid.ui.openPdgaUrl
import com.undy.tdaid.ui.pdgaPlayerPath
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Accent
import com.undy.tdaid.ui.theme.AccentTint
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.SurfaceColor
import com.undy.tdaid.ui.theme.SurfaceVariant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Shares whichever real rosters have loaded, merged into a single tee-time-ordered queue across
 *  every division — a TD announces over one PA, and divisions commonly tee off in the same
 *  window or interleaved times, so the announce queue needs to be one combined timeline rather
 *  than scoped to a single division. Empty until a real tournament is selected and loaded. */
class NowAnnouncingViewModel(
    private val liveRosterRepository: LiveRosterRepository,
) : ViewModel() {
    val rosters = liveRosterRepository.rosters
    val lastLoadedAtMillis = liveRosterRepository.lastLoadedAtMillis
    val groups = rosters
        .map { byDivision -> mergeGroupsAcrossDivisions(byDivision) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun roundLabel(rosters: Map<String, LiveRoster>): String =
        rosters.values.firstOrNull()?.let { "RD ${it.round} · Live" } ?: "No round loaded"

    /** Lets a TD pick up a newly-started round (or any other change on PDGA's end) without
     *  backing out to Setup Mode first — re-fetches every division fresh, same as Dashboard's
     *  own refresh, since Field Mode has no other way to trigger a reload. */
    fun refreshSync(tournamentId: String?) {
        if (tournamentId != null) liveRosterRepository.loadAllDivisions(tournamentId)
    }

    var currentIndex by mutableStateOf(0)
        private set
    var bioPlayerId by mutableStateOf<String?>(null)
        private set

    fun current(groups: List<TeeGroup>): TeeGroup? = groups.getOrNull(currentIndex)
    fun onDeck(groups: List<TeeGroup>): List<TeeGroup> = groups.drop(currentIndex + 1).take(3)
    fun done(groups: List<TeeGroup>): Boolean = currentIndex >= groups.size - 1
    fun bioPlayer(groups: List<TeeGroup>): Player? = groups.flatMap { it.players }.find { it.id == bioPlayerId }

    fun advance(groups: List<TeeGroup>) { if (!done(groups)) currentIndex++ }
    fun openBio(id: String) { bioPlayerId = id }
    fun closeBio() { bioPlayerId = null }
}

/** Real minutes between [nowMillis] and [group]'s actual tee time — replaces what used to be a
 *  fixed "in 11/22/33 min" placeholder regardless of the real gap between groups. Blank when the
 *  time doesn't parse (e.g. no tee time published yet), same as no badge at all. Internal (rather
 *  than private) so it can be unit tested directly. */
internal fun countdownLabel(group: TeeGroup, nowMillis: Long): String {
    val teeMillis = TeeAlarmScheduler.parseTodayMillis(group.time) ?: return ""
    val diffMinutes = Math.round((teeMillis - nowMillis) / 60_000.0).toInt()
    return when {
        diffMinutes > 0 -> "in $diffMinutes min"
        diffMinutes == 0 -> "now"
        else -> "${-diffMinutes} min ago"
    }
}

/** Course name(s) for [group]'s division(s) — only worth surfacing when the event actually spans
 *  more than one course, since otherwise it's just the one course every screen already names.
 *  Union across every division represented in the group (a shared tee time can mix divisions),
 *  deduped, so a mixed card never shows the same course name twice. */
fun coursesForGroup(group: TeeGroup, rosters: Map<String, LiveRoster>): List<String> {
    val allCourseIds = rosters.values.flatMap { it.courses }.distinctBy { it.courseId }
    if (allCourseIds.size <= 1) return emptyList()
    val divisions = group.players.map { it.division }.distinct()
    return divisions.flatMap { rosters[it]?.courses.orEmpty() }.distinctBy { it.courseId }.map { it.name }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowAnnouncingScreen(onOpenSchedule: () -> Unit, onOpenAlert: () -> Unit) {
    val vm = rememberViewModel {
        NowAnnouncingViewModel(ServiceLocator.liveRosterRepository)
    }
    val context = LocalContext.current
    val groups by vm.groups.collectAsState()
    val rosters by vm.rosters.collectAsState()
    val lastLoadedAtMillis by vm.lastLoadedAtMillis.collectAsState()
    val settings by ServiceLocator.settingsRepository.settings.collectAsState(initial = AppSettings())

    // Arm real OS alarms for every group still ahead today, so alerts keep firing even if
    // the TD backgrounds or fully closes the app once the round is underway. Re-arms if the
    // roster changes (e.g. a real load finishes while this screen is open).
    LaunchedEffect(groups) {
        val intervalMinutes = ServiceLocator.settingsRepository.settings.first().announceIntervalMin
        TeeAlarmScheduler.scheduleAll(context.applicationContext, groups, intervalMinutes)
    }

    // On Deck's "in X min" is real time-until-tee-time, not a fixed label — ticks so it keeps
    // reading correctly the whole time a TD leaves this screen open during a round.
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowTick = System.currentTimeMillis()
        }
    }

    Column(
        Modifier.fillMaxSize().background(com.undy.tdaid.ui.theme.BgPaper)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(100.dp)).background(SurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.WifiOff, contentDescription = null, tint = InkMuted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    lastLoadedAtMillis?.let { "Offline · cached ${formatRelative(it)}" } ?: "Offline · cached",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp),
                    color = InkMuted,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(vm.roundLabel(rosters), style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp), color = ForestDark, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refreshSync(settings.selectedTournamentId) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh from PDGA", tint = ForestDark, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onOpenAlert) {
                Icon(Icons.Filled.Notifications, contentDescription = "Tee-time alert", tint = ForestDark, modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onOpenSchedule) {
                Icon(Icons.Filled.CalendarViewDay, contentDescription = "Full schedule", tint = ForestDark, modifier = Modifier.size(19.dp))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = vm.current(groups)
            if (current != null) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Forest).padding(19.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("NOW ANNOUNCING", color = Accent, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), modifier = Modifier.weight(1f))
                            if (current.division.isNotEmpty()) {
                                Text(
                                    current.division,
                                    color = Cream,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                    modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(Cream.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Text(current.time, color = Cream, style = MaterialTheme.typography.headlineLarge)
                        Text(
                            if (vm.done(groups)) "Final card of the day" else "Announcing now · ${settings.announceIntervalMin} min before start",
                            color = Cream.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        val courses = coursesForGroup(current, rosters)
                        if (courses.isNotEmpty()) {
                            Text(
                                courses.joinToString(" · "),
                                color = Cream.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                modifier = Modifier.padding(top = 1.dp, bottom = 13.dp),
                            )
                        } else {
                            Spacer(Modifier.height(13.dp))
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Cream.copy(alpha = 0.18f)).padding(bottom = 11.dp))
                        Spacer(Modifier.width(0.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            current.players.forEach { player ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                        .background(Cream.copy(alpha = 0.08f))
                                        .clickable { vm.openBio(player.id) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        player.name,
                                        color = Cream,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.5.sp),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (current.division.isEmpty() && player.division.isNotEmpty()) {
                                        Text(
                                            player.division,
                                            color = Cream.copy(alpha = 0.75f),
                                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                                            modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(Cream.copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 3.dp),
                                        )
                                        Spacer(Modifier.width(7.dp))
                                    }
                                    player.overall?.let { ScoreChip(it.scoreToPar, onDark = true) }
                                    if (player.pdga.hasPdgaNumber) {
                                        PdgaLinkIcon(url = pdgaPlayerPath(player.pdga.pdgaNumber), tint = Cream.copy(alpha = 0.6f))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Filled.Info, contentDescription = "View bio", tint = Cream.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            item {
                when {
                    groups.isEmpty() -> Text(
                        "No tournament loaded yet — select and load one from Setup Mode.",
                        color = InkMuted,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    vm.done(groups) -> Text(
                        "All groups announced ✓",
                        color = ForestDark,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    else -> PrimaryButton(
                        text = "Mark Announced · Next Group",
                        onClick = { vm.advance(groups) },
                        trailing = {
                            Spacer(Modifier.width(7.dp))
                            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = ForestDark, modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }

            if (groups.isNotEmpty()) {
                item {
                    Column {
                        SectionLabel("On Deck", modifier = Modifier.padding(bottom = 9.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            vm.onDeck(groups).forEachIndexed { index, group ->
                                OnDeckRow(group = group, countdown = countdownLabel(group, nowTick), urgent = index == 0, onPlayerClick = vm::openBio)
                            }
                        }
                    }
                }
            }

            if (groups.isNotEmpty()) {
                item {
                    PdgaAttribution(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }

    val bioPlayer = vm.bioPlayer(groups)
    if (bioPlayer != null) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = vm::closeBio, sheetState = sheetState, containerColor = SurfaceColor) {
            BioSheetContent(bioPlayer)
        }
    }
}

@Composable
private fun OnDeckRow(group: TeeGroup, countdown: String, urgent: Boolean, onPlayerClick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(SurfaceColor).padding(horizontal = 13.dp, vertical = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(group.time, style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp), color = Ink)
            if (group.division.isNotEmpty()) {
                Spacer(Modifier.width(7.dp))
                Text(
                    group.division,
                    color = ForestDark,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(com.undy.tdaid.ui.theme.ForestTint).padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                countdown,
                color = if (urgent) ForestDark else InkMuted,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(if (urgent) AccentTint else SurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(7.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(group.players) { player ->
                Row(
                    Modifier.clip(RoundedCornerShape(100.dp)).background(SurfaceVariant)
                        .clickable { onPlayerClick(player.id) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(player.name, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp), color = Ink)
                    if (group.division.isEmpty() && player.division.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text(player.division, style = MaterialTheme.typography.labelSmall, color = InkMuted)
                    }
                    player.overall?.let {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            it.scoreToPar.asScoreLabel(),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.5.sp),
                            color = if (it.scoreToPar < 0) ForestDark else if (it.scoreToPar > 0) Accent else Ink,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BioSheetContent(player: Player) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 6.dp).padding(bottom = 22.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(Forest),
                contentAlignment = Alignment.Center,
            ) {
                Text(player.initials, color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp))
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    player.name,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.5.sp, textDecoration = TextDecoration.Underline),
                    color = Ink,
                    modifier = Modifier.clickable(enabled = player.pdga.hasPdgaNumber) { openPdgaUrl(context, pdgaPlayerPath(player.pdga.pdgaNumber)) },
                )
                Text("PDGA #${player.pdga.pdgaNumber.ifBlank { "—" }} · Member since ${player.pdga.memberSince}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = InkMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                "Rating ${player.pdga.rating ?: "—"}",
                color = ForestDark,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 12.sp),
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(com.undy.tdaid.ui.theme.ForestTint).padding(horizontal = 11.dp, vertical = 7.dp),
            )
            Text(
                "Recent: ${player.recentResult}",
                color = ForestDark,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.5.sp),
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(AccentTint).padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        if (player.round1 != null && player.overall != null) {
            RoundStatRow(round1 = player.round1, overall = player.overall)
            Spacer(Modifier.height(10.dp))
        } else if (player.overall != null) {
            OverallStatRow(player.overall)
            Spacer(Modifier.height(10.dp))
        }
        AdgLine(player.adg)
        Spacer(Modifier.height(8.dp))
        Text(player.bio, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp), color = Ink)
        if (player.sponsor.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Sponsored by ${player.sponsor}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = InkMuted,
            )
        }
    }
}
