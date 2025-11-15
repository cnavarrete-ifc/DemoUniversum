#include "SpatialAudioEngine.h"
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"SpatialAudio",__VA_ARGS__)

SpatialAudioEngine::SpatialAudioEngine() {
    // Inicializar los búferes de retardo con ceros
    leftDelayBuffer.assign(MAX_DELAY_SAMPLES, 0);
    rightDelayBuffer.assign(MAX_DELAY_SAMPLES, 0);
}

SpatialAudioEngine::~SpatialAudioEngine() { stop(); }

void SpatialAudioEngine::setAudioData(const std::vector<int16_t> &data) {
    pcm = data;
    playhead = 0;
}

void SpatialAudioEngine::setAzimuth(float deg) {
    std::lock_guard<std::mutex> lock(paramMutex);
    azimuth = deg;
}

void SpatialAudioEngine::setElevation(float deg) {
    std::lock_guard<std::mutex> lock(paramMutex);
    elevation = deg;
}

void SpatialAudioEngine::setDistance(float meters) {
    std::lock_guard<std::mutex> lock(paramMutex);
    distance = fmax(1.0f, meters); // Evitar distancia cero o negativa
}

void SpatialAudioEngine::start() {
    stop();

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setFormat(oboe::AudioFormat::I16)
            ->setChannelCount(2)
            ->setSampleRate(44100)
            ->setCallback(this);

    oboe::Result result = builder.openStream(&stream);
    if (result != oboe::Result::OK) {
        LOGI("Error opening stream");
        return;
    }
    stream->requestStart();
}

void SpatialAudioEngine::stop() {
    if (stream) {
        stream->stop();
        stream->close();
        stream = nullptr;
    }
}

oboe::DataCallbackResult SpatialAudioEngine::onAudioReady(
        oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) {

    int16_t *out = (int16_t *) audioData;
    int sampleRate = oboeStream->getSampleRate();

    for (int i = 0; i < numFrames; i++) {

        if (pcm.empty()) {
            out[i * 2] = 0;
            out[i * 2 + 1] = 0;
            continue;
        }

        int16_t monoSample = pcm[playhead];
        playhead = (playhead + 1) % pcm.size();

        float az, el, dist;
        {
            std::lock_guard<std::mutex> lock(paramMutex);
            az = azimuth;
            el = elevation;
            dist = distance;
        }

        // Calcular el modelo HRTF para la posición actual
        HRTFModel hrtf = computeHRTF(az, el, sampleRate);

        // Escribir la muestra actual en los búferes de retardo
        leftDelayBuffer[leftDelayIndex] = monoSample;
        rightDelayBuffer[rightDelayIndex] = monoSample;

        // Calcular los índices de lectura para el retardo (ITD)
        int leftReadIndex = (leftDelayIndex - (int)hrtf.leftDelay + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
        int rightReadIndex = (rightDelayIndex - (int)hrtf.rightDelay + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;

        // Leer las muestras retardadas
        int16_t leftSample = leftDelayBuffer[leftReadIndex];
        int16_t rightSample = rightDelayBuffer[rightReadIndex];

        // Aplicar ganancia (ILD) y atenuación por distancia
        float leftOutput = leftSample * hrtf.leftGain / dist;
        float rightOutput = rightSample * hrtf.rightGain / dist;

        // Avanzar los índices de escritura
        leftDelayIndex = (leftDelayIndex + 1) % MAX_DELAY_SAMPLES;
        rightDelayIndex = (rightDelayIndex + 1) % MAX_DELAY_SAMPLES;
        
        // Escribir en el búfer de salida, asegurando que no haya clipping
        out[i * 2] = (int16_t) fmax(-32768.0f, fmin(32767.0f, leftOutput));
        out[i * 2 + 1] = (int16_t) fmax(-32768.0f, fmin(32767.0f, rightOutput));
    }

    return oboe::DataCallbackResult::Continue;
}