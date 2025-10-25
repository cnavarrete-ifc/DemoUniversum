package com.example.demouniversum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View


class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 1. Preparamos el "pincel" para dibujar la onda
    private val wavePaint = Paint().apply {
        color = Color.parseColor("#FFFFFF") // Color blanco para la onda
        style = Paint.Style.STROKE // Queremos que dibuje solo la línea, no el relleno
        strokeWidth = 5f // Grosor de la línea
        isAntiAlias = true // Suaviza los bordes de la línea
    }

    // El Path que contendrá la forma de la onda a dibujar
    private var currentPath: Path? = null

    /**
     * Este es el método más importante. Se llama automáticamente cuando la vista necesita
     * ser dibujada o redibujada (por ejemplo, después de llamar a invalidate()).
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Si tenemos un Path para dibujar, lo pintamos en el lienzo (Canvas)
        currentPath?.let {
            canvas.drawPath(it, wavePaint)
        }
    }

    /**
     * Un método público que nuestra Activity usará para pasarle la nueva forma
     * de la onda que hemos calculado.
     */
    fun setWavePath(newPath: Path) {
        this.currentPath = newPath
        // invalidate() le dice al sistema: "¡Esta vista ha cambiado! Por favor,
        // vuelve a llamar a onDraw() para redibujarla lo antes posible."
        invalidate()
    }
}