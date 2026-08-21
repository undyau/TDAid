package com.undy.tdaid.ui.screens.schedule

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.model.RowStatus
import com.undy.tdaid.data.model.ScheduleRow
import com.undy.tdaid.data.repo.TournamentRepository
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

class FullScheduleViewModel(tournamentRepository: TournamentRepository) : ViewModel() {
    val rows: List<ScheduleRow> = tournamentRepository.fullSchedule()
    val divisionFilters: List<String> = listOf("ALL") + rows.map { it.division }.distinct()
}

@Composable
fun FullScheduleScreen(onBack: () -> Unit) {
    val vm = rememberViewModel { FullScheduleViewModel(ServiceLocator.tournamentRepository) }
    var filter by remember { mutableStateOf("ALL") }
    val filteredRows = vm.rows.filter { filter == "ALL" || it.division == filter }

    Column(Modifier.fillMaxSize().background(com.undy.tdaid.ui.theme.BgPaper)) {
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
            Text("Offline · cached 7:42 AM", style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp), color = InkMuted)
        }

        Spacer(Modifier.padding(top = 6.dp))

        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.divisionFilters) { code ->
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
        Text(row.names, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = InkMuted, modifier = Modifier.weight(1f))
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
