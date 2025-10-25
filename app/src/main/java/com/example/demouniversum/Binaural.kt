package com.example.demouniversum

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.Path
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.slider.Slider
import kotlin.concurrent.thread
import kotlin.math.sin


class Binaural : AppCompatActivity() {

    // --- Variables de UI ---
    private var isPlaying = false
    private lateinit var playPauseButton: ImageButton
    private lateinit var balanceSlider: Slider
    private lateinit var freqSlider: Slider
    private lateinit var delaySlider: Slider
    private lateinit var visualizerView: VisualizerView2

    // --- Variables de Audio ---
    private lateinit var audioTrack: AudioTrack
    private var audioThread: Thread? = null
    private val sampleRate = 44100

    @Volatile private var currentFrequency = 440.0
    @Volatile private var amplitudeRight = 0.5 // Amplitud del canal derecho (0 a 1)
    @Volatile private var amplitudeLeft = 0.5  // Amplitud del canal izquierdo (0 a 1)
    @Volatile private var phaseDelayMs = 0.0   // Retraso en milisegundos (-10 a 10)

    // --- Variables de Visualización ---
    private val leftWavePath = Path()
    private val rightWavePath = Path()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binaural)
        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        playPauseButton = findViewById(R.id.playPauseButton)
        balanceSlider = findViewById(R.id.ampSlider) // Tu XML usa ampSlider, lo mantenemos pero lo tratamos como balance
        freqSlider = findViewById(R.id.freqSlider)
        delaySlider = findViewById(R.id.difSlider) // Nuevo
        visualizerView = findViewById(R.id.visualizerView)
        findViewById<ImageButton>(R.id.button_home).setOnClickListener { finish() }

        // Configuración sliders
        freqSlider.valueFrom = 44.0f
        freqSlider.valueTo = 880.0f
        freqSlider.stepSize = 0.0f
        freqSlider.value = 440.0f

        // La vista se actualiza sola la primera vez
        visualizerView.post { updateWavePaths() }
    }

    private fun setupListeners() {
        playPauseButton.setOnClickListener {
            isPlaying = !isPlaying
            if (isPlaying) {
                startAudio()
                playPauseButton.setImageResource(R.drawable.ic_pause)
            } else {
                stopAudio()
                playPauseButton.setImageResource(R.drawable.ic_play)
            }
        }

        // SLIDER DE BALANCE (L/R)
        balanceSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                amplitudeRight = value.toDouble()
                amplitudeLeft = 1.0 - amplitudeRight // Balance inverso
                updateWavePaths()
            }
        }

        // SLIDER DE FRECUENCIA
        freqSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequency = value.toDouble()
                updateWavePaths()
            }
        }

        // SLIDER DE RETRASO
        delaySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                phaseDelayMs = value.toDouble()
                updateWavePaths()
            }
        }
    }

    private fun startAudio() {
        stopAudio()

        // IMPORTANTE: Configurar para ESTÉREO (CHANNEL_OUT_STEREO)
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = minBufferSize * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(bufferSize)
            .build()

        audioThread = thread(start = true) {
            // Buffer estéreo, el doble de grande (Izquierda, Derecha, Izquierda, Derecha...)
            val buffer = ShortArray(minBufferSize * 2)
            var phaseLeft = 0.0
            // La fase derecha empieza con el retraso inicial
            var phaseRightOffset = 2 * Math.PI * currentFrequency * (phaseDelayMs / 1000.0)

            try {
                audioTrack.play()
                while (!Thread.currentThread().isInterrupted) {
                    val localFrequency = currentFrequency
                    val localAmpLeft = amplitudeLeft
                    val localAmpRight = amplitudeRight
                    val localDelayMs = phaseDelayMs

                    // Recalcula el desfase de la fase derecha solo si cambia
                    val newPhaseRightOffset = 2 * Math.PI * localFrequency * (localDelayMs / 1000.0)
                    if (newPhaseRightOffset != phaseRightOffset) {
                        phaseRightOffset = newPhaseRightOffset
                    }

                    // Generar muestras estéreo
                    for (i in buffer.indices step 2) {
                        val phaseRight = phaseLeft + phaseRightOffset

                        val sineLeft = sin(phaseLeft)
                        val sineRight = sin(phaseRight)

                        buffer[i] = (sineLeft * localAmpLeft * Short.MAX_VALUE).toInt().toShort()   // Muestra Izquierda
                        buffer[i + 1] = (sineRight * localAmpRight * Short.MAX_VALUE).toInt().toShort() // Muestra Derecha

                        phaseLeft += 2 * Math.PI * localFrequency / sampleRate
                    }
                    if (phaseLeft > 2 * Math.PI) phaseLeft -= 2 * Math.PI

                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    private fun stopAudio() {
        audioThread?.interrupt()
        audioThread = null
    }

    private fun updateWavePaths() {
        if (visualizerView.width == 0 || visualizerView.height == 0) return

        leftWavePath.reset()
        rightWavePath.reset()

        val viewWidth = visualizerView.width.toFloat()
        val viewHeight = visualizerView.height.toFloat()
        val centerY = viewHeight / 2

        // --- SOLUCIÓN: FIJAR LA VENTANA DE TIEMPO EN LUGAR DEL NÚMERO DE CICLOS ---
        // Vamos a mostrar siempre un lapso fijo de tiempo en la pantalla.
        // 25ms (0.025s) es un buen valor para ver bien las frecuencias de tu rango.
        val timeWindowSeconds = 0.025
        // -------------------------------------------------------------------------

        // Ya no usamos numCyclesToDraw.
        val numSamplesForView = (viewWidth * 2).toInt()

        leftWavePath.moveTo(0f, centerY)
        rightWavePath.moveTo(0f, centerY)

        // Calcular el desfase angular a partir del retraso en milisegundos.
        // Esta fórmula es correcta y no necesita cambios.
        val phaseOffset = 2 * Math.PI * currentFrequency * (phaseDelayMs / 1000.0)

        for (i in 0..numSamplesForView) {
            val x = i.toFloat() / numSamplesForView * viewWidth

            // --- SOLUCIÓN: EL ÁNGULO SE CALCULA BASADO EN LA VENTANA DE TIEMPO FIJA ---
            // Calculamos el tiempo 't' en el punto actual del dibujo (de 0 a timeWindowSeconds)
            val t = (i.toFloat() / numSamplesForView) * timeWindowSeconds
            // El ángulo ahora es 2*PI*f*t, la fórmula física de la fase.
            val angle = 2 * Math.PI * currentFrequency * t
            // -----------------------------------------------------------------------------

            // Onda Izquierda
            val yLeft = centerY - (sin(angle) * amplitudeLeft * (viewHeight / 2.2)).toFloat()
            leftWavePath.lineTo(x, yLeft)

            // Onda Derecha (con el desfase angular aplicado)
            val yRight = centerY - (sin(angle + phaseOffset) * amplitudeRight * (viewHeight / 2.2)).toFloat()
            rightWavePath.lineTo(x, yRight)
        }

        // La llamada final sigue siendo la misma
        visualizerView.setWavePaths(leftWavePath, rightWavePath)
    }

    override fun onStop() {
        super.onStop()
        if (isPlaying) {
            isPlaying = false
            stopAudio()
            playPauseButton.setImageResource(R.drawable.ic_play)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }
}