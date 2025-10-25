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


class FreqAmp : AppCompatActivity() {


    // --- Variables de estado y UI ---
    private var isPlaying = false
    private lateinit var playPauseButton: ImageButton
    private lateinit var freqSlider: Slider
    private lateinit var ampSlider: Slider
    // --------------------------------


    // --- Variables de audio ---
    private lateinit var audioTrack: AudioTrack
    private var audioThread: Thread? = null
    private val sampleRate = 44100

    @Volatile // @Volatile permite que todos los hilos actualicen la variable
    private var currentFrequency = 440.0

    @Volatile
    private var currentAmplitude = 0.5
    // ---------------------------


    // --- Variables de visualización ---
    private val wavePath = Path()
    private lateinit var visualizerView: VisualizerView
    // ----------------------------------


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_freq_amp)

        // --- 1. Inicializar Vistas ---
        setupViews()

        // --- 2. Configurar Listeners para los Sliders y Botones ---
        setupListeners()

        // --- 3. Actualizar la gráfica una vez al inicio ---
        updateWavePath()
    }

    private fun setupViews(){
        playPauseButton = findViewById(R.id.playPauseButton)
        freqSlider = findViewById(R.id.freqSlider)
        ampSlider = findViewById(R.id.ampSlider)
        visualizerView = findViewById(R.id.visualizerView)
        visualizerView.post { updateWavePath() }
        findViewById<ImageButton>(R.id.button_home).setOnClickListener { finish() }

        // Corregir valores iniciales de los sliders según el XML
        freqSlider.valueFrom = 44.0f
        freqSlider.valueTo = 880.0f
        freqSlider.stepSize = 0.0f
        freqSlider.value = 440.0f // Un valor intermedio
    }

    private fun setupListeners(){
        // --- Listener para el botón de Play/Pausa ---
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

        // --- Listener para el slider de Frecuencia ---
        freqSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentFrequency = value.toDouble()
                updateWavePath() // Actualiza la forma de la onda para la gráfica
            }
        }

        // --- Listener para el slider de Amplitud ---
        ampSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                currentAmplitude = value.toDouble()
                updateWavePath() // Actualiza la forma de la onda para la gráfica
            }
        }
    }


    private fun startAudio() {
        // Detener cualquier audio previo para evitar duplicadosstopAudio()
        stopAudio()


        // --- Definimos el tamaño del buffer
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = minBufferSize * 1 // Usamos un buffer 4 veces más grande para máxima estabilidad

        // --- Configuración del AudioTrack ---
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
            .setBufferSizeInBytes(bufferSize) // Aplicamos el nuevo tamaño de buffer
            .build()
        // -------------------------------------

        audioThread = thread(start = true) {
            // El buffer local que llenaremos en cada ciclo del bucle. Su tamaño es el mínimo requerido.
            val buffer = ShortArray(minBufferSize)
            var phase = 0.0 // La fase se mantiene entre bucles para una onda continua

            try {
                audioTrack.play()

                // Bucle principal: se ejecuta mientras el hilo no sea interrumpido.
                while (!Thread.currentThread().isInterrupted) {
                    // Copiamos las variables @Volatile a locales una sola vez por ciclo del bucle.
                    // Esto es más eficiente y seguro en entornos multihilo.
                    val localFrequency = currentFrequency
                    val localAmplitude = currentAmplitude

                    // 2. Generamos un bloque completo de audio (un 'buffer' entero).
                    for (i in buffer.indices) {
                        val sineValue = sin(phase)
                        val sample = (sineValue * localAmplitude * Short.MAX_VALUE).toInt().toShort()
                        buffer[i] = sample

                        // Avanzamos la fase para la siguiente muestra.
                        phase += 2 * Math.PI * localFrequency / sampleRate
                    }
                    // Mantenemos la fase en el rango de 0 a 2*PI para evitar que crezca indefinidamente.
                    if (phase > 2 * Math.PI) {
                        phase -= 2 * Math.PI
                    }

                    // 3. Escribimos el bloque completo de datos al AudioTrack.
                    // Esta operación bloquea hasta que hay espacio, previniendo que sobrecarguemos el buffer.
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (e: InterruptedException) {
                // El hilo fue interrumpido, es normal al parar el audio.
                Thread.currentThread().interrupt() // Restablece el estado de interrupción
            } finally {
                // Aseguramos la correcta limpieza del AudioTrack si sale del bucle
                if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop()
                }
                audioTrack.release()
            }
        }
    }

    private fun stopAudio() {
        // Interrumpe el hilo de audio, lo que hará que salga de su bucle 'while'.
        audioThread?.interrupt()
        audioThread = null // Liberamos la referencia al hilo
    }

    private fun updateWavePath() {
        // Salimos si la vista aún no ha sido medida (evita crashes al iniciar)
        if (visualizerView.width == 0 || visualizerView.height == 0) {
            return
        }

        wavePath.reset() // Limpia el path anterior

        // --- Usamos las dimensiones REALES de la vista ---
        val viewWidth = visualizerView.width.toFloat()
        val viewHeight = visualizerView.height.toFloat()
        val centerY = viewHeight / 2
        // --------------------------------------------------

        // --- CÁLCULO CONTINUO DEL NÚMERO DE CICLOS ---
        val minFreq = 44.0
        val maxFreq = 880.0
        val minCycles = 2.0  // Ciclos a mostrar en la frecuencia más baja (44Hz)
        val maxCycles = 20.0 // Ciclos a mostrar en la frecuencia más alta (880Hz)

        // Mapeo lineal: Convierte el rango de frecuencia (44-880) a un rango de ciclos (2-20)
        val freqRatio = (currentFrequency - minFreq) / (maxFreq - minFreq)
        val numCyclesToDraw = minCycles + (freqRatio * (maxCycles - minCycles))
        // --------------------------------------------------------------------------


        // Ancho de la ventana de tiempo para el número de ciclos deseado a la frecuencia actual
        val timeWindow = numCyclesToDraw / currentFrequency
        val numSamplesForView = (viewWidth * 2).toInt() // Más puntos para una onda más suave

        wavePath.moveTo(0f, centerY)

        for (i in 0..numSamplesForView) {
            val x = i.toFloat() / numSamplesForView * viewWidth

            // Ángulo basado en la progresión a través de la ventana de tiempo
            val angle = (i.toFloat() / numSamplesForView) * (2 * Math.PI) * (currentFrequency * timeWindow)

            // El valor de 'y' es la onda senoidal, escalado por la amplitud y la altura de la vista
            val y = centerY - (sin(angle) * currentAmplitude * (viewHeight / 2.2)).toFloat()

            wavePath.lineTo(x, y)
        }

        // --- Le pasamos el Path a nuestra vista para que se dibuje ---
        visualizerView.setWavePath(wavePath)
    }

    override fun onStop() {
        super.onStop()
        // Es crucial detener el audio si la app pasa a segundo plano.
        if (isPlaying) {
            isPlaying = false
            stopAudio()
            playPauseButton.setImageResource(R.drawable.ic_play)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio() // Liberar recursos al destruir la Activity.
    }

}