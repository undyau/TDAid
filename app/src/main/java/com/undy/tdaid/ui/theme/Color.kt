package com.undy.tdaid.ui.theme

import androidx.compose.ui.graphics.Color

// "Field Utility" palette: deep course green, an amber accent, warm paper — built for
// glanceable reading in bright outdoor light, not a dim indoor dashboard.
val Forest = Color(0xFF2E4A34)
val ForestDark = Color(0xFF223626)
val ForestTint = Color(0xFFE4EEE4)
val Accent = Color(0xFFE07B39)
val AccentTint = Color(0xFFFBE8D8)
val BgPaper = Color(0xFFFAF8F4)
val SurfaceColor = Color(0xFFFEFCF9)
val SurfaceVariant = Color(0xFFF2EEE3)
val Ink = Color(0xFF232B25)
val InkMuted = Color(0xFF6B756C)
val Border = Color(0xFFDDD8CC)
val Cream = Color(0xFFFAF6EE)

val ScoreGood = ForestDark
val ScoreBad = Accent
val ScoreGoodOnDark = Color(0xFFBFE6C8)
val ScoreNeutralOnDark = Color(0xFFC7D2C9)
// Accent (the "over par" orange) reads at only ~2:1 against the Forest card — nearly invisible
// in direct sun. This is a brighter, lighter-shifted version kept for on-dark use only.
val ScoreBadOnDark = Color(0xFFFFB27A)
