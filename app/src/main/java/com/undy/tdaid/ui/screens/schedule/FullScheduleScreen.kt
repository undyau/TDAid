package com.undy.tdaid.ui.screens.schedule

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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.RowStatus
import com.undy.tdaid.data.model.ScheduleRow
import com.undy.tdaid.data.model.asScoreLabel
import com.undy.tdaid.data.repo.LiveRoster
import com.undy.tdaid.data.repo.LiveRosterRepository
import com.undy.tdaid.data.repo.TournamentRepository
import com.undy.tdaid.notify.TeeAlarmScheduler
import com.undy.tdaid.ui.components.scoreColor
import com.undy.tdaid.ui.formatRelative
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Accent
import com.undy.tdaid.ui.theme.Border
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.ForestTint
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.SurfaceColor
import com.undy.tdaid.ui.theme.SurfaceVariant

class FullScheduleViewModel(
    tournamentRepository: TournamentRepository,
    liveRosterRepository: LiveRosterRepository,
) : ViewModel() {
    private val demoRows: List<ScheduleRow> = tournamentRepository.fullSchedule()
    val rosters = liveRosterRepository.rosters
    val lastLoadedAtMillis = liveRosterRepository.lastLoadedAtMillis

    /** A real cross-division schedule once every division has loaded — the demo one until then.
     *  Status is derived from the real tee times against the current time: passed means done,
     *  the single soonest not-yet-passed group across every division is the one under way now,
     *  everything else (including any group with no published tee time yet) is upcoming. */
    fun rowsFor(rosters: Map<String, LiveRoster>): List<ScheduleRow> {
        if (rosters.isEmpty()) return demoRows

        data class Raw(val time: String, val division: String, val players: List<Player>, val millis: Long?)
        val now = System.currentTimeMillis()
        val raw = rosters.entries
            .flatMap { (division, roster) ->
                roster.groups.map { group ->
                    Raw(
                        time = group.time,
                        division = division,
                        players = group.players,
                        millis = TeeAlarmScheduler.parseTodayMillis(group.time),
                    )
                }
            }
            .sortedWith(compareBy({ it.millis == null }, { it.millis ?: Long.MAX_VALUE }))
        val nextUpcomingIndex = raw.indexOfFirst { it.millis != null && it.millis >= now }

        return raw.mapIndexed { index, r ->
            val status = when {
                r.millis == null -> RowStatus.UPCOMING
                r.millis < now -> RowStatus.DONE
                index == nextUpcomingIndex -> RowStatus.CURRENT
                else -> RowStatus.UPCOMING
            }
            ScheduleRow(
                time = r.time,
                division = r.division,
                names = r.players.joinToString(" / ") { it.name },
                status = status,
                players = r.players,
            )
        }
    }
}

@Composable
fun FullScheduleScreen(onBack: () -> Unit) {
    val vm = rememberViewModel { FullScheduleViewModel(ServiceLocator.tournamentRepository, ServiceLocator.liveRosterRepository) }
    val rosters by vm.rosters.collectAsState()
    val lastLoadedAtMillis by vm.lastLoadedAtMillis.collectAsState()
    val rows = vm.rowsFor(rosters)
    val divisionFilters = listOf("ALL") + rows.map { it.division }.distinct()
    var filter by remember { mutableStateOf("ALL") }
    val filteredRows = rows.filter { filter == "ALL" || it.division == filter }

    Column(
        Modifier.fillMaxSize().background(com.undy.tdaid.ui.theme.BgPaper)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
            Text("Full Day Schedule", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 17.sp), color = Ink)
        }

        Row(
            Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(100.dp)).background(SurfaceVariant)
                .padding(horizontal = 11.dp, vertical = 7.dp)
                .wrapContentWidth(),
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

        Spacer(Modifier.padding(top = 6.dp))

        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(divisionFilters) { code ->
                val active = code == filter
                Text(
                    code,
                    color = if (active) Cream else InkMuted,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.5.sp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (active) Forest else SurfaceColor)
                        .clickable { filter = code }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filteredRows) { row -> ScheduleRowItem(row) }
        }
    }
}

@Composable
private fun ScheduleRowItem(row: ScheduleRow) {
    val isCurrent = row.status == RowStatus.CURRENT
    val isDone = row.status == RowStatus.DONE
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (isCurrent) ForestTint else SurfaceColor)
            .alpha(if (isDone) 0.55f else 1f)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.time, style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.5.sp), color = Ink, modifier = Modifier.width(72.dp))
        Text(
            row.division,
            color = ForestDark,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(ForestTint).padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Spacer(Modifier.width(9.dp))
        if (row.players.isNotEmpty()) {
            Text(
                buildAnnotatedString {
                    row.players.forEachIndexed { index, player ->
                        if (index > 0) append("  ·  ")
                        append(player.name)
                        player.overall?.let { overall ->
                            append(" ")
                            withStyle(SpanStyle(color = scoreColor(overall.scoreToPar, onDark = false), fontWeight = FontWeight.Bold)) {
                                append(overall.scoreToPar.asScoreLabel())
                            }
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = InkMuted,
                modifier = Modifier.weight(1f),
            )
        } else {
            Text(row.names, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = InkMuted, modifier = Modifier.weight(1f))
        }
        if (isDone) {
            Icon(Icons.Filled.Check, contentDescription = "Done", tint = InkMuted, modifier = Modifier.size(14.dp))
        }
        if (isCurrent) {
            Text(
                "NOW",
                color = Cream,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(Forest).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
    }
}
