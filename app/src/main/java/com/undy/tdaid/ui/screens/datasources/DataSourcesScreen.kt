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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.prefs.AppSettings
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.model.asScoreLabel
import com.undy.tdaid.data.remote.AdgLeaderboardRow
import com.undy.tdaid.data.remote.PdgaLiveResult
import com.undy.tdaid.data.remote.PdgaPlayerResult
import com.undy.tdaid.data.repo.AdgRepository
import com.undy.tdaid.data.repo.LiveRosterRepository
import com.undy.tdaid.data.repo.PdgaRepository
import com.undy.tdaid.notify.TeeAlarmScheduler
import com.undy.tdaid.ui.components.OutlineButton
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.components.SectionLabel
import com.undy.tdaid.ui.components.ToggleRow
import com.undy.tdaid.ui.formatRelative
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Accent
import com.undy.tdaid.ui.theme.AccentTint
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
    private val liveRosterRepository: LiveRosterRepository,
) : ViewModel() {
    val settings = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    val lastLoadedAtMillis = liveRosterRepository.lastLoadedAtMillis

    var pdgaLoggedIn by mutableStateOf(pdgaRepository.isLoggedIn)
        private set
    var pdgaUsername by mutableStateOf(pdgaRepository.loggedInAs)
        private set
    var pdgaLoginInProgress by mutableStateOf(false)
        private set
    var pdgaLoginError by mutableStateOf<String?>(null)
        private set

    var pdgaLookupInProgress by mutableStateOf(false)
        private set
    var pdgaLookupResult by mutableStateOf<PdgaPlayerResult?>(null)
        private set
    var pdgaLookupError by mutableStateOf<String?>(null)
        private set

    var adgLookupInProgress by mutableStateOf(false)
        private set
    var adgLookupResult by mutableStateOf<AdgLeaderboardRow?>(null)
        private set
    var adgLookupNotFound by mutableStateOf(false)
        private set
    var adgLookupError by mutableStateOf<String?>(null)
        private set

    var liveTournId by mutableStateOf("")
    var liveDivision by mutableStateOf("MPO")
    var liveRound by mutableStateOf("1")
    var liveLookupInProgress by mutableStateOf(false)
        private set
    var liveLookupResults by mutableStateOf<List<PdgaLiveResult>>(emptyList())
        private set
    var liveLookupError by mutableStateOf<String?>(null)
        private set

    fun prefillLiveTournId(tournamentId: String) {
        if (liveTournId.isBlank()) liveTournId = tournamentId
    }

    fun setAdgConnected(v: Boolean) = viewModelScope.launch { settingsRepository.setAdgConnected(v) }
    fun setAdgShowRank(v: Boolean) = viewModelScope.launch { settingsRepository.setAdgShowRank(v) }
    fun setFetchPlayerProfiles(v: Boolean) = viewModelScope.launch { settingsRepository.setFetchPlayerProfiles(v) }

    /** For a real tournament this actually re-fetches every division from PDGA Live, not just a
     *  timestamp stamp. */
    fun syncNow() {
        val tournamentId = settings.value.selectedTournamentId
        if (tournamentId != null) {
            liveRosterRepository.loadAllDivisions(tournamentId)
        } else {
            viewModelScope.launch { settingsRepository.markSyncedNow() }
        }
    }

    fun loginPdga(username: String, password: String) {
        pdgaLoginInProgress = true
        pdgaLoginError = null
        viewModelScope.launch {
            pdgaRepository.login(username, password)
                .onSuccess {
                    pdgaLoggedIn = true
                    pdgaUsername = pdgaRepository.loggedInAs
                }
                .onFailure { e -> pdgaLoginError = e.message ?: "Login failed" }
            pdgaLoginInProgress = false
        }
    }

    fun logoutPdga() {
        pdgaLoggedIn = false
        pdgaUsername = null
        pdgaLookupResult = null
        pdgaLookupError = null
        viewModelScope.launch { pdgaRepository.logout() }
    }

    fun lookupPdgaPlayer(pdgaNumber: String) {
        pdgaLookupInProgress = true
        pdgaLookupError = null
        pdgaLookupResult = null
        viewModelScope.launch {
            pdgaRepository.lookupPlayer(pdgaNumber)
                .onSuccess { player ->
                    if (player == null) pdgaLookupError = "No player found for #$pdgaNumber" else pdgaLookupResult = player
                }
                .onFailure { e -> pdgaLookupError = e.message ?: "Lookup failed" }
            pdgaLookupInProgress = false
        }
    }

    fun lookupAdgPlayer(name: String) {
        adgLookupInProgress = true
        adgLookupError = null
        adgLookupResult = null
        adgLookupNotFound = false
        viewModelScope.launch {
            adgRepository.lookupPlayerByName(name)
                .onSuccess { row -> if (row == null) adgLookupNotFound = true else adgLookupResult = row }
                .onFailure { e -> adgLookupError = e.message ?: "Lookup failed" }
            adgLookupInProgress = false
        }
    }

    fun lookupLiveResults() {
        val tournId = liveTournId.trim()
        val round = liveRound.trim().toIntOrNull()
        if (tournId.isEmpty() || round == null) {
            liveLookupError = "Enter a tournament ID and round number"
            return
        }
        liveLookupInProgress = true
        liveLookupError = null
        liveLookupResults = emptyList()
        viewModelScope.launch {
            pdgaRepository.fetchLiveResults(tournId, liveDivision.trim().uppercase(), round)
                .onSuccess { results ->
                    liveLookupResults = results.sortedBy { it.teeTime }
                    if (results.isEmpty()) liveLookupError = "No tee times published yet for this round"
                }
                .onFailure { e -> liveLookupError = e.message ?: "Lookup failed" }
            liveLookupInProgress = false
        }
    }
}

