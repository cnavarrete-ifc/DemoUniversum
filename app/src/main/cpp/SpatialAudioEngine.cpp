#include "SpatialAudioEngine.h"
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,"SpatialAudio",__VA_ARGS__)

SpatialAudioEngine::SpatialAudioEngine() {
    leftDelayBuffer.assign(MAX_DELAY_SAMPLES, 0);
    rightDelayBuffer.assign(MAX_DELAY_SAMPLES, 0);
}

SpatialAudioEngine::~SpatialAudioEngine() { stop(); }

void SpatialAudioEngine::setAudioData(const std::vector<int16_t> &data) {
    pcm = data;
    playhead = 0;
    // Reiniciar estado del filtro al cargar nuevo audio
    lx1 = lx2 = ly1 = ly2 = 0;
    rx1 = rx2 = ry1 = ry2 = 0;
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
    distance = fmax(1.0f, meters);
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

        HRTFModel hrtf = computeHRTF(az, el, sampleRate);

        // --- PROCESADO DE RETARDO (ITD) ---
        leftDelayBuffer[leftDelayIndex] = monoSample;
        rightDelayBuffer[rightDelayIndex] = monoSample;
        int leftReadIndex = (leftDelayIndex - (int)hrtf.leftDelay + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
        int rightReadIndex = (rightDelayIndex - (int)hrtf.rightDelay + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
        float leftDelayed = leftDelayBuffer[leftReadIndex];
        float rightDelayed = rightDelayBuffer[rightReadIndex];
        leftDelayIndex = (leftDelayIndex + 1) % MAX_DELAY_SAMPLES;
        rightDelayIndex = (rightDelayIndex + 1) % MAX_DELAY_SAMPLES;

        // --- PROCESADO DE FILTRO (ELEVACIÓN) ---
        BiquadCoeffs c = hrtf.filterCoeffs;
        float leftFiltered = c.b0 * leftDelayed + c.b1 * lx1 + c.b2 * lx2 - c.a1 * ly1 - c.a2 * ly2;
        lx2 = lx1; lx1 = leftDelayed;
        ly2 = ly1; ly1 = leftFiltered;

        float rightFiltered = c.b0 * rightDelayed + c.b1 * rx1 + c.b2 * rx2 - c.a1 * ry1 - c.a2 * ry2;
        rx2 = rx1; rx1 = rightDelayed;
        ry2 = ry1; ry1 = rightFiltered;

        // --- APLICACIÓN DE GANANCIA (ILD Y DISTANCIA) ---
        float leftOutput = leftFiltered * hrtf.leftGain / dist;
        float rightOutput = rightFiltered * hrtf.rightGain / dist;
        
        out[i * 2] = (int16_t) fmax(-32768.0f, fmin(32767.0f, leftOutput));
        out[i * 2 + 1] = (int16_t) fmax(-32768.0f, fmin(32767.0f, rightOutput));
    }

    return oboe::DataCallbackResult::Continue;
}