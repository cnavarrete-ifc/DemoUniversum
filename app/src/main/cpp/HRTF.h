#pragma once
#include <cmath>

// Estructura para los coeficientes de un filtro Biquad
struct BiquadCoeffs {
    float b0, b1, b2, a1, a2;
};

// Modelo HRTF que ahora incluye coeficientes para un filtro espectral
struct HRTFModel {
    float leftGain;
    float rightGain;
    float leftDelay;  // Retardo en muestras
    float rightDelay; // Retardo en muestras
    BiquadCoeffs filterCoeffs;
};

inline HRTFModel computeHRTF(float azimuthDeg, float elevationDeg, float sampleRate) {
    constexpr float HEAD_RADIUS = 0.0875f; // Radio de la cabeza en metros
    constexpr float SOUND_SPEED = 343.0f; // Velocidad del sonido en m/s
    HRTFModel model;

    // --- CÁLCULOS DE POSICIONAMIENTO HORIZONTAL (ILD & ITD) ---
    float az = azimuthDeg * M_PI / 180.0f;
    float el = elevationDeg * M_PI / 180.0f;
    float x = cos(az) * cos(el);
    float y = sin(az) * cos(el);
    float theta = atan2(y, x);

    float pathDiff = HEAD_RADIUS * (theta + sin(theta));
    float itd = fabsf(pathDiff) / SOUND_SPEED;

    if (azimuthDeg > 0) { // Derecha
        model.leftDelay = itd * sampleRate;
        model.rightDelay = 0;
    } else { // Izquierda
        model.leftDelay = 0;
        model.rightDelay = itd * sampleRate;
    }

    float baseGain = 0.2f;
    float angleRatio = (sin(theta) + 1.0f) / 2.0f;
    model.rightGain = baseGain + (1.0f - baseGain) * angleRatio;
    model.leftGain = baseGain + (1.0f - baseGain) * (1.0f - angleRatio);

    // --- SIMULACIÓN DE ELEVACIÓN CON FILTRO DE PICO (PEAKING FILTER) ---
    // Para que el efecto sea inconfundible, se exagera el realce y se amplía el rango.
    
    // Mapear la elevación a una frecuencia de pico en un rango más bajo y rico en armónicos (3-8kHz)
    float elevation_rad_norm = (sinf(el) + 1.0f) / 2.0f; // Normalizado de 0 a 1
    float peakFreq = 3000.0f + 5000.0f * elevation_rad_norm;

    // Calcular coeficientes del filtro Biquad para un Peaking EQ
    float gainDB = 12.0f; // Aumento drástico de +12dB
    float A = powf(10.0f, gainDB / 40.0f);
    float w0 = 2.0f * M_PI * peakFreq / sampleRate;
    float Q = 1.0f; // Q más baja para una curva de realce más ancha
    float alpha = sinf(w0) / (2.0f * Q);

    float a0_inv = 1.0f / (1.0f + alpha / A);
    model.filterCoeffs.b0 = (1.0f + alpha * A) * a0_inv;
    model.filterCoeffs.b1 = -2.0f * cosf(w0) * a0_inv;
    model.filterCoeffs.b2 = (1.0f - alpha * A) * a0_inv;
    model.filterCoeffs.a1 = model.filterCoeffs.b1;
    model.filterCoeffs.a2 = (1.0f - alpha / A) * a0_inv;

    return model;
}
