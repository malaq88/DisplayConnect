package com.example.displayconnect.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.displayconnect.R
import com.example.displayconnect.offline.OfflineMapState

@Composable
fun OfflineMapCard(state: OfflineMapState, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(when {
                state.ready && state.outsideCoverage -> R.string.offline_outside
                state.ready -> R.string.offline_ready
                state.error != null -> R.string.offline_incomplete
                else -> R.string.offline_downloading
            }), style = MaterialTheme.typography.titleMedium)
            if (state.total > 0) {
                LinearProgressIndicator(progress = { state.downloaded.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.offline_progress, state.downloaded, state.total, state.radiusM),
                    style = MaterialTheme.typography.bodySmall)
            } else if (state.error == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            val errorResource = when {
                state.error?.contains("long", ignoreCase = true) == true -> R.string.offline_too_long
                state.error?.contains("suportada") == true -> R.string.offline_unsupported
                state.error?.contains("demais") == true || state.error?.contains("grande") == true -> R.string.offline_too_dense
                else -> R.string.offline_download_error
            }
            Text(if (state.error != null) stringResource(errorResource) else stringResource(if (state.ready && !state.outsideCoverage)
                R.string.offline_ready_hint else if (state.ready) R.string.offline_outside_hint else R.string.offline_wait_hint),
                style = MaterialTheme.typography.bodySmall)
            if (state.error != null) OutlinedButton(onClick = onRetry, enabled = !state.downloading) {
                Text(stringResource(R.string.offline_retry))
            }
        }
    }
}
