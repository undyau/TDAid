package com.undy.tdaid.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undy.tdaid.ui.theme.InkMuted

private const val PDGA_BASE_URL = "https://www.pdga.com"

/** Opens a real pdga.com page in the browser — required by the PDGA data license: every screen
 *  showing PDGA player/event/course data must credit and link to pdga.com, and every displayed
 *  player/event/course name must link to its own page there. */
fun openPdgaUrl(context: Context, path: String = "") {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$PDGA_BASE_URL$path")))
}

fun pdgaPlayerPath(pdgaNumber: String) = "/player/$pdgaNumber"
fun pdgaEventPath(tournamentId: String) = "/tour/event/$tournamentId"
fun pdgaCoursePath(courseId: Int) = "/course-directory/course/$courseId"

/** The PDGA data license's required attribution + link to pdga.com — include on every screen
 *  that displays real PDGA player, event, or course data. */
@Composable
fun PdgaAttribution(modifier: Modifier = Modifier, color: Color = InkMuted) {
    val context = LocalContext.current
    Text(
        "Player, event, and course data provided by PDGA.",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, textDecoration = TextDecoration.Underline),
        color = color,
        modifier = modifier.clickable { openPdgaUrl(context) },
    )
}

/** A small, separately-tappable "view on PDGA" affordance — use this instead of nesting a
 *  clickable directly on a player/event name when that name already sits inside a row with its
 *  own primary tap action (selecting a row, expanding it, opening a bio). A name-level nested
 *  clickable consumes taps within its own bounds, which silently breaks the row's primary action
 *  for anyone who naturally taps the (usually large, prominent) name itself — this icon gives the
 *  PDGA link its own small, deliberate hit target instead of stealing the name's.
 *
 *  [IconButton] normally pads its hit-test area out to Material's 48dp accessibility minimum,
 *  which — on an icon this small, sitting right next to the row's own tap target — silently
 *  swallows nearby taps meant for the row (e.g. picking an event from a search result) rather than
 *  the link. Pinning [LocalMinimumInteractiveComponentSize] down to the icon's own visual size
 *  confines the hit target to just the icon, so it no longer competes with the row around it. */
@Composable
fun PdgaLinkIcon(url: String, tint: Color = InkMuted, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 28.dp) {
        IconButton(onClick = { openPdgaUrl(context, url) }, modifier = modifier.size(28.dp)) {
            Icon(Icons.Filled.OpenInNew, contentDescription = "View on PDGA", tint = tint, modifier = Modifier.size(15.dp))
        }
    }
}
