#pragma once
#include <cmath>

// Modelo HRTF basado en "Acoustic HRTF for a rigid sphere"

struct HRTFModel {
    float leftGain;
    float rightGain;
    float leftDelay;  // Retardo en muestras
    float rightDelay; // Retardo en muestras
};

inline HRTFModel computeHRTF(float azimuthDeg, float elevationDeg, float sampleRate) {
    constexpr float HEAD_RADIUS = 0.0875f; // Radio de la cabeza en metros
    constexpr float SOUND_SPEED = 343.0f; // Velocidad del sonido en m/s
    HRTFModel model;

    // Convertir ángulos a radianes.
    float az = azimuthDeg * M_PI / 180.0f;
    float el = elevationDeg * M_PI / 180.0f;

    // Coordenadas cartesianas de la fuente de sonido
    float x = cos(az) * cos(el);
    float y = sin(az) * cos(el);

    // Calcular el ángulo theta real con respecto al eje interaural
    float theta = atan2(y, x);

    // Diferencia de Tiempo Interaural (ITD) - Modelo de Woodworth
    float pathDiff = HEAD_RADIUS * (theta + sin(theta));
    float itd = fabsf(pathDiff) / SOUND_SPEED;

    if (azimuthDeg > 0) { // Sonido a la derecha
        model.leftDelay = itd * sampleRate;
        model.rightDelay = 0;
    } else { // Sonido a la izquierda
        model.leftDelay = 0;
        model.rightDelay = itd * sampleRate;
    }

    // Diferencia de Nivel Interaural (ILD) - Suavizado para evitar silencio total
    float baseGain = 0.2f;
    float angleRatio = (sin(theta) + 1.0f) / 2.0f; // [0, 1] -> Izquierda a Derecha
    model.rightGain = baseGain + (1.0f - baseGain) * angleRatio;
    model.leftGain = baseGain + (1.0f - baseGain) * (1.0f - angleRatio);
    
    // Simular atenuación por elevación (efecto del torso/hombros).
    // El factor se reduce a 0.5 para una atenuación menos drástica en los polos.
    float elevationFactor = 1.0f - (fabsf(sinf(el)) * 0.5f);
    model.leftGain *= elevationFactor;
    model.rightGain *= elevationFactor;

    return model;
}
