package com.example.demouniversum

import android.graphics.Path
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import kotlin.concurrent.thread
import kotlin.math.sin


class Binaural3 : AppCompatActivity() {

    // --- Variables de UI ---
    private var isPlaying = false
    private lateinit var playPauseButton: ImageButton
    private lateinit var ampSliderL: Slider
    private lateinit var ampSliderR: Slider
    private lateinit var freqSliderL: Slider
    private lateinit var freqSliderR: Slider
    private lateinit var visualizerView: VisualizerView2

    // --- Variables de Audio ---
    private lateinit var audioTrack: AudioTrack
    private var audioThread: Thread? = null
    private val sampleRate = 44100

    @Volatile private var currentFrequencyL = 440.0
    @Volatile private var currentFrequencyR = 440.0
    @Volatile private var amplitudeLeft = 0.5
    @Volatile private var amplitudeRight = 0.5


    // --- Variables de Visualización ---
    private val leftWavePath = Path()
    private val rightWavePath = Path()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binaural3)
        setupViews()
        setupListeners()
    }

    private fun setupViews() {
        playPauseButton = findViewById(R.id.playPauseButton)
        ampSliderL = findViewById(R.id.ampSlider)
        ampSliderR = findViewById(R.id.ampSlider2)
        freqSliderL = findViewById(R.id.freqSlider)
        freqSliderR = findViewById(R.id.difSlider)
        visualizerView = findViewById(R.id.visualizerView)
        findViewById<ImageButton>(R.id.button_home).setOnClickListener { finish() }


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

        // SLIDERS DE AMPLITUDES
        ampSliderL.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                amplitudeLeft = value.toDouble()
                updateWavePaths()
            }
        }

        ampSliderR.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                amplitudeRight = value.toDouble()
                updateWavePaths()
            }
        }

        // SLIDERS DE FRECUENCIAS
        freqSliderL.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequencyL = value.toDouble()
                updateWavePaths()
            }
        }

        freqSliderR.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequencyR = value.toDouble()
                updateWavePaths()
            }
        }
    }

    private fun startAudio() {
        stopAudio()

        val minBufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(minBufferSizeInBytes)
            .build()

        audioThread = thread(start = true) {
            // El tamaño del buffer viene en bytes, pero usamos un ShortArray, así que dividimos por 2.
            val buffer = ShortArray(minBufferSizeInBytes / 2)
            var phaseL = 0.0
            var phaseR = 0.0

            try {
                audioTrack.play()
                while (!Thread.currentThread().isInterrupted) {
                    // Copias locales para evitar problemas de concurrencia dentro del bucle
                    val localAmpL = amplitudeLeft
                    val localAmpR = amplitudeRight
                    val localFreqL = currentFrequencyL
                    val localFreqR = currentFrequencyR

                    // Generamos las muestras para cada canal (Izquierda y Derecha)
                    for (i in buffer.indices step 2) {
                        // Muestra para el canal Izquierdo
                        buffer[i] = (localAmpL * sin(phaseL) * Short.MAX_VALUE).toInt().toShort()
                        // Muestra para el canal Derecho
                        buffer[i + 1] = (localAmpR * sin(phaseR) * Short.MAX_VALUE).toInt().toShort()

                        // Avanzamos la fase para cada canal de forma independiente
                        phaseL += 2 * Math.PI * localFreqL / sampleRate
                        phaseR += 2 * Math.PI * localFreqR / sampleRate
                    }

                    if (phaseL > 2 * Math.PI) phaseL -= 2 * Math.PI
                    if (phaseR > 2 * Math.PI) phaseR -= 2 * Math.PI

                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                // Nos aseguramos de parar y liberar el AudioTrack de forma segura
                if (this::audioTrack.isInitialized) {
                    if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        audioTrack.stop()
                    }
                    audioTrack.release()
                }
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
        // Usamos una ventana de tiempo fija para que la visualización sea estable
        val timeWindowSeconds = 0.025

        val numSamplesForView = (viewWidth * 2).toInt()

        leftWavePath.moveTo(0f, centerY)
        rightWavePath.moveTo(0f, centerY)

        for (i in 0..numSamplesForView) {
            val x = i.toFloat() / numSamplesForView * viewWidth
            val t = (i.toFloat() / numSamplesForView) * timeWindowSeconds

            // Onda Izquierda: se calcula con su propia frecuencia y amplitud
            val angleL = 2 * Math.PI * currentFrequencyL * t
            val yLeft = centerY - (sin(angleL) * amplitudeLeft * (viewHeight / 2.2)).toFloat()
            leftWavePath.lineTo(x, yLeft)

            // Onda Derecha: se calcula con su propia frecuencia y amplitud
            val angleR = 2 * Math.PI * currentFrequencyR * t
            val yRight = centerY - (sin(angleR) * amplitudeRight * (viewHeight / 2.2)).toFloat()
            rightWavePath.lineTo(x, yRight)
        }

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
