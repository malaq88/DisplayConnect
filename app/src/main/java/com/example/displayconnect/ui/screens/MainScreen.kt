package com.example.displayconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.displayconnect.R
import com.example.displayconnect.models.ConnectionState
import com.example.displayconnect.ui.components.ConnectionIndicator
import com.example.displayconnect.ui.components.StatsCard
import com.example.displayconnect.ui.navigation.MainTopBar
import com.example.displayconnect.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onRequestCapture: () -> Unit,
    onOpenMaps: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isConnected = uiState.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { MainTopBar(onNavigateToSettings) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            ConnectionIndicator(state = uiState.connectionState)

            OutlinedTextField(
                value = uiState.espIp,
                onValueChange = viewModel::updateIp,
                label = { Text(stringResource(R.string.esp_ip)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnected && !uiState.isTransmitting
            )

            OutlinedTextField(
                value = uiState.espPort,
                onValueChange = viewModel::updatePort,
                label = { Text(stringResource(R.string.esp_port)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isConnected && !uiState.isTransmitting
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = viewModel::connect,
                    modifier = Modifier.weight(1f),
                    enabled = !isConnected && !uiState.isTransmitting
                ) {
                    Text(stringResource(R.string.connect))
                }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected || uiState.isTransmitting
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            }

            StatsCard(stats = uiState.stats)

            Text(
                text = stringResource(R.string.navigation_section),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedButton(
                onClick = onOpenMaps,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.open_google_maps))
            }

            Button(
                onClick = onRequestCapture,
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected && !uiState.isTransmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(stringResource(R.string.start_transmission))
            }

            if (uiState.isTransmitting) {
                Text(
                    text = stringResource(R.string.transmission_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
