package com.smartgnss.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import com.smartgnss.core.nmea.FixQuality
import com.smartgnss.core.transport.LinkState
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.SurveyColors
import com.smartgnss.ui.theme.fixQualityColor

/** One tile in an icon-grid menu. */
data class MenuEntry(
    val label: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

/** The solution-status strip shown under the app bar (Fixed · H · V · sats …). */
@Composable
fun SolutionBar(vm: ReceiverViewModel) {
    val fix by vm.fix.collectAsState()
    val link by vm.link.collectAsState()
    val connected = link == LinkState.CONNECTED
    val color = if (connected) fixQualityColor(fix.quality) else SurveyColors.singleGray

    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(9.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.size(7.dp))
        Text(
            if (connected) fix.quality.label else "No receiver",
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        SolStat("SAT", fix.satellitesUsed.toString())
        SolStat("HDOP", fix.hdop?.let { "%.1f".format(it) } ?: "—")
        SolStat("AGE", fix.diffAgeSec?.let { "%.0fs".format(it) } ?: "—")
    }
}

@Composable
private fun SolStat(label: String, value: String) {
    Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$label ", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
        Text(value, style = MaterialTheme.typography.labelLarge,
            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
    }
}

/** Icon-grid menu (3 columns), the home screen of each tab. */
@Composable
fun MenuGrid(entries: List<MenuEntry>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
    ) {
        items(entries) { e -> MenuTile(e) }
    }
}

@Composable
private fun MenuTile(e: MenuEntry) {
    Column(
        Modifier
            .clickable(onClick = e.onClick)
            .padding(vertical = 16.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .background(
                    (e.tint ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.12f),
                    RoundedCornerShape(15.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(e.icon, contentDescription = e.label,
                tint = e.tint ?: MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp))
        }
        Text(e.label, style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Branded splash shown briefly at launch. */
@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(SurveyColors.brandBlueDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(92.dp)
                    .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.GpsFixed,
                    contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(52.dp))
            }
            Text("Smart GNSS", color = Color.White,
                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Precision survey & stakeout",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium, letterSpacing = 0.5.sp)
        }
    }
}

/** Simple placeholder for features scheduled for a later stage. */
@Composable
fun PlaceholderScreen(title: String, note: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text(title, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold)
        Text(note, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
    }
}
