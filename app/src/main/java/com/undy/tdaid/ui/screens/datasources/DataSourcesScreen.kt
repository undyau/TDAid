package com.undy.tdaid.ui.screens.datasources

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.prefs.AppSettings
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.repo.AdgRepository
import com.undy.tdaid.data.repo.PdgaRepository
import com.undy.tdaid.ui.components.OutlineButton
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.components.SectionLabel
import com.undy.tdaid.ui.components.StepperRow
import com.undy.tdaid.ui.components.ToggleRow
import com.undy.tdaid.ui.formatRelative
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.ForestTint
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.SurfaceColor
import com.undy.tdaid.ui.theme.SurfaceVariant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DataSourcesViewModel(
    private val settingsRepository: SettingsRepository,
    private val pdgaRepository: PdgaRepository,
    private val adgRepository: AdgRepository,
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setPdgaConnected(v: Boolean) = viewModelScope.launch { settingsRepository.setPdgaConnected(v) }
    fun setAdgConnected(v: Boolean) = viewModelScope.launch { settingsRepository.setAdgConnected(v) }
    fun setAdgShowRank(v: Boolean) = viewModelScope.launch { settingsRepository.setAdgShowRank(v) }
    fun setSync(ratings: Boolean? = null, results: Boolean? = null, membership: Boolean? = null, bios: Boolean? = null) =
        viewModelScope.launch { settingsRepository.setSyncToggle(ratings, results, membership, bios) }
    fun setFrequency(min: Int) = viewModelScope.launch { settingsRepository.setSyncFrequency(min) }

    fun syncNow() = viewModelScope.launch {
        pdgaRepository.syncNow()
        adgRepository.syncNow()
        settingsRepository.markSyncedNow()
    }
}

@Composable
fun DataSourcesScreen(onBack: () -> Unit) {
    val vm = rememberViewModel {
        DataSourcesViewModel(ServiceLocator.settingsRepository, ServiceLocator.pdgaRepository, ServiceLocator.adgRepository)
    }
    val settings by vm.settings.collectAsState()
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }

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
                Text("Data Sources", color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp))
                Text("PDGA & ADG Tour sync settings", color = Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column {
                    SectionLabel("Primary Source", modifier = Modifier.padding(bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(14.dp)) {
                        if (settings.pdgaConnected) {
                            ConnectionHeader(
                                icon = Icons.Filled.Link,
                                connected = true,
                                title = "Connected to PDGA",
                                subtitle = "Linked as Ridge Valley Disc Golf Club · TD #4471",
                            )
                            TextButton(onClick = { vm.setPdgaConnected(false) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Disconnect", style = MaterialTheme.typography.titleSmall, color = InkMuted)
                            }
                            Spacer(Modifier.width(0.dp))
                            EventLine()
                        } else {
                            ConnectionHeader(
                                icon = Icons.Filled.Link,
                                connected = false,
                                title = "Not connected",
                                subtitle = "Connect your club's PDGA account to pull ratings, results and bios automatically.",
                            )
                            PrimaryButton(text = "Connect to PDGA", onClick = { vm.setPdgaConnected(true) }, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }

            if (settings.pdgaConnected) {
                item {
                    Column {
                        SectionLabel("Sync from PDGA", modifier = Modifier.padding(bottom = 4.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(horizontal = 14.dp)) {
                            ToggleRow("Player ratings", "Current PDGA rating for each starter", settings.syncRatings, { vm.setSync(ratings = it) })
                            ToggleRow("Recent results", "Last 3 sanctioned events per player", settings.syncResults, { vm.setSync(results = it) })
                            ToggleRow("Membership date", "Year each player joined the PDGA", settings.syncMembership, { vm.setSync(membership = it) })
                            ToggleRow("Player bios", "Short auto-generated summary per card", settings.syncBios, { vm.setSync(bios = it) })
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Supplementary Source", modifier = Modifier.padding(bottom = 8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(14.dp)) {
                        if (settings.adgConnected) {
                            ConnectionHeader(
                                icon = Icons.Filled.BarChart,
                                connected = true,
                                title = "Connected to ADG Tour",
                                subtitle = "Australian Disc Golf Tour Leaderboard · ranks by division from a player's best 6 events",
                            )
                            TextButton(onClick = { vm.setAdgConnected(false) }, contentPadding = PaddingValues(0.dp)) {
                                Text("Disconnect", style = MaterialTheme.typography.titleSmall, color = InkMuted)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                ToggleRow(
                                    "Show ADG Tour rank in bios",
                                    "Adds tour rank & points alongside PDGA rating",
                                    settings.adgShowRank,
                                    { vm.setAdgShowRank(it) },
                                )
                            }
                        } else {
                            ConnectionHeader(
                                icon = Icons.Filled.BarChart,
                                connected = false,
                                title = "Not connected",
                                subtitle = "Optional — adds Australian Disc Golf (ADG) Tour Leaderboard rank to player bios.",
                            )
                            OutlineButton(text = "Connect to ADG Tour", onClick = { vm.setAdgConnected(true) }, modifier = Modifier.padding(top = 14.dp))
                        }
                    }
                }
            }

            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp)) {
                    StepperRow(
                        label = "Auto-sync frequency",
                        subtitle = "How often to refresh all connected sources",
                        value = settings.syncFrequencyMin,
                        unit = "min",
                        onDecrement = { vm.setFrequency(settings.syncFrequencyMin - 5) },
                        onIncrement = { vm.setFrequency(settings.syncFrequencyMin + 5) },
                    )
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
                        "Last synced ${formatRelative(settings.lastSyncedAtMillis, nowTick)}",
                        color = ForestDark,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.5.sp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.syncNow(); nowTick = System.currentTimeMillis() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync now", tint = ForestDark, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    connected: Boolean,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(if (connected) Forest else SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (connected) Cream else InkMuted, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.5.sp), color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = InkMuted, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        }
    }
}

@Composable
private fun EventLine() {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Ridge Valley Open · Event #58392", style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.5.sp), color = Ink)
            Text("Aug 21–23, 2026 · 4 divisions linked", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = InkMuted)
        }
        Text("Change", style = MaterialTheme.typography.titleSmall, color = ForestDark)
    }
}
