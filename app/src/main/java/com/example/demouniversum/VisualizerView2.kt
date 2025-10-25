package com.example.demouniversum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View


class VisualizerView2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Pincel para la onda izquierda (cian)
    private val leftWavePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    // Pincel para la onda derecha (por ejemplo, magenta)
    private val rightWavePaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private var leftPath: Path? = null
    private var rightPath: Path? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibuja el Path izquierdo si existe
        leftPath?.let {
            canvas.drawPath(it, leftWavePaint)
        }
        // Dibuja el Path derecho si existe
        rightPath?.let {
            canvas.drawPath(it, rightWavePaint)
        }
    }

    /**
     * Método para actualizar los Paths y solicitar un redibujado.
     */
    fun setWavePaths(newLeftPath: Path, newRightPath: Path) {
        this.leftPath = newLeftPath
        this.rightPath = newRightPath
        invalidate() // Solicita que se llame a onDraw() de nuevo
    }

    fun setWavePath(newPath: Path) {
        this.currentPath = newPath
        // invalidate() le dice al sistema: "¡Esta vista ha cambiado! Por favor,
        // vuelve a llamar a onDraw() para redibujarla lo antes posible."
        invalidate()
    }

    private var currentPath: Path? = null
}