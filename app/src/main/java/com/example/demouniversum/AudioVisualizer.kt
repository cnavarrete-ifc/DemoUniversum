package com.example.demouniversum

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class AudioVisualizer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val audioPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private var audioPath: Path? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        audioPath?.let {
            canvas.drawPath(it, audioPaint)
        }
    }

    fun setAudioPath(newPath: Path) {
        this.audioPath = newPath
        invalidate()
    }
}