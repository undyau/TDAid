package com.undy.tdaid.ui.screens.bioeditor

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
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.local.BioNote
import com.undy.tdaid.data.local.BioNotesRepository
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.repo.LiveRosterRepository
import com.undy.tdaid.ui.components.PillTag
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.components.ToggleRow
import com.undy.tdaid.ui.PdgaAttribution
import com.undy.tdaid.ui.openPdgaUrl
import com.undy.tdaid.ui.pdgaPlayerPath
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BioEditorViewModel(
    private val playerId: String,
    liveRosterRepository: LiveRosterRepository,
    private val bioNotesRepository: BioNotesRepository,
) : ViewModel() {
    // Real players load from whichever division's real roster is cached — check all of them,
    // since this screen can be reached from any loaded division.
    val player: Player? = liveRosterRepository.rosters.value.values.flatMap { it.groups }
        .flatMap { it.players }
        .find { it.id == playerId }

    var pronunciation by mutableStateOf("")
    var hometown by mutableStateOf("")
    var bioText by mutableStateOf(player?.bio ?: "")
    var saveToLibrary by mutableStateOf(true)
    var sourceRoundLabel by mutableStateOf<String?>(null)
    var justSaved by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val existing = bioNotesRepository.observeNote(playerId).first()
            if (existing != null) {
                pronunciation = existing.pronunciation
                hometown = existing.hometown
                bioText = existing.bio
                saveToLibrary = existing.savedToLibrary
                sourceRoundLabel = existing.sourceRoundLabel
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            bioNotesRepository.saveNote(
                BioNote(
                    playerId = playerId,
                    pronunciation = pronunciation,
                    hometown = hometown,
                    bio = bioText,
                    savedToLibrary = saveToLibrary,
                    sourceRoundLabel = sourceRoundLabel,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            justSaved = true
        }
    }
}

@Composable
fun BioEditorScreen(playerId: String, onBack: () -> Unit) {
    val vm = rememberViewModel {
        BioEditorViewModel(
            playerId,
            ServiceLocator.liveRosterRepository,
            ServiceLocator.bioNotesRepository,
        )
    }
    val player = vm.player
    val context = androidx.compose.ui.platform.LocalContext.current
    if (player == null) {
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
                Text("Edit Player Bio", color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp))
            }
            Text(
                "This player isn't in the roster currently loaded — go back and reopen their card.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = InkMuted,
                modifier = Modifier.padding(20.dp),
            )
        }
        return
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
                Text("Edit Player Bio", color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp))
                Text(player.name, color = Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(ForestTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(player.initials, color = ForestDark, style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp))
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            player.name,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp, textDecoration = TextDecoration.Underline),
                            color = Ink,
                            modifier = Modifier.clickable(enabled = player.pdga.hasPdgaNumber) { openPdgaUrl(context, pdgaPlayerPath(player.pdga.pdgaNumber)) },
                        )
                        Text("PDGA #${player.pdga.pdgaNumber.ifBlank { "—" }} · Rating ${player.pdga.rating ?: "—"}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = InkMuted)
                    }
                    PillTag(text = "From PDGA", containerColor = SurfaceVariant, contentColor = InkMuted)
                }
            }

            if (vm.sourceRoundLabel != null) {
                item {
                    Row(
                        Modifier.clip(RoundedCornerShape(100.dp)).background(AccentTint)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (vm.justSaved) Icons.Filled.Check else Icons.Filled.Schedule,
                            contentDescription = null, tint = ForestDark, modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (vm.justSaved) "Up to date · saved just now" else "Loaded from last round · ${vm.sourceRoundLabel}",
                            color = ForestDark,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.5.sp),
                        )
                    }
                }
            }

            item {
                LabeledField(label = "Name pronunciation", value = vm.pronunciation, onChange = { vm.pronunciation = it })
            }
            item {
                LabeledField(label = "Hometown", value = vm.hometown, onChange = { vm.hometown = it })
            }
            item {
                LabeledField(label = "Announcer bio & notes", value = vm.bioText, onChange = { vm.bioText = it }, minLines = 4)
            }

            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SurfaceColor).padding(horizontal = 14.dp)) {
                    ToggleRow(
                        title = "Save to player library",
                        subtitle = "Auto-fills these notes whenever ${player.name.substringBefore(' ')} plays a future round",
                        checked = vm.saveToLibrary,
                        onCheckedChange = { vm.saveToLibrary = it },
                    )
                }
            }

            item {
                PrimaryButton(text = "Save Bio", onClick = vm::save)
            }

            if (vm.justSaved) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ForestTint)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = ForestDark, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (vm.saveToLibrary) "Saved to player library — will auto-fill next time ${player.name.substringBefore(' ')} tees off"
                            else "Saved for this round only — won't carry forward",
                            color = ForestDark,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp),
                        )
                    }
                }
            }

            item {
                PdgaAttribution(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit, minLines: Int = 1) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall, color = InkMuted, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            shape = RoundedCornerShape(10.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Forest,
                unfocusedContainerColor = SurfaceColor,
                focusedContainerColor = SurfaceColor,
            ),
        )
    }
}
