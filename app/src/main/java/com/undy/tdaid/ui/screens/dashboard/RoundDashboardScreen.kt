package com.undy.tdaid.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.model.Division
import com.undy.tdaid.data.prefs.AppSettings
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.repo.TournamentRepository
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.components.SectionLabel
import com.undy.tdaid.ui.components.StepperRow
import com.undy.tdaid.ui.formatRelative
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
    private val tournamentRepository: TournamentRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val tournamentName get() = tournamentRepository.tournamentName
    val roundLabel get() = tournamentRepository.roundLabel
    val roundDate get() = tournamentRepository.roundDate
    val divisions: List<Division> = tournamentRepository.divisions()

    var selectedDivision by mutableStateOf(divisions.firstOrNull()?.code ?: "")
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

    fun refreshSync() = viewModelScope.launch {
        settingsRepository.markSyncedNow()
    }
}

@Composable
fun RoundDashboardScreen(
    onEnterFieldMode: () -> Unit,
    onOpenDataSources: () -> Unit,
    onOpenRoster: (String) -> Unit,
) {
    val vm = rememberViewModel { DashboardViewModel(ServiceLocator.tournamentRepository, ServiceLocator.settingsRepository) }
    val settings by vm.settings.collectAsState()
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }

    Column(Modifier.fillMaxSize().background(BgPaper)) {
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
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(vm.tournamentName, style = MaterialTheme.typography.titleLarge)
                        Text("${vm.roundLabel} · ${vm.roundDate}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = InkMuted)
                    }
                    Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = InkMuted)
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
                        "Synced with PDGA · ${formatRelative(settings.lastSyncedAtMillis, nowTick)}",
                        color = ForestDark,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.5.sp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.refreshSync(); nowTick = System.currentTimeMillis() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh sync", tint = ForestDark, modifier = Modifier.size(18.dp))
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Divisions — ${vm.roundDate}", modifier = Modifier.padding(bottom = 10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        vm.divisions.forEach { division ->
                            DivisionRow(
                                division = division,
                                selected = division.code == vm.selectedDivision,
                                onClick = { vm.selectDivision(division.code); onOpenRoster(division.code) },
                            )
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
            Text("${division.matchedCount}/${division.starterCount} matched to PDGA", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = InkMuted)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = InkMuted)
    }
}
