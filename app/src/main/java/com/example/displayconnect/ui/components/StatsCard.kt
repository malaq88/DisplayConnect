package com.example.displayconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.displayconnect.R
import com.example.displayconnect.models.TransmissionStats
import kotlin.math.roundToInt

@Composable
fun StatsCard(
    stats: TransmissionStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium
            )
            StatRow(
                label = stringResource(R.string.stat_fps),
                value = String.format("%.1f", stats.fps)
            )
            StatRow(
                label = stringResource(R.string.stat_bitrate),
                value = formatKbps(stats.bytesPerSecond)
            )
            StatRow(
                label = stringResource(R.string.stat_resolution),
                value = stats.resolution
            )
            StatRow(
                label = stringResource(R.string.stat_frames),
                value = "${stats.framesSent} enviados / ${stats.framesSkipped} ignorados"
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatKbps(bytesPerSecond: Long): String {
    val kb = bytesPerSecond / 1024.0
    return if (kb < 1) "${bytesPerSecond} B/s" else "${kb.roundToInt()} KB/s"
}
