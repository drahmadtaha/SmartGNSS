package com.smartgnss.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartgnss.core.nmea.FixQuality

/** Colour that represents a fix quality — used for badges, borders, chips. */
fun fixQualityColor(q: FixQuality): Color = when (q) {
    FixQuality.RTK_FIXED -> SurveyColors.fixedGreen
    FixQuality.RTK_FLOAT -> SurveyColors.floatAmber
    FixQuality.DGPS, FixQuality.PPS -> SurveyColors.dgpsBlue
    FixQuality.GPS -> SurveyColors.singleGray
    else -> SurveyColors.noFixRed
}

/** A small section label above a group of content. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(top = 4.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
    )
}

/** Compact metric tile: big value with a small caption. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                color = accent ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/** A coloured pill / chip. */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium,
            color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Horizontal signal-strength bar (0–55 dB-Hz scale). */
@Composable
fun SignalBar(snr: Int?, modifier: Modifier = Modifier) {
    val frac = ((snr ?: 0).coerceIn(0, 55)) / 55f
    val color = when {
        snr == null || snr == 0 -> MaterialTheme.colorScheme.outline
        snr < 25 -> SurveyColors.noFixRed
        snr < 35 -> SurveyColors.floatAmber
        else -> SurveyColors.fixedGreen
    }
    Box(
        modifier
            .height(6.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                RoundedCornerShape(50))
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac)
                .height(6.dp)
                .background(color, RoundedCornerShape(50))
        )
    }
}

/** Label / value row with the value in monospace. */
@Composable
fun LabeledValue(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface)
    }
}
