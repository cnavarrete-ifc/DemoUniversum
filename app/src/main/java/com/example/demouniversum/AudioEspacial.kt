package com.example.demouniversum

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import com.google.android.material.slider.Slider

class AudioEspacial : AppCompatActivity() {

    // Engine
    private val engine = SpatialAudioEngine()

    // UI Views
    private lateinit var azimuthSlider: Slider
    private lateinit var elevationSlider: Slider
    private lateinit var playPauseButton: ImageButton
    private lateinit var homeButton: ImageButton
    private lateinit var speakerIcon: ImageView

    @Volatile
    private var isPlaying = false

    // Dynamic movement range
    private var maxHorizontalMovement = 0f
    private var maxVerticalMovement = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Habilitar el modo de borde a borde para usar toda la pantalla
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_audio_espacial)

        setupUI()

        val pcm = loadWavPCM(R.raw.vivaldi)
        engine.setAudioData(pcm)

        // Set initial spatial parameters
        updateSpatialParams()
    }

    private fun setupUI() {
        azimuthSlider = findViewById(R.id.azimuthSlider)
        elevationSlider = findViewById(R.id.elevationSlider)
        playPauseButton = findViewById(R.id.playPauseButton)
        homeButton = findViewById(R.id.button_home)
        speakerIcon = findViewById(R.id.speakerIcon)

        speakerIcon.setImageResource(R.drawable.ic_bocina_off)

        playPauseButton.setOnClickListener {
            if (isPlaying) {
                engine.stop()
                isPlaying = false
                playPauseButton.setImageResource(R.drawable.ic_play)
                speakerIcon.setImageResource(R.drawable.ic_bocina_off)
            } else {
                engine.start()
                isPlaying = true
                playPauseButton.setImageResource(R.drawable.ic_pause)
                speakerIcon.setImageResource(R.drawable.ic_bocina_on)
            }
        }
        homeButton.setOnClickListener { finish() }

        azimuthSlider.addOnChangeListener { _, _, _ ->
            updateSpeakerIconPosition()
            updateSpatialParams()
        }
        elevationSlider.addOnChangeListener { _, _, _ ->
            updateSpeakerIconPosition()
            updateSpatialParams()
        }

        val mainLayout = findViewById<ConstraintLayout>(R.id.main)
        mainLayout.post {
            maxHorizontalMovement = (mainLayout.width - speakerIcon.width) / 2f
            maxVerticalMovement = (mainLayout.height - speakerIcon.height) / 2f
            updateSpeakerIconPosition()
        }
    }

    private fun updateSpatialParams() {
        // Azimuth: Map slider value [0, 1] to [-90, 90] degrees for a frontal arc
        val azimuth = (azimuthSlider.value * 180f) - 90f
        // Elevation: Map slider value [0, 1] to [-90, 90] degrees
        val elevation = (elevationSlider.value * 180f) - 90f

        engine.setAzimuth(azimuth)
        engine.setElevation(elevation)
    }

    private fun loadWavPCM(resId: Int): ShortArray {
        val input = resources.openRawResource(resId).readBytes()
        // Omitir la cabecera WAV de 44 bytes para obtener los datos PCM
        val raw = input.copyOfRange(44, input.size)
        val shortBuffer = java.nio.ByteBuffer.wrap(raw)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val arr = ShortArray(shortBuffer.remaining())
        shortBuffer.get(arr)
        return arr
    }

    private fun updateSpeakerIconPosition() {
        if (!this::speakerIcon.isInitialized || maxHorizontalMovement == 0f) return

        speakerIcon.translationX = (azimuthSlider.value - 0.5f) * 2 * maxHorizontalMovement
        speakerIcon.translationY = (elevationSlider.value - 0.5f) * -2 * maxVerticalMovement
    }

    override fun onPause() {
        super.onPause()
        if (isPlaying) {
            engine.stop()
            isPlaying = false
            playPauseButton.setImageResource(R.drawable.ic_play)
            speakerIcon.setImageResource(R.drawable.ic_bocina_off)
        }
    }

    override fun onDestroy() {
        engine.stop() // Asegurarse de que el motor se detiene
        super.onDestroy()
    }
}
