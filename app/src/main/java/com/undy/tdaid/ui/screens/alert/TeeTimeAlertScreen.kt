package com.undy.tdaid.ui.screens.alert

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.undy.tdaid.data.ServiceLocator
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.repo.TournamentRepository
import com.undy.tdaid.notify.NotificationHelper
import com.undy.tdaid.ui.components.PrimaryButton
import com.undy.tdaid.ui.rememberViewModel
import com.undy.tdaid.ui.theme.Accent
import com.undy.tdaid.ui.theme.Cream
import com.undy.tdaid.ui.theme.Forest
import com.undy.tdaid.ui.theme.ForestDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AlertPhase { COUNTING, FIRING, CONFIRMED }
private const val TOTAL_SECONDS = 165

class TeeTimeAlertViewModel(
    application: Application,
    tournamentRepository: TournamentRepository,
) : AndroidViewModel(application) {
    val groups: List<TeeGroup> = tournamentRepository.teeGroups()
    var groupIndex by mutableIntStateOf(0)
        private set
    var secondsLeft by mutableIntStateOf(TOTAL_SECONDS)
        private set
    var phase by mutableStateOf(AlertPhase.COUNTING)
        private set

    val currentGroup: TeeGroup get() = groups[groupIndex % groups.size]

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (phase == AlertPhase.COUNTING) {
                    secondsLeft = (secondsLeft - 15).coerceAtLeast(0)
                    if (secondsLeft <= 0) {
                        phase = AlertPhase.FIRING
                        NotificationHelper.notifyAnnounceNow(getApplication(), currentGroup)
                    }
                }
            }
        }
    }

    fun snooze() {
        if (phase == AlertPhase.COUNTING) secondsLeft = (secondsLeft + 60).coerceAtMost(TOTAL_SECONDS)
    }

    fun markAnnounced() {
        phase = AlertPhase.CONFIRMED
        viewModelScope.launch {
            delay(1400)
            groupIndex = (groupIndex + 1) % groups.size
            secondsLeft = TOTAL_SECONDS
            phase = AlertPhase.COUNTING
        }
    }
}

@Composable
fun TeeTimeAlertScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val application = context.applicationContext as Application
    val vm = rememberViewModel { TeeTimeAlertViewModel(application, ServiceLocator.tournamentRepository) }
    val group = vm.currentGroup
    val fraction = vm.secondsLeft.toFloat() / TOTAL_SECONDS
    val firing = vm.phase == AlertPhase.FIRING

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasPermission(context)
        ) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Forest).windowInsetsPadding(WindowInsets.systemBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.padding(top = 26.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Cream, modifier = Modifier.size(13.dp))
            Spacer(Modifier.size(6.dp))
            Text("Alert Mode · Offline", color = Cream, style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp))
        }

        Spacer(Modifier.padding(top = 12.dp))

        Box(Modifier.padding(top = 24.dp).size(168.dp), contentAlignment = Alignment.Center) {
            CountdownRing(fraction = if (firing) 0f else fraction, firing = firing)
            when (vm.phase) {
                AlertPhase.COUNTING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val m = vm.secondsLeft / 60
                    val s = vm.secondsLeft % 60
                    Text("$m:${s.toString().padStart(2, '0')}", color = Cream, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 30.sp))
                    Text("until tee time", color = Cream.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp))
                }
                AlertPhase.FIRING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("NOW", color = Cream, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 21.sp))
                    Text("announce this card", color = Cream.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5.sp))
                }
                AlertPhase.CONFIRMED -> Icon(Icons.Filled.Check, contentDescription = null, tint = Cream, modifier = Modifier.size(30.dp))
            }
        }

        Column(Modifier.padding(top = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (vm.phase) {
                    AlertPhase.COUNTING -> "NEXT TO ANNOUNCE"
                    AlertPhase.FIRING -> "ANNOUNCE NOW"
                    AlertPhase.CONFIRMED -> "NICE WORK"
                },
                color = Accent,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, letterSpacing = 1.4.sp),
            )
            Text(group.time, color = Cream, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp), modifier = Modifier.padding(top = 4.dp))
        }

        Column(
            Modifier.padding(top = 14.dp).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            group.players.forEach { player ->
                Text(
                    player.name,
                    color = Cream,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.5.sp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                        .background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (vm.phase == AlertPhase.COUNTING) {
                androidx.compose.material3.OutlinedButton(
                    onClick = vm::snooze,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.22f)),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Cream),
                ) {
                    Text("Snooze · +1 min", style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp))
                }
            }
            PrimaryButton(
                text = "Mark Announced",
                onClick = vm::markAnnounced,
                trailing = {
                    Spacer(Modifier.size(7.dp))
                    Icon(Icons.Filled.Check, contentDescription = null, tint = ForestDark, modifier = Modifier.size(16.dp))
                },
            )
            Text(
                "Alerts fire 3 min before each tee time with sound & vibration, even with the screen off. (sped up here for preview)",
                color = Cream.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
                modifier = Modifier.padding(top = 5.dp),
            )
        }
    }
}

@Composable
private fun CountdownRing(fraction: Float, firing: Boolean) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(168.dp)) {
        val stroke = 10.dp.toPx()
        drawArc(
            color = Color.White.copy(alpha = 0.15f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            size = Size(size.width - stroke, size.height - stroke),
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        )
        drawArc(
            color = if (firing) Accent else Accent,
            startAngle = -90f,
            sweepAngle = 360f * fraction.coerceIn(0f, 1f).let { if (firing) 1f else it },
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            size = Size(size.width - stroke, size.height - stroke),
            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
        )
    }
}
