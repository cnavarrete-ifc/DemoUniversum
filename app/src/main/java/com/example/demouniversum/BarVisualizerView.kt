package com.example.demouniversum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.hypot

class BarVisualizerView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var fftData: ByteArray? = null
    private val paint = Paint()
    private var barWidth = 0f
    private var barSpacing = 0f

    init {
        paint.color = Color.GREEN
    }

    fun updateVisualizer(data: ByteArray) {
        fftData = data
        invalidate() // Tell the view to redraw itself
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // We calculate the bar width and spacing based on the view's width.
        // We only use half of the FFT data.
        val numBars = 128 / 2 // A typical size for the capture is 128, so we get 64 bars.
        if (numBars > 0) {
            val totalSpacing = (numBars - 1) * 2 // Let's use 2px for spacing
            barWidth = (w - totalSpacing) / numBars.toFloat()
            barSpacing = 2f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val data = fftData ?: return

        val height = height.toFloat()
        val numBars = data.size / 2
        var currentX = 0f

        for (i in 0 until numBars) {
            // The FFT data is returned as pairs of real and imaginary parts.
            val re = data[i * 2].toFloat()
            val im = data[i * 2 + 1].toFloat()

            // Calculate the magnitude of the FFT bin
            val magnitude = hypot(re, im)

            // Scale the magnitude to the view height. The values are in a range we can experiment with.
            // A value like 150 seems to work well for this data format.
            val barHeight = (magnitude / 150f) * height
            val top = height - barHeight

            canvas.drawRect(currentX, top, currentX + barWidth, height, paint)
            currentX += barWidth + barSpacing
        }
    }
}