@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.undy.tdaid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.undy.tdaid.R

// The design system pairs Space Grotesk (display) with IBM Plex Sans (body) — both real,
// OFL-licensed variable fonts bundled in res/font (see /licenses for the OFL text), each
// instanced per weight via its variation axis rather than needing separate static files.
val DisplayFontFamily = FontFamily(
    Font(R.font.space_grotesk, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
val BodyFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.ibm_plex_sans, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.ibm_plex_sans, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.ibm_plex_sans, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val TDAidTypography = Typography(
    headlineLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.2).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    titleLarge = TextStyle(fontFamily = DisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    titleSmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = BodyFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
)
