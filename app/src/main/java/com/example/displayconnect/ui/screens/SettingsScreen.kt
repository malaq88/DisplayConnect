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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.displayconnect.R
import com.example.displayconnect.capture.ScreenCaptureManager
import com.example.displayconnect.ui.navigation.SettingsTopBar
import com.example.displayconnect.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = { SettingsTopBar(onNavigateBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SettingSlider(
                title = stringResource(R.string.setting_fps, settings.targetFps),
                value = settings.targetFps.toFloat(),
                valueRange = ScreenCaptureManager.MIN_FPS.toFloat()..ScreenCaptureManager.MAX_FPS.toFloat(),
                steps = ScreenCaptureManager.MAX_FPS - ScreenCaptureManager.MIN_FPS - 1,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(targetFps = value.toInt()) }
                }
            )

            SettingSlider(
                title = stringResource(R.string.setting_jpeg_quality, settings.jpegQuality),
                value = settings.jpegQuality.toFloat(),
                valueRange = 30f..100f,
                steps = 13,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(jpegQuality = value.toInt()) }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = settings.targetWidth.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let { w ->
                            viewModel.updateSettings { it.copy(targetWidth = w.coerceIn(120, 480)) }
                        }
                    },
                    label = { Text(stringResource(R.string.setting_width)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = settings.targetHeight.toString(),
                    onValueChange = { text ->
                        text.toIntOrNull()?.let { h ->
                            viewModel.updateSettings { it.copy(targetHeight = h.coerceIn(160, 640)) }
                        }
                    },
                    label = { Text(stringResource(R.string.setting_height)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            SettingSlider(
                title = stringResource(R.string.setting_rotation, settings.rotationDegrees),
                value = settings.rotationDegrees.toFloat(),
                valueRange = 0f..270f,
                steps = 2,
                onValueChange = { value ->
                    val snapped = (value / 90f).toInt() * 90
                    viewModel.updateSettings { it.copy(rotationDegrees = snapped) }
                }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_battery_saver),
                subtitle = stringResource(R.string.setting_battery_saver_desc),
                checked = settings.batterySaverMode,
                onCheckedChange = { checked ->
                    viewModel.updateSettings { it.copy(batterySaverMode = checked) }
                }
            )

            SettingSwitch(
                title = stringResource(R.string.setting_transmit_on_changes),
                subtitle = stringResource(R.string.setting_transmit_on_changes_desc),
                checked = settings.transmitOnlyOnChanges,
                onCheckedChange = { checked ->
                    viewModel.updateSettings { it.copy(transmitOnlyOnChanges = checked) }
                }
            )

            Text(
                text = stringResource(R.string.crop_section),
                style = MaterialTheme.typography.titleMedium
            )

            SettingSlider(
                title = stringResource(R.string.setting_crop_top, settings.cropTopPercent),
                value = settings.cropTopPercent,
                valueRange = 0f..20f,
                steps = 19,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(cropTopPercent = value) }
                }
            )

            SettingSlider(
                title = stringResource(R.string.setting_crop_bottom, settings.cropBottomPercent),
                value = settings.cropBottomPercent,
                valueRange = 0f..20f,
                steps = 19,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(cropBottomPercent = value) }
                }
            )

            SettingSlider(
                title = stringResource(R.string.setting_diff_threshold, settings.frameDiffThreshold),
                value = settings.frameDiffThreshold,
                valueRange = 0.5f..10f,
                steps = 18,
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(frameDiffThreshold = value) }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
