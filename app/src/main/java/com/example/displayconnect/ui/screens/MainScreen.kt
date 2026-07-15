package com.example.displayconnect.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.displayconnect.R
import com.example.displayconnect.models.ConnectionState
import com.example.displayconnect.routing.RouteProfile
import com.example.displayconnect.ui.components.ConnectionIndicator
import com.example.displayconnect.ui.components.StatsCard
import com.example.displayconnect.ui.navigation.MainTopBar
import com.example.displayconnect.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onRequestLocationPermission: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
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

            Text(
                text = stringResource(R.string.ble_section),
                style = MaterialTheme.typography.titleMedium
            )

            if (uiState.bleDeviceName.isNotBlank() || uiState.bleDeviceAddress.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.ble_saved_device,
                        uiState.bleDeviceName.ifBlank { uiState.bleDeviceAddress }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (uiState.isScanning) {
                            viewModel.stopBleScan()
                        } else {
                            viewModel.startBleScan(onRequestBluetoothPermission)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isConnected && !uiState.isNavigating
                ) {
                    Text(
                        stringResource(
                            if (uiState.isScanning) R.string.ble_stop_scan else R.string.ble_scan
                        )
                    )
                }
                if (uiState.isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }

            if (uiState.scannedDevices.isNotEmpty() && !isConnected) {
                Text(
                    text = stringResource(R.string.ble_devices),
                    style = MaterialTheme.typography.labelLarge
                )
                uiState.scannedDevices.forEach { device ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isNavigating) {
                                viewModel.connectToDevice(device, onRequestBluetoothPermission)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(device.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${device.address}  ·  ${device.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.connectSavedDevice(onRequestBluetoothPermission) },
                    modifier = Modifier.weight(1f),
                    enabled = !isConnected &&
                        !uiState.isNavigating &&
                        uiState.bleDeviceAddress.isNotBlank()
                ) {
                    Text(stringResource(R.string.connect))
                }
                OutlinedButton(
                    onClick = viewModel::disconnect,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected || uiState.isNavigating
                ) {
                    Text(stringResource(R.string.disconnect))
                }
            }

            StatsCard(stats = uiState.stats)

            Text(
                text = stringResource(R.string.navigation_section),
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = stringResource(R.string.route_profile_section),
                style = MaterialTheme.typography.labelLarge
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RouteProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = uiState.routeProfile == profile,
                        onClick = { viewModel.updateRouteProfile(profile) },
                        label = { Text(stringResource(routeProfileLabel(profile))) },
                        enabled = !uiState.isNavigating,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            OutlinedTextField(
                value = uiState.destQuery,
                onValueChange = viewModel::updateDestQuery,
                label = { Text(stringResource(R.string.dest_search)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 2,
                enabled = !uiState.isNavigating
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = viewModel::searchDestination,
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isNavigating && !uiState.isSearching
                ) {
                    Text(stringResource(R.string.search_place))
                }
                if (uiState.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }

            if (uiState.searchResults.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.search_results),
                    style = MaterialTheme.typography.labelLarge
                )
                uiState.searchResults.forEach { result ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isNavigating) {
                                viewModel.selectSearchResult(result)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = result.displayName,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (uiState.destLabel.isNotBlank()) {
                Text(
                    text = stringResource(R.string.dest_selected, uiState.destLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = stringResource(R.string.dest_coords_section),
                style = MaterialTheme.typography.labelLarge
            )

            OutlinedTextField(
                value = uiState.destLat,
                onValueChange = viewModel::updateDestLat,
                label = { Text(stringResource(R.string.dest_lat)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !uiState.isNavigating
            )

            OutlinedTextField(
                value = uiState.destLon,
                onValueChange = viewModel::updateDestLon,
                label = { Text(stringResource(R.string.dest_lon)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = !uiState.isNavigating
            )

            Text(
                text = stringResource(R.string.dest_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { viewModel.startNavigation(onRequestLocationPermission) },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected && !uiState.isNavigating,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(stringResource(R.string.start_navigation))
            }

            OutlinedButton(
                onClick = {
                    viewModel.startMapsBrowserNavigation(context, onRequestLocationPermission)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected && !uiState.isNavigating
            ) {
                Text(stringResource(R.string.start_maps_browser))
            }

            if (uiState.isNavigating) {
                OutlinedButton(
                    onClick = viewModel::stopNavigation,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.stop_navigation))
                }
                Text(
                    text = stringResource(R.string.navigation_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun routeProfileLabel(profile: RouteProfile): Int = when (profile) {
    RouteProfile.CAR -> R.string.route_profile_car
    RouteProfile.MOTORCYCLE -> R.string.route_profile_motorcycle
    RouteProfile.BIKE -> R.string.route_profile_bike
    RouteProfile.WALKING -> R.string.route_profile_walking
}
