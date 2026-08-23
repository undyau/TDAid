package com.undy.tdaid.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
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
