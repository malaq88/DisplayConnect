package com.example.displayconnect.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.example.displayconnect.R
import com.example.displayconnect.routing.TripProgress
import java.util.Locale

@Composable
fun TripProgressCard(progress: TripProgress) {
    val locale = LocalConfiguration.current.locales[0]
    val distance = progress.remainingDistanceM?.let {
        String.format(locale, if (it < 1000) "%.2f km" else "%.1f km", it / 1000.0)
    } ?: "—"
    val time = progress.remainingDurationS?.let {
        val minutes = (it + 59) / 60
        if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    } ?: "—"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remaining_time), style = MaterialTheme.typography.labelMedium)
                    Text(time, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remaining_distance), style = MaterialTheme.typography.labelMedium)
                    Text(distance, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(stringResource(if (progress.offRoute) R.string.trip_off_route
                else if (progress.remainingDistanceM == null) R.string.trip_calculating else R.string.trip_estimate),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
