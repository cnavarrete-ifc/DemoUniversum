#pragma once

#include <oboe/Oboe.h>
#include <vector>
#include <mutex>
#include "HRTF.h"

#define MAX_DELAY_SAMPLES 128 // Suficiente para un ITD realista

class SpatialAudioEngine : public oboe::AudioStreamCallback {

public:
    SpatialAudioEngine();
    ~SpatialAudioEngine();

    void start();
    void stop();

    void setAudioData(const std::vector<int16_t> &data);
    void setAzimuth(float deg);
    void setElevation(float deg);
    void setDistance(float meters);

    oboe::DataCallbackResult
    onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override;

private:
    oboe::AudioStream *stream = nullptr;
    std::vector<int16_t> pcm;
    int playhead = 0;

    float azimuth = 0;
    float elevation = 0;
    float distance = 1.0f;

    // Búferes para retardo interaural (ITD)
    std::vector<int16_t> leftDelayBuffer;
    std::vector<int16_t> rightDelayBuffer;
    int leftDelayIndex = 0;
    int rightDelayIndex = 0;

    // Variables de estado para el filtro Biquad (para simulación de elevación)
    float lx1 = 0, lx2 = 0, ly1 = 0, ly2 = 0; // Estado del filtro izquierdo
    float rx1 = 0, rx2 = 0, ry1 = 0, ry2 = 0; // Estado del filtro derecho

    std::mutex paramMutex;
};