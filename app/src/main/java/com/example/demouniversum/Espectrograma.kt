package com.example.demouniversum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.jtransforms.fft.FloatFFT_1D
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

class Espectrograma : AppCompatActivity() {

    private lateinit var spectrogramView: SpectrogramView
    private lateinit var playPauseButton: ImageButton
    private lateinit var homeButton: ImageButton

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    companion object {
        private const val TAG = "Espectrograma"
        private const val PERMISSION_REQUEST_CODE = 101
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val FFT_SIZE = 2048 // Tamaño de la ventana de análisis
        private const val HOP_SIZE = FFT_SIZE / 2 // Solapamiento del 50% (1024 muestras)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_espectrograma)

        spectrogramView = findViewById(R.id.visualizerView)
        playPauseButton = findViewById(R.id.playPauseButton)
        homeButton = findViewById(R.id.button_home)

        playPauseButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        homeButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST_CODE)
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de audio no concedido", Toast.LENGTH_SHORT).show()
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Toast.makeText(this, "Parámetros de grabación no soportados", Toast.LENGTH_SHORT).show()
            return
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(FFT_SIZE)

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Toast.makeText(this, "No se pudo inicializar AudioRecord", Toast.LENGTH_SHORT).show()
            return
        }

        isRecording = true
        audioRecord?.startRecording()
        playPauseButton.setImageResource(R.drawable.ic_pause)

        recordingThread = Thread {
            processAudio()
        }
        recordingThread?.start()
    }

    private fun stopRecording() {
        isRecording = false
        recordingThread?.interrupt()
        try {
            recordingThread?.join(500)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupción al esperar al hilo de grabación", e)
        }

        audioRecord?.apply {
            if (state == AudioRecord.STATE_INITIALIZED) {
                try {
                    stop()
                    release()
                } catch(e: Exception) {
                    Log.e(TAG, "Error al detener y liberar AudioRecord", e)
                }
            }
        }
        audioRecord = null
        playPauseButton.setImageResource(R.drawable.ic_play)
    }

    private fun processAudio() {
        val readBuffer = ShortArray(HOP_SIZE)
        val analysisBuffer = FloatArray(FFT_SIZE) 
        val fftBuffer = FloatArray(FFT_SIZE * 2)
        val fft = FloatFFT_1D(FFT_SIZE.toLong())

        val hannWindow = DoubleArray(FFT_SIZE) { 0.5 * (1 - cos(2 * Math.PI * it / (FFT_SIZE - 1))) }

        while (isRecording && !Thread.currentThread().isInterrupted) {
            val readResult = audioRecord?.read(readBuffer, 0, HOP_SIZE) ?: -1
            if (readResult > 0) {
                System.arraycopy(analysisBuffer, HOP_SIZE, analysisBuffer, 0, HOP_SIZE)

                for (i in 0 until HOP_SIZE) {
                    analysisBuffer[i + HOP_SIZE] = readBuffer[i] / 32768.0f // 1. Normalización de Entrada
                }

                for (i in 0 until FFT_SIZE) {
                    fftBuffer[i] = (analysisBuffer[i] * hannWindow[i]).toFloat()
                }

                fft.realForward(fftBuffer)

                val magnitudesDB = FloatArray(FFT_SIZE / 2) {
                    val re = fftBuffer[2 * it]
                    val im = fftBuffer[2 * it + 1]
                    val magnitude = sqrt(re * re + im * im)
                    
                    // 2. Normalización de Salida (para escala dBFS)
                    val normalizedMagnitude = magnitude / (FFT_SIZE / 2.0f)

                    val db = if (normalizedMagnitude > 0) 20 * log10(normalizedMagnitude) else -Float.MAX_VALUE
                    db
                }

                runOnUiThread {
                    spectrogramView.updateSpectrogram(arrayOf(magnitudesDB))
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) {
            stopRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopRecording()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "El permiso para grabar audio es necesario.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
