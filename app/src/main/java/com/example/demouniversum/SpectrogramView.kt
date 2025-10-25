package com.example.demouniversum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.log10

class SpectrogramView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private var magnitudes: FloatArray? = null

    private val barPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 1f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
    }

    // Márgenes para las etiquetas
    private val leftPadding = 90f
    private val bottomPadding = 50f
    private val topPadding = 20f
    private val rightPadding = 20f

    // Constantes para el cálculo de FFT
    private val sampleRate = 44100f
    private val fftSize = 2048f

    // Rango de visualización estándar y profesional para audio de 16-bit normalizado
    private val minFreq = 20f
    private val maxFreq = 20000f
    private val minDb = -96f  // Límite inferior (silencio teórico)
    private val maxDb = 0f    // Límite superior (máximo digital)

    fun updateSpectrogram(data: Array<FloatArray>?) {
        magnitudes = data?.firstOrNull()
        invalidate() // Redibuja la vista
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Definir el área de la gráfica, excluyendo los márgenes
        val graphWidth = width - leftPadding - rightPadding
        val graphHeight = height - topPadding - bottomPadding
        val graphLeft = leftPadding
        val graphTop = topPadding

        // Dibuja la cuadrícula y las etiquetas en los márgenes
        drawGridAndLabels(canvas, graphLeft, graphTop, graphWidth, graphHeight)

        val mags = magnitudes ?: return
        if (mags.isEmpty()) return

        val logMinFreq = log10(minFreq)
        val logMaxFreq = log10(maxFreq)

        // Función para convertir una frecuencia a una coordenada X (dentro del área de la gráfica)
        fun freqToX(freq: Float): Float {
            if (freq <= minFreq) return graphLeft
            if (freq >= maxFreq) return graphLeft + graphWidth
            val logFreq = log10(freq)
            return graphLeft + graphWidth * (logFreq - logMinFreq) / (logMaxFreq - logMinFreq)
        }

        // Función para convertir una magnitud en dB a una coordenada Y (dentro del área de la gráfica)
        fun dbToY(db: Float): Float {
            val clampedDb = db.coerceIn(minDb, maxDb)
            return graphTop + graphHeight - (((clampedDb - minDb) / (maxDb - minDb)) * graphHeight)
        }

        // Dibuja las barras dentro del área de la gráfica
        for (i in mags.indices) {
            val currentFreq = i * sampleRate / fftSize
            val nextFreq = (i + 1) * sampleRate / fftSize

            if (currentFreq < maxFreq && nextFreq > minFreq) {
                val db = mags[i]
                if (db.isFinite()) {
                    val startX = freqToX(currentFreq)
                    val endX = freqToX(nextFreq)
                    val topY = dbToY(db)
                    val bottomY = graphTop + graphHeight

                    canvas.drawRect(startX, topY, endX, bottomY, barPaint)
                }
            }
        }
    }

    private fun drawGridAndLabels(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
        val right = left + width
        val bottom = top + height

        // --- ETIQUETAS Y CUADRÍCULA DE FRECUENCIA (EJE X) ---
        // Lista extendida para tener más etiquetas de frecuencia
        val freqLabels = floatArrayOf(30f, 50f, 100f, 200f, 500f, 1000f, 2000f, 5000f, 10000f, 15000f)
        val logMinFreq = log10(minFreq)
        val logMaxFreq = log10(maxFreq)
        textPaint.textAlign = Paint.Align.CENTER

        freqLabels.forEach { freq ->
            if (freq in minFreq..maxFreq) {
                val logFreq = log10(freq)
                val x = left + width * (logFreq - logMinFreq) / (logMaxFreq - logMinFreq)
                canvas.drawLine(x, top, x, bottom, gridPaint) // Línea vertical
                val label = if (freq >= 1000) "${(freq / 1000).toInt()}k" else freq.toInt().toString()
                // Dibuja la etiqueta DEBAJO del área de la gráfica
                canvas.drawText(label, x, bottom + textPaint.textSize + 10, textPaint)
            }
        }

        // --- ETIQUETAS Y CUADRÍCULA DE AMPLITUD (EJE Y) ---
        textPaint.textAlign = Paint.Align.RIGHT

        // Bucle ajustado para el rango estándar de -96 a 0 dB
        for (db in -96..0 step 12) {
            val y = top + height - (((db.toFloat() - minDb) / (maxDb - minDb)) * height)
            canvas.drawLine(left, y, right, y, gridPaint) // Línea horizontal
            canvas.drawText("$db dB", left - 15, y + (textPaint.textSize / 3), textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT // Resetear alineación
    }
}
