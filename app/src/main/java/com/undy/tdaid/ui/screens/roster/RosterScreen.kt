package com.undy.tdaid.ui.screens.roster

import androidx.compose.animation.animateContentSize
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
import androidx.compose.runtime.getValue
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
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.repo.TournamentRepository
import com.undy.tdaid.ui.components.AdgLine
import com.undy.tdaid.ui.components.PillTag
import com.undy.tdaid.ui.components.RoundStatRow
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Border
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.ForestTint
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.SurfaceColor

class RosterViewModel(tournamentRepository: TournamentRepository) : ViewModel() {
    val groups: List<TeeGroup> = tournamentRepository.teeGroups()
    val totalPlayers: Int = groups.sumOf { it.players.size }
}

@Composable
fun RosterScreen(divisionCode: String, onBack: () -> Unit, onEditBio: (String) -> Unit) {
    val vm = rememberViewModel { RosterViewModel(ServiceLocator.tournamentRepository) }
    var expandedId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(com.undy.tdaid.ui.theme.BgPaper)) {
        Row(
            Modifier.fillMaxWidth().background(Forest).padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Cream)
            }
            Column {
                Text("$divisionCode · Round 2 Starters", color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp))
                Text("${vm.totalPlayers} players", color = Cream.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp))
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            vm.groups.forEach { group ->
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
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerRow(player: Player, expanded: Boolean, onToggle: () -> Unit, onEditBio: () -> Unit) {
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
