package com.example.displayconnect.models

/**
 * Configurações persistidas do aplicativo.
 */
data class AppSettings(
    val espIp: String = "192.168.4.1",
    val espPort: Int = 81,
    val targetFps: Int = 15,
    val jpegQuality: Int = 70,
    val targetWidth: Int = 240,
    val targetHeight: Int = 320,
    val rotationDegrees: Int = 0,
    val batterySaverMode: Boolean = false,
    val transmitOnlyOnChanges: Boolean = true,
    val cropTopPercent: Float = 4f,
    val cropBottomPercent: Float = 6f,
    val cropLeftPercent: Float = 0f,
    val cropRightPercent: Float = 0f,
    val frameDiffThreshold: Float = 2f
) {
    val resolutionLabel: String get() = "${targetWidth}×${targetHeight}"
}
