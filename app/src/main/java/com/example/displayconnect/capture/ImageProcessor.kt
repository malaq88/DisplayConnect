package com.example.displayconnect.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import com.example.displayconnect.models.AppSettings
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Processa capturas de tela: recorte, redimensionamento com letterbox e compressão JPEG.
 * Reutiliza buffers para minimizar alocações.
 */
class ImageProcessor(private var settings: AppSettings) {

    private var outputBitmap: Bitmap? = null
    private val jpegStream = ByteArrayOutputStream(32_768)
    private val canvas = Canvas()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val srcRect = Rect()
    private val dstRect = Rect()
    private val matrix = Matrix()

    fun updateSettings(settings: AppSettings) {
        this.settings = settings
        outputBitmap?.recycle()
        outputBitmap = null
    }

    fun process(source: Bitmap): Pair<ByteArray, Int>? {
        val cropped = cropSource(source) ?: return null
        val needsCropRecycle = cropped !== source
        scaleWithLetterbox(cropped, needsCropRecycle)
        val scaled = outputBitmap ?: return null
        val rotated = applyRotation(scaled)

        jpegStream.reset()
        val quality = if (settings.batterySaverMode) {
            (settings.jpegQuality * 0.75f).toInt().coerceIn(30, 100)
        } else {
            settings.jpegQuality
        }
        if (!rotated.compress(Bitmap.CompressFormat.JPEG, quality, jpegStream)) {
            if (rotated !== scaled) rotated.recycle()
            return null
        }

        if (rotated !== scaled) rotated.recycle()
        val bytes = jpegStream.toByteArray()
        return bytes to bytes.size
    }

    fun release() {
        outputBitmap?.recycle()
        outputBitmap = null
        jpegStream.reset()
    }

    private fun cropSource(source: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return null

        val left = (w * settings.cropLeftPercent / 100f).toInt().coerceIn(0, w - 1)
        val top = (h * settings.cropTopPercent / 100f).toInt().coerceIn(0, h - 1)
        val right = w - (w * settings.cropRightPercent / 100f).toInt().coerceIn(0, w - 1)
        val bottom = h - (h * settings.cropBottomPercent / 100f).toInt().coerceIn(0, h - 1)

        if (right <= left || bottom <= top) return source

        return if (left == 0 && top == 0 && right == w && bottom == h) {
            source
        } else {
            Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        }
    }

    private fun scaleWithLetterbox(source: Bitmap, recycleSource: Boolean) {
        val targetW = settings.targetWidth
        val targetH = settings.targetHeight

        val output = getOrCreateOutputBitmap(targetW, targetH)
        output.eraseColor(Color.BLACK)

        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        val scale = min(targetW / srcW, targetH / srcH)

        val scaledW = (srcW * scale).toInt()
        val scaledH = (srcH * scale).toInt()
        val offsetX = (targetW - scaledW) / 2
        val offsetY = (targetH - scaledH) / 2

        srcRect.set(0, 0, source.width, source.height)
        dstRect.set(offsetX, offsetY, offsetX + scaledW, offsetY + scaledH)

        canvas.setBitmap(output)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(source, srcRect, dstRect, paint)

        if (recycleSource && source !== output) {
            source.recycle()
        }
    }

    private fun applyRotation(source: Bitmap): Bitmap {
        val degrees = settings.rotationDegrees % 360
        if (degrees == 0) return source

        matrix.reset()
        matrix.postRotate(degrees.toFloat())

        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }

    private fun getOrCreateOutputBitmap(width: Int, height: Int): Bitmap {
        val existing = outputBitmap
        if (existing != null && existing.width == width && existing.height == height && !existing.isRecycled) {
            return existing
        }
        existing?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            outputBitmap = it
        }
    }
}
