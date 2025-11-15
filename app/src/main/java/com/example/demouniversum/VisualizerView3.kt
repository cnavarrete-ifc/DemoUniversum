package com.example.demouniversum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class VisualizerView3 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Pincel para la onda izquierda (cian)
    private val leftWavePaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Pincel para la onda derecha (magenta)
    private val rightWavePaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Pincel para la onda combinada (amarillo)
    private val combinedWavePaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val leftPath = Path()
    private val rightPath = Path()
    private val combinedPath = Path()

    // Almacenamos los parámetros para poder regenerar los paths si la vista cambia de tamaño.
    private var frequencyL: Double = 0.0
    private var amplitudeL: Double = 0.0
    private var frequencyR: Double = 0.0
    private var amplitudeR: Double = 0.0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Si el tamaño de la vista cambia, actualizamos los paths
        updatePaths()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibuja las tres ondas
        canvas.drawPath(leftPath, leftWavePaint)
        canvas.drawPath(rightPath, rightWavePaint)
        canvas.drawPath(combinedPath, combinedWavePaint)
    }

    /**
     * Actualiza los parámetros de las ondas y regenera los paths para el dibujo.
     * La vista se encarga de generar las ondas y su suma.
     */
    fun setWaveParameters(freqL: Double, ampL: Double, freqR: Double, ampR: Double) {
        this.frequencyL = freqL
        this.amplitudeL = ampL
        this.frequencyR = freqR
        this.amplitudeR = ampR
        updatePaths()
        invalidate() // Solicita redibujar
    }

    private fun updatePaths() {
        if (width == 0 || height == 0) return

        leftPath.reset()
        rightPath.reset()
        combinedPath.reset()

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val centerY = viewHeight / 2f
        // Usamos una ventana de tiempo fija para que la visualización sea estable
        val timeWindowSeconds = 0.025

        val numSamplesForView = (viewWidth).toInt()

        leftPath.moveTo(0f, centerY)
        rightPath.moveTo(0f, centerY)
        combinedPath.moveTo(0f, centerY)

        for (i in 0..numSamplesForView) {
            val x = i.toFloat()
            // El tiempo 't' progresa a medida que avanzamos en el eje X de la vista
            val t = (i.toFloat() / numSamplesForView) * timeWindowSeconds

            // Componente Y de la onda izquierda
            val angleL = 2 * Math.PI * frequencyL * t
            val yL = (sin(angleL) * amplitudeL * (viewHeight / 2.2)).toFloat()
            leftPath.lineTo(x, centerY - yL)

            // Componente Y de la onda derecha
            val angleR = 2 * Math.PI * frequencyR * t
            val yR = (sin(angleR) * amplitudeR * (viewHeight / 2.2)).toFloat()
            rightPath.lineTo(x, centerY - yR)

            // La onda combinada es la suma de las componentes Y.
            // Se divide entre 2 para asegurar que la amplitud de la suma no se salga de la vista.
            val yCombined = (yL + yR) / 2f
            combinedPath.lineTo(x, centerY - yCombined)
        }
    }
}