@Composable
fun DataSourcesScreen(onBack: () -> Unit) {
    val vm = rememberViewModel {
        DataSourcesViewModel(
            ServiceLocator.settingsRepository,
            ServiceLocator.pdgaRepository,
            ServiceLocator.adgRepository,
            ServiceLocator.liveRosterRepository,
        )
    }
    val settings by vm.settings.collectAsState()
    val lastLoadedAtMillis by vm.lastLoadedAtMillis.collectAsState()
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(settings.selectedTournamentId) {
        settings.selectedTournamentId?.let { vm.prefillLiveTournId(it) }
    }

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
                        if (vm.pdgaLoggedIn) {
                            ConnectionHeader(
                                icon = Icons.Filled.Link,
                                connected = true,
                                title = "Connected to PDGA",
                                subtitle = "Logged in as ${vm.pdgaUsername}",
                            )
                            TextButton(onClick = vm::logoutPdga, contentPadding = PaddingValues(0.dp)) {
                                Text("Disconnect", style = MaterialTheme.typography.titleSmall, color = InkMuted)
                            }
                        } else {
                            ConnectionHeader(
                                icon = Icons.Filled.Link,
                                connected = false,
                                title = "Not connected",
                                subtitle = "Log in with your real PDGA membership to pull ratings, results and bios — this hits the actual PDGA REST API.",
                            )
                            var username by remember { mutableStateOf("") }
                            var password by remember { mutableStateOf("") }
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("PDGA username") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("PDGA password") },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                                )
                                if (vm.pdgaLoginError != null) {
                                    Text(vm.pdgaLoginError!!, color = Accent, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                                }
                                PrimaryButton(
                                    text = if (vm.pdgaLoginInProgress) "Logging in…" else "Log In to PDGA",
                                    onClick = { vm.loginPdga(username, password) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Player Profile Data", modifier = Modifier.padding(bottom = 4.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(horizontal = 14.dp)) {
                        ToggleRow(
                            "Player profiles",
                            "Member-since date, recent results & auto-bio for every real starter — loaded once per event (no PDGA login needed). Turn off for a faster, bare roster.",
                            settings.fetchPlayerProfiles,
                            { vm.setFetchPlayerProfiles(it) },
                        )
                    }
                }
            }

            if (vm.pdgaLoggedIn) {
                item {
                    Column {
                        SectionLabel("Look Up a Real PDGA Player", modifier = Modifier.padding(bottom = 8.dp))
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            var pdgaNumber by remember { mutableStateOf("") }
                            OutlinedTextField(
                                value = pdgaNumber,
                                onValueChange = { pdgaNumber = it },
                                label = { Text("PDGA number") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                            )
                            OutlineButton(
                                text = if (vm.pdgaLookupInProgress) "Looking up…" else "Look Up",
                                onClick = { vm.lookupPdgaPlayer(pdgaNumber) },
                            )
                            vm.pdgaLookupError?.let {
                                Text(it, color = Accent, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                            }
                            vm.pdgaLookupResult?.let { player ->
                                Column(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ForestTint).padding(12.dp),
                                ) {
                                    Text(
                                        "${player.firstName} ${player.lastName} · #${player.pdgaNumber}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = ForestDark,
                                    )
                                    Text(
                                        "Rating ${player.rating ?: "—"} · ${player.membershipStatus} member" +
                                            (player.city?.let { c -> " · $c, ${player.stateProv ?: player.country ?: ""}" } ?: ""),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = InkMuted,
                                    )
                                }
                            }
                            Text(
                                "Real data from api.pdga.com — try your own PDGA number.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                                color = InkMuted,
                            )
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
                Column {
                    SectionLabel("Look Up a Real ADG Tour Player", modifier = Modifier.padding(bottom = 8.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        var playerName by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = playerName,
                            onValueChange = { playerName = it },
                            label = { Text("Player name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                        )
                        OutlineButton(
                            text = if (vm.adgLookupInProgress) "Looking up…" else "Look Up",
                            onClick = { vm.lookupAdgPlayer(playerName) },
                        )
                        vm.adgLookupError?.let {
                            Text(it, color = Accent, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                        }
                        if (vm.adgLookupNotFound) {
                            Text(
                                "Not found in the current ADG Tour standings.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = InkMuted,
                            )
                        }
                        vm.adgLookupResult?.let { row ->
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentTint).padding(12.dp),
                            ) {
                                Text(
                                    "${row.name} · #${row.rank} in ${row.division}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ForestDark,
                                )
                                Text(
                                    "ADG #${row.adgNumber} · ${row.points} pts · ${row.eventsPlayed} events (best 6 counted)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = InkMuted,
                                )
                            }
                        }
                        Text(
                            "Real data scraped live from australiandiscgolf.com/leaderboard — no login needed. Try a name from the current standings, e.g. \"Jade Brady\".",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = InkMuted,
                        )
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Look Up Real Starters & Tee Times", modifier = Modifier.padding(bottom = 8.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = vm.liveTournId,
                            onValueChange = { vm.liveTournId = it },
                            label = { Text("Tournament ID") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = vm.liveDivision,
                                onValueChange = { vm.liveDivision = it },
                                label = { Text("Division") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                            )
                            OutlinedTextField(
                                value = vm.liveRound,
                                onValueChange = { vm.liveRound = it },
                                label = { Text("Round") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Forest),
                            )
                        }
                        OutlineButton(
                            text = if (vm.liveLookupInProgress) "Looking up…" else "Look Up Tee Times",
                            onClick = vm::lookupLiveResults,
                        )
                        vm.liveLookupError?.let {
                            Text(it, color = Accent, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
                        }
                        if (vm.liveLookupResults.isNotEmpty()) {
                            val groups = vm.liveLookupResults
                                .groupBy { it.teeTime }
                                .entries
                                .sortedWith(compareBy({ it.key.isEmpty() }, { it.key }))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                groups.forEach { (teeTime, players) ->
                                    Column(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ForestTint).padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        Text(
                                            teeTime.ifEmpty { "Tee time TBD" },
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                                            color = ForestDark,
                                        )
                                        players.forEach { p ->
                                            Text(
                                                "${p.firstName} ${p.lastName}" +
                                                    (p.rating?.let { r -> " · $r" } ?: "") +
                                                    (p.toPar?.let { tp -> " · ${tp.asScoreLabel()}" } ?: ""),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                                color = InkMuted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "Real data from PDGA Live — only published shortly before a round tees off. Try TournID 101036, e.g. Division MPO, Round 3.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = InkMuted,
                        )
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
                            lastLoadedAtMillis?.let { "Last synced ${formatRelative(it, nowTick)}" } ?: "Not synced yet"
                        } else {
                            "Last synced ${formatRelative(settings.lastSyncedAtMillis, nowTick)}"
                        },
                        color = ForestDark,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.5.sp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.syncNow(); nowTick = System.currentTimeMillis() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Sync now", tint = ForestDark, modifier = Modifier.size(18.dp))
                    }
                }
            }

            item {
                Column {
                    SectionLabel("Tee-Time Alerts", modifier = Modifier.padding(bottom = 8.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        var exactAlarmsAllowed by remember { mutableStateOf(TeeAlarmScheduler.canScheduleExact(context)) }
                        if (!exactAlarmsAllowed) {
                            Text(
                                "Exact alarms aren't allowed yet — alerts will still fire, just without a tight timing guarantee.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = InkMuted,
                            )
                            OutlineButton(
                                text = "Allow Exact Alarms",
                                onClick = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                                    context.startActivity(intent)
                                },
                            )
                        } else {
                            Text(
                                "Exact alarms are allowed — tee-time alerts will fire on time even if the app is closed.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = InkMuted,
                            )
                        }
                        var testSent by remember { mutableStateOf(false) }
                        OutlineButton(
                            text = if (testSent) "Test alert sent — check in ~15s" else "Send Test Alert",
                            onClick = {
                                TeeAlarmScheduler.scheduleTestAlert(context.applicationContext)
                                testSent = true
                            },
                        )
                        Text(
                            "Verify sound & vibration work on this phone — try closing the app after sending.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = InkMuted,
                        )
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
