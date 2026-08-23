package com.undy.tdaid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undy.tdaid.data.model.AdgRanking
import com.undy.tdaid.data.model.RoundResult
import com.undy.tdaid.data.model.TournamentStanding
import com.undy.tdaid.data.model.asScoreLabel
import com.undy.tdaid.ui.theme.Accent
import com.undy.tdaid.ui.theme.ForestDark
import com.undy.tdaid.ui.theme.ForestTint
import com.undy.tdaid.ui.theme.Ink
import com.undy.tdaid.ui.theme.InkMuted
import com.undy.tdaid.ui.theme.ScoreGoodOnDark
import com.undy.tdaid.ui.theme.SurfaceVariant

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = InkMuted,
        modifier = modifier,
    )
}

@Composable
fun PillTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, color = contentColor, style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp))
    }
}

private fun scoreColor(score: Int, onDark: Boolean): Color = when {
    score < 0 -> if (onDark) ScoreGoodOnDark else ForestDark
    score > 0 -> Accent
    else -> if (onDark) Color.White.copy(alpha = 0.78f) else Ink
}

@Composable
fun ScoreChip(score: Int, onDark: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        text = score.asScoreLabel(),
        color = scoreColor(score, onDark),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 13.sp),
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (onDark) Color.White.copy(alpha = 0.14f) else SurfaceVariant)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun RoundStatRow(round1: RoundResult, overall: TournamentStanding, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        StatBox(label = "ROUND 1", value = round1.scoreToPar.asScoreLabel(), detail = "(${round1.strokes})", score = round1.scoreToPar, modifier = Modifier.weight(1f))
        StatBox(label = "OVERALL", value = overall.scoreToPar.asScoreLabel(), detail = "· ${overall.position}", score = overall.scoreToPar, modifier = Modifier.weight(1f))
    }
}

/** Just the real live standing — used for a real PDGA Live player, where per-round stroke counts
 *  aren't cleanly available but the real cumulative score-to-par and position are. */
@Composable
fun OverallStatRow(overall: TournamentStanding, modifier: Modifier = Modifier) {
    StatBox(
        label = "OVERALL",
        value = overall.scoreToPar.asScoreLabel(),
        detail = "· ${overall.position}",
        score = overall.scoreToPar,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatBox(label: String, value: String, detail: String, score: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp), color = InkMuted)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, color = scoreColor(score, onDark = false), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 14.sp))
            Text(detail, color = InkMuted, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp))
        }
    }
}

@Composable
fun AdgLine(adg: AdgRanking?, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.BarChart, contentDescription = null, tint = InkMuted, modifier = Modifier.size(12.dp))
        if (adg != null) {
            Text(
                text = buildAdgText(adg),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = InkMuted,
            )
        } else {
            Text(
                text = "Not ranked on ADG Tour",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                color = InkMuted,
            )
        }
    }
}

private fun buildAdgText(adg: AdgRanking): String =
    "ADG Tour #${adg.rank} · ${adg.division} · ${adg.points} pts (best 6 events)"

@Composable
fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun StepperRow(
    label: String,
    subtitle: String,
    value: Int,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    valueSuffix: String = "",
) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp), color = InkMuted, modifier = Modifier.padding(bottom = 12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StepButton(icon = false, onClick = onDecrement)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$value $unit$valueSuffix", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp), color = Ink)
            }
            StepButton(icon = true, onClick = onIncrement)
        }
    }
}

@Composable
private fun StepButton(icon: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(SurfaceVariant),
    ) {
        Text(if (icon) "+" else "−", style = MaterialTheme.typography.titleLarge, color = Ink)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Accent,
    contentColor: Color = ForestDark,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp))
        trailing?.invoke(this)
    }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = ForestDark),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, ForestDark),
    ) {
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp))
    }
}
