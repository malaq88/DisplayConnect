package com.example.displayconnect.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import com.example.displayconnect.models.AppSettings
import com.example.displayconnect.network.DisplaySocketClient
import com.example.displayconnect.utils.StatsTracker
import com.example.displayconnect.utils.TransmissionHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * Gerencia a captura de tela via MediaProjection com processamento em background.
 */
class ScreenCaptureManager(
    private val context: Context,
    private val socketClient: DisplaySocketClient
) {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val imageProcessor = ImageProcessor(AppSettings())
    private val frameDiffer = FrameDiffer()
    private val statsTracker = StatsTracker()

    private var settings = AppSettings()
    private var isCapturing = false
    private var lastFrameTimeNs = 0L
    private var reusableBitmap: Bitmap? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            stopCapture()
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        imageProcessor.updateSettings(newSettings)
        frameDiffer.updateThreshold(newSettings.frameDiffThreshold)
    }

    fun start(projection: MediaProjection) {
        if (isCapturing) return

        mediaProjection = projection
        projection.registerCallback(projectionCallback, null)

        val metrics = getDisplayMetrics()
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        captureThread = HandlerThread("DisplayCapture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader!!.setOnImageAvailableListener({ reader ->
            processImage(reader)
        }, captureHandler)

        virtualDisplay = projection.createVirtualDisplay(
            "DisplayConnectCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            captureHandler
        )

        isCapturing = true
        _isRunning.value = true
        TransmissionHub.setTransmitting(true)
        statsTracker.reset()
        frameDiffer.reset()
        lastFrameTimeNs = 0L
    }

    fun stopCapture() {
        if (!isCapturing && mediaProjection == null) return

        isCapturing = false
        _isRunning.value = false
        TransmissionHub.setTransmitting(false)

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null

        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        reusableBitmap?.recycle()
        reusableBitmap = null

        imageProcessor.release()
        frameDiffer.reset()
        statsTracker.reset()
    }

    private fun processImage(reader: ImageReader) {
        if (!isCapturing) return

        val now = System.nanoTime()
        val minIntervalNs = 1_000_000_000L / settings.targetFps.coerceIn(MIN_FPS, MAX_FPS)
        if (lastFrameTimeNs > 0 && now - lastFrameTimeNs < minIntervalNs) {
            reader.acquireLatestImage()?.close()
            return
        }
        lastFrameTimeNs = now

        val image = reader.acquireLatestImage() ?: return
        try {
            val bitmap = imageToBitmap(image) ?: return

            if (settings.transmitOnlyOnChanges && !frameDiffer.hasSignificantChange(bitmap)) {
                statsTracker.onFrameSkipped()
                publishStats()
                return
            }

            val result = imageProcessor.process(bitmap)
            if (bitmap !== reusableBitmap) {
                bitmap.recycle()
            }

            if (result == null) return
            val (jpegBytes, size) = result

            if (socketClient.sendFrame(jpegBytes, 0, size)) {
                statsTracker.onFrameSent(size + 4)
            }
            publishStats()
        } finally {
            image.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = getOrCreateBitmap(image.width + rowPadding / pixelStride, image.height)
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
    }

    private fun getOrCreateBitmap(width: Int, height: Int): Bitmap {
        val existing = reusableBitmap
        if (existing != null && existing.width == width && existing.height == height && !existing.isRecycled) {
            return existing
        }
        existing?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            reusableBitmap = it
        }
    }

    private fun publishStats() {
        TransmissionHub.updateStats(
            statsTracker.currentStats(settings.resolutionLabel)
        )
    }

    private fun getDisplayMetrics(): DisplayMetrics {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    companion object {
        const val MIN_FPS = 5
        const val MAX_FPS = 30
    }
}
