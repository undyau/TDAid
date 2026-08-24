package com.undy.tdaid.ui.screens.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.remote.PdgaEventResult
import com.undy.tdaid.data.repo.LiveRosterRepository
import com.undy.tdaid.data.repo.PdgaRepository
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.PdgaAttribution
import com.undy.tdaid.ui.PdgaLinkIcon
import com.undy.tdaid.ui.pdgaEventPath
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Accent
import com.undy.tdaid.ui.theme.BgPaper
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.ForestTint
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.SurfaceColor
import com.undy.tdaid.ui.theme.SurfaceVariant
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private fun formatDateRange(startDate: String, endDate: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return try {
        val start = parser.parse(startDate)
        val end = parser.parse(endDate)
        if (start == null || end == null) return "$startDate – $endDate"
        val dayFmt = SimpleDateFormat("MMM d", Locale.US)
        val yearFmt = SimpleDateFormat("yyyy", Locale.US)
        val sameYear = yearFmt.format(start) == yearFmt.format(end)
        val sameMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(start) == SimpleDateFormat("yyyy-MM", Locale.US).format(end)
        when {
            sameMonth -> "${dayFmt.format(start)}–${SimpleDateFormat("d", Locale.US).format(end)}, ${yearFmt.format(end)}"
            sameYear -> "${dayFmt.format(start)} – ${dayFmt.format(end)}, ${yearFmt.format(end)}"
            else -> "${dayFmt.format(start)}, ${yearFmt.format(start)} – ${dayFmt.format(end)}, ${yearFmt.format(end)}"
        }
    } catch (e: Exception) {
        "$startDate – $endDate"
    }
}

class TournamentSearchViewModel(
    private val pdgaRepository: PdgaRepository,
    private val settingsRepository: SettingsRepository,
    private val liveRosterRepository: LiveRosterRepository,
) : ViewModel() {
    val pdgaLoggedIn: Boolean get() = pdgaRepository.isLoggedIn

    var query by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<PdgaEventResult>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun search() {
        if (query.isBlank()) return
        loading = true
        error = null
        results = emptyList()
        viewModelScope.launch {
            pdgaRepository.searchEvents(query)
                .onSuccess { events ->
                    results = events
                    if (events.isEmpty()) error = "No PDGA events found for \"$query\""
                }
                .onFailure { e -> error = e.message ?: "Search failed" }
            loading = false
        }
    }

    fun select(event: PdgaEventResult, onDone: () -> Unit) {
        viewModelScope.launch {
            val dates = formatDateRange(event.startDate, event.endDate)
            val location = listOfNotNull(event.city, event.stateProv ?: event.country).joinToString(", ").ifEmpty { null }
            settingsRepository.setSelectedTournament(event.tournamentName, dates, location, event.tournamentId)
            // Only clear here — Dashboard notices the cache is empty for this tournament and
            // triggers the actual load itself, since it (unlike this screen) is still around to
            // see it through, whether this is a fresh selection or a restart with one already set.
            liveRosterRepository.clear()
            onDone()
        }
    }

}

@Composable
fun TournamentSearchScreen(onBack: () -> Unit, onGoToDataSources: () -> Unit) {
    val vm = rememberViewModel {
        TournamentSearchViewModel(ServiceLocator.pdgaRepository, ServiceLocator.settingsRepository, ServiceLocator.liveRosterRepository)
    }

    Column(
        Modifier.fillMaxSize().background(BgPaper).windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Forest).padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Cream)
            }
            Column {
                Text("Select Tournament", color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp))
                Text("PDGA Event Search", color = Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!vm.pdgaLoggedIn) {
                item {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Log in to PDGA to search tournaments",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.5.sp),
                            color = Ink,
                        )
                        Text(
                            "PDGA's Event Search API needs the same login as the player lookups.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = InkMuted,
                        )
                        PrimaryButton(text = "Go to Data Sources", onClick = onGoToDataSources)
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = vm.query,
                            onValueChange = { vm.query = it },
                            label = { Text("Tournament name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                        )
                        PrimaryButton(text = if (vm.loading) "Searching…" else "Search PDGA Events", onClick = vm::search)
                        vm.error?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Accent)
                        }
                    }
                }

                items(vm.results) { event ->
                    EventRow(event = event, onClick = { vm.select(event, onBack) })
                }
            }

            item {
                Text(
                    "Picking an event loads its divisions, starters and tee times from PDGA Live automatically — give it a few seconds after returning to the dashboard.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = InkMuted,
                )
            }

            if (vm.results.isNotEmpty()) {
                item {
                    PdgaAttribution(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: PdgaEventResult, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceColor)
            .clickable(onClick = onClick).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                event.tournamentName,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            PdgaLinkIcon(url = pdgaEventPath(event.tournamentId))
        }
        val location = listOfNotNull(event.city, event.stateProv ?: event.country).joinToString(", ")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDateRange(event.startDate, event.endDate) + (if (location.isNotEmpty()) " · $location" else ""),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = InkMuted,
                modifier = Modifier.weight(1f),
            )
            event.tier?.let {
                Text(
                    it,
                    color = ForestDark,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(ForestTint).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}
