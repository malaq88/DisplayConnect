package com.example.displayconnect.capture

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Detecta diferenças entre quadros consecutivos para evitar envios desnecessários.
 * Compara uma versão reduzida em escala de cinza para eficiência.
 */
class FrameDiffer(
    private var thresholdPercent: Float = 2f,
    sampleWidth: Int = 48,
    sampleHeight: Int = 64
) {

    private val sampleW = sampleWidth
    private val sampleH = sampleHeight
    private var previousPixels: IntArray? = null
    private val currentPixels = IntArray(sampleW * sampleH)

    fun updateThreshold(threshold: Float) {
        thresholdPercent = threshold.coerceIn(0.5f, 20f)
    }

    /**
     * @return true se o quadro difere o suficiente do anterior para ser transmitido.
     */
    fun hasSignificantChange(source: Bitmap): Boolean {
        val scaled = Bitmap.createScaledBitmap(source, sampleW, sampleH, true)
        scaled.getPixels(currentPixels, 0, sampleW, 0, 0, sampleW, sampleH)
        if (scaled !== source) scaled.recycle()

        val previous = previousPixels
        if (previous == null) {
            previousPixels = currentPixels.copyOf()
            return true
        }

        var diffSum = 0L
        for (i in currentPixels.indices) {
            val c1 = currentPixels[i]
            val c2 = previous[i]
            diffSum += abs(Color.red(c1) - Color.red(c2))
            diffSum += abs(Color.green(c1) - Color.green(c2))
            diffSum += abs(Color.blue(c1) - Color.blue(c2))
        }

        System.arraycopy(currentPixels, 0, previous, 0, currentPixels.size)

        val maxDiff = currentPixels.size * 3L * 255L
        val changePercent = diffSum * 100f / maxDiff
        return changePercent >= thresholdPercent
    }

    fun reset() {
        previousPixels = null
    }
}
