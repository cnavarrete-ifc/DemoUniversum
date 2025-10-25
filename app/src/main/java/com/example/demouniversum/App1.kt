package com.example.demouniversum

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.sin
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class App1 : AppCompatActivity() {

    // Variable que indica si el audio se está reproduciendo
    private var isPlaying = false

    // Objeto AudioTrack para reproducir el audio
    private lateinit var audioTrack: AudioTrack

    // Creamos el hilo de audio para no bloquear la UI
    private var audioThread: Thread? = null


    // Configuración del audio //
    private val sampleRate = 44100  // Frecuencia estandar de muestreo en CD
    private val frequency = 440.0    // Frecuencia de la nota La4

    // Buffer pre-calculado para la onda sinusoidal
    private lateinit var sineWaveBuffer: ShortArray



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app1)

        // Pre-generamos la onda sinusoidal 1 sola vez
        generateSineWave()

        // Referencias a los botones
        val button_home = findViewById<ImageButton>(R.id.button_home)
        val button_play = findViewById<ImageButton>(R.id.playPauseButton)


        // Listener Botón Home
        button_home.setOnClickListener {
            finish()  // Se termina esta activity y se regresa a MainActivity
        }

        // Listener para el botón Play/Pausa
        button_play.setOnClickListener {
            if (isPlaying) {
                stopSound()
                button_play.setImageResource(R.drawable.ic_play)
            } else {
                playSound()
                button_play.setImageResource(R.drawable.ic_pause)
            }

            // Invertimos el estado de isPlaying
            isPlaying = !isPlaying
        }
    }




    // ---------- Funciones para manejo de audio --------------


    // Genera la onda sinusoidal una sola vez
    private fun generateSineWave() {
        val numSamplesInCycle = (sampleRate / frequency).toInt()
        sineWaveBuffer = ShortArray(numSamplesInCycle)

        for (i in 0 until numSamplesInCycle) {
            val angle = 2.0 * Math.PI * i / numSamplesInCycle
            sineWaveBuffer[i] = (sin(angle) * Short.MAX_VALUE).toInt().toShort()
        }
    }


    // Reproduce audio
    private fun playSound() {

        // Crea el buffer de audio
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        // Crea la instancia del AudioTrack
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        // Inicia el hilo de audio
        audioThread = thread(start = true) {
            var positionInSineWave = 0
            val playbackBuffer = ShortArray(bufferSize)


            // Empieza a reproducir
            audioTrack.play()

            // Bucle para reproducir la onda sinusoidal repetidamente
            while (!(Thread.currentThread().isInterrupted)) {
                for (i in playbackBuffer.indices) {
                    playbackBuffer[i] = sineWaveBuffer[positionInSineWave]
                    positionInSineWave++
                    // Si hemos recorrido toda la onda sinusoidal, reiniciamos la posición
                    if (positionInSineWave >= sineWaveBuffer.size) {
                        positionInSineWave = 0
                    }
                }
                // Escribe los datos de audio en el AudioTrack
                audioTrack.write(playbackBuffer, 0, playbackBuffer.size)
            }
        }
    }


    // Detiene el audio
    private fun stopSound() {
        audioThread?.interrupt()
        audioThread = null

        // Detener y liberar los recursos de AudioTrack de forma segura
        if (this::audioTrack.isInitialized && audioTrack.state == AudioTrack.STATE_INITIALIZED) {
            audioTrack.stop()
            audioTrack.release()
        }
    }

    // --- 5. Liberar recursos al destruir la actividad ---
    override fun onDestroy() {
        super.onDestroy()
        // Nos aseguramos de que el sonido se detenga si el usuario cierra la app
        stopSound()
    }
}

