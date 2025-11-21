package com.example.demouniversum

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
    private lateinit var visualizerView: VisualizerView3
    private lateinit var animometroButton: Button

    // --- Variables de Audio ---
    private lateinit var audioTrack: AudioTrack
    private var audioThread: Thread? = null
    private val sampleRate = 44100

    @Volatile private var currentFrequencyL = 440.0
    @Volatile private var currentFrequencyR = 440.0
    @Volatile private var amplitudeLeft = 0.5
    @Volatile private var amplitudeRight = 0.5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
        animometroButton = findViewById(R.id.animometroButton)

        // La vista se actualiza sola la primera vez
        visualizerView.post { updateVisualizer() }
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

        animometroButton.setOnClickListener {
            val intent = Intent(this, Animometro::class.java)
            startActivity(intent)
        }

        // SLIDERS DE AMPLITUDES
        ampSliderL.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                amplitudeLeft = value.toDouble()
                updateVisualizer()
            }
        }

        ampSliderR.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                amplitudeRight = value.toDouble()
                updateVisualizer()
            }
        }

        // SLIDERS DE FRECUENCIAS
        freqSliderL.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequencyL = value.toDouble()
                updateVisualizer()
            }
        }

        freqSliderR.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequencyR = value.toDouble()
                updateVisualizer()
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
            val buffer = ShortArray(minBufferSizeInBytes / 2)
            var phaseL = 0.0
            var phaseR = 0.0

            try {
                audioTrack.play()
                while (!Thread.currentThread().isInterrupted) {
                    val localAmpL = amplitudeLeft
                    val localAmpR = amplitudeRight
                    val localFreqL = currentFrequencyL
                    val localFreqR = currentFrequencyR

                    for (i in buffer.indices step 2) {
                        buffer[i] = (localAmpL * sin(phaseL) * Short.MAX_VALUE).toInt().toShort()
                        buffer[i + 1] = (localAmpR * sin(phaseR) * Short.MAX_VALUE).toInt().toShort()

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

    private fun updateVisualizer() {
        visualizerView.setWaveParameters(
            currentFrequencyL,
            amplitudeLeft,
            currentFrequencyR,
            amplitudeRight
        )
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
