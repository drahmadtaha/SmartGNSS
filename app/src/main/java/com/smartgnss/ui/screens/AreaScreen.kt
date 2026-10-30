package com.smartgnss.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartgnss.ui.ReceiverViewModel
import com.smartgnss.ui.theme.LabeledValue
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Area & perimeter of the collected points, taken in order and projected into
 * the active coordinate system (shoelace formula on grid East/North).
 */
@Composable
fun AreaScreen(vm: ReceiverViewModel, modifier: Modifier = Modifier) {
    val points by vm.survey.points.collectAsState()
    val crs by vm.crs.collectAsState()

    val en = points.map { crs.toGrid(it.latitude, it.longitude, it.altitude ?: 0.0) }

    var area = 0.0
    var perim = 0.0
    if (en.size >= 3) {
        for (i in en.indices) {
            val (e1, n1) = en[i]
            val (e2, n2) = en[(i + 1) % en.size]
            area += e1 * n2 - e2 * n1
            perim += hypot(e2 - e1, n2 - n1)
        }
        area = abs(area) / 2.0
    }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        com.smartgnss.ui.theme.SectionHeader("Result · ${crs.name}")

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (en.size < 3) {
                    Text("Collect at least 3 points to compute an area.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LabeledValue("Points", en.size.toString())
                    LabeledValue("Area", "%,.3f m²".format(area))
                    LabeledValue("Area (dunam)", "%,.4f".format(area / 1000.0))
                    LabeledValue("Area (ha)", "%,.4f".format(area / 10_000.0))
                    LabeledValue("Perimeter", "%,.3f m".format(perim))
                }
            }
        }

        if (en.isNotEmpty()) {
            com.smartgnss.ui.theme.SectionHeader("Vertices (grid E, N)")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(points) { p ->
                    val (e, n) = crs.toGrid(p.latitude, p.longitude, p.altitude ?: 0.0)
                    Text("${p.name}:  %.3f, %.3f".format(e, n),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
