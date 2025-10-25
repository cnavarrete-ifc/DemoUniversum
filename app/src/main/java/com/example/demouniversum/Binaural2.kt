package com.example.demouniversum

import android.media.*
import android.os.Bundle
import android.graphics.Path
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import kotlin.concurrent.thread

class Binaural2 : AppCompatActivity() {

    // --- Variables de UI ---
    private var isPlaying = false
    private lateinit var playPauseButton: ImageButton
    private lateinit var balanceSlider: Slider
    private lateinit var delaySlider: Slider
    private lateinit var visualizerView: VisualizerView2

    // --- Variables de Audio ---
    private lateinit var audioTrack: AudioTrack
    private var audioThread: Thread? = null
    private val sampleRate = 44100
    private var audioData: ShortArray? = null
    @Volatile private var currentPlaybackFrame = 0

    @Volatile private var amplitudeRight = 0.5f
    @Volatile private var amplitudeLeft = 0.5f
    @Volatile private var delayMs = 0.0f

    // --- Variables de Visualización ---
    private val leftWavePath = Path()
    private val rightWavePath = Path()
    private var lastUpdateTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_binaural2)
        setupViews()
        setupListeners()
        loadAudioFile()
    }

    private fun setupViews() {
        playPauseButton = findViewById(R.id.playPauseButton)
        balanceSlider = findViewById(R.id.ampSlider)
        delaySlider = findViewById(R.id.difSlider)
        visualizerView = findViewById(R.id.visualizerView)
        findViewById<ImageButton>(R.id.button_home).setOnClickListener { finish() }

        playPauseButton.isEnabled = false
    }

    private fun setupListeners() {
        playPauseButton.setOnClickListener {
            if (audioData == null) {
                Toast.makeText(this, "El audio aún no ha cargado.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isPlaying = !isPlaying
            if (isPlaying) {
                startAudio()
                playPauseButton.setImageResource(R.drawable.ic_pause)
            } else {
                stopAudio()
                playPauseButton.setImageResource(R.drawable.ic_play)
            }
        }

        balanceSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                amplitudeRight = value
                amplitudeLeft = 1.0f - value
                updateWaveform(currentPlaybackFrame)
            }
        }

        delaySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                delayMs = value
                updateWaveform(currentPlaybackFrame)
            }
        }
    }

    private fun loadAudioFile() {
        thread(start = true) {
            try {
                val pcmData = mutableListOf<Short>()
                val extractor = MediaExtractor()
                resources.openRawResourceFd(R.raw.prueba).use { afd ->
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }

                val trackIndex = (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: run {
                    runOnUiThread { Toast.makeText(this, "No se encontró pista de audio.", Toast.LENGTH_SHORT).show() }
                    return@thread
                }

                extractor.selectTrack(trackIndex)
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                val fileSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                if (fileSampleRate != sampleRate) {
                    runOnUiThread { Toast.makeText(this, "Advertencia: El SR del archivo ($fileSampleRate Hz) no coincide con el del reproductor ($sampleRate Hz).", Toast.LENGTH_LONG).show() }
                }

                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sawInputEOS = false
                var sawOutputEOS = false

                while (!sawOutputEOS) {
                    if (!sawInputEOS) {
                        val inputBufferIndex = codec.dequeueInputBuffer(10000)
                        if (inputBufferIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                sawInputEOS = true
                            } else {
                                codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                    if (outputBufferIndex >= 0) {
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEOS = true

                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                            val shortBuffer = outputBuffer.asShortBuffer()
                            val tempArray = ShortArray(shortBuffer.remaining())
                            shortBuffer.get(tempArray)

                            if (channelCount == 1) {
                                tempArray.forEach { sample ->
                                    pcmData.add(sample) // L
                                    pcmData.add(sample) // R (duplicado para estéreo)
                                }
                            } else {
                                pcmData.addAll(tempArray.toList())
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
                audioData = pcmData.toShortArray()
                codec.stop(); codec.release(); extractor.release()

                runOnUiThread {
                    Toast.makeText(this, "Audio cargado.", Toast.LENGTH_SHORT).show()
                    playPauseButton.isEnabled = true
                    updateWaveform(0) // Dibuja la forma de onda inicial
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error al cargar audio: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startAudio() {
        stopAudio()
        val localAudioData = audioData ?: return

        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(bufferSize).build()

        audioThread = thread(start = true) {
            val buffer = ShortArray(bufferSize)
            val totalFrames = localAudioData.size / 2

            try {
                audioTrack.play()
                while (!Thread.currentThread().isInterrupted && totalFrames > 0) {
                    val localAmpLeft = amplitudeLeft
                    val localAmpRight = amplitudeRight
                    val delayInFrames = (delayMs / 1000f * sampleRate).toInt()

                    for (i in 0 until buffer.size step 2) {
                        currentPlaybackFrame = (currentPlaybackFrame + 1) % totalFrames

                        val leftFrameIndex = currentPlaybackFrame
                        val leftSample = localAudioData[leftFrameIndex * 2]
                        buffer[i] = (leftSample * localAmpLeft).toInt().toShort()

                        var rightFrameIndex = (currentPlaybackFrame - delayInFrames)
                        if (rightFrameIndex < 0) rightFrameIndex += totalFrames
                        rightFrameIndex %= totalFrames

                        val rightSample = localAudioData[rightFrameIndex * 2 + 1]
                        buffer[i + 1] = (rightSample * localAmpRight).toInt().toShort()
                    }
                    audioTrack.write(buffer, 0, buffer.size)

                    val now = System.currentTimeMillis()
                    if (now - lastUpdateTime > 30) { // Actualiza la UI ~30 veces por segundo
                        runOnUiThread { updateWaveform(currentPlaybackFrame) }
                        lastUpdateTime = now
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun updateWaveform(startFrame: Int) {
        val localAudioData = audioData
        if (localAudioData == null || visualizerView.width == 0) return

        leftWavePath.reset()
        rightWavePath.reset()

        val viewWidth = visualizerView.width.toFloat()
        val viewHeight = visualizerView.height.toFloat()
        val centerY = viewHeight / 2

        val timeWindowMs = 50f
        val samplesToDraw = (sampleRate * (timeWindowMs / 1000f)).toInt() * 2 // *2 for stereo

        leftWavePath.moveTo(0f, centerY)
        rightWavePath.moveTo(0f, centerY)

        val totalFrames = localAudioData.size / 2
        val delayInFrames = (delayMs / 1000f * sampleRate).toInt()

        for (i in 0 until samplesToDraw step 2) {
            val x = (i / 2f) / (samplesToDraw / 2f) * viewWidth

            // --- Onda Izquierda ---
            val leftFrameIndex = (startFrame + i / 2) % totalFrames
            val leftSample = localAudioData[leftFrameIndex * 2]
            val yLeft = centerY - (leftSample / 32767f * (viewHeight / 1.5f) * amplitudeLeft)
            leftWavePath.lineTo(x, yLeft)

            // --- Onda Derecha ---
            var rightFrameIndex = (startFrame + i / 2 - delayInFrames)
            if (rightFrameIndex < 0) rightFrameIndex += totalFrames
            rightFrameIndex %= totalFrames

            val rightSample = localAudioData[rightFrameIndex * 2 + 1]
            val yRight = centerY - (rightSample / 32767f * (viewHeight / 1.5f) * amplitudeRight)
            rightWavePath.lineTo(x, yRight)
        }
        visualizerView.setWavePaths(leftWavePath, rightWavePath)
    }

    private fun stopAudio() {
        audioThread?.interrupt()
        audioThread = null
        if(::audioTrack.isInitialized && audioTrack.playState != AudioTrack.PLAYSTATE_STOPPED) {
             audioTrack.stop()
             audioTrack.release()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isPlaying) {
            stopAudio()
            isPlaying = false
            if(::playPauseButton.isInitialized) playPauseButton.setImageResource(R.drawable.ic_play)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }
}
