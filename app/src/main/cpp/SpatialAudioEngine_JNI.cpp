#include <jni.h>
#include "SpatialAudioEngine.h"

static SpatialAudioEngine engine;

extern "C" {

JNIEXPORT void JNICALL
Java_com_example_demouniversum_SpatialAudioEngine_start(JNIEnv* env, jobject /* this */) {
    engine.start();
}

JNIEXPORT void JNICALL
Java_com_example_demouniversum_SpatialAudioEngine_stop(JNIEnv* env, jobject /* this */) {
    engine.stop();
}

JNIEXPORT void JNICALL
Java_com_example_demouniversum_SpatialAudioEngine_setAudioData(JNIEnv* env, jobject /* this */, jshortArray pcm) {
    jsize length = env->GetArrayLength(pcm);
    std::vector<int16_t> vec(length);
    env->GetShortArrayRegion(pcm, 0, length, vec.data());
    engine.setAudioData(vec);
}

JNIEXPORT void JNICALL
Java_com_example_demouniversum_SpatialAudioEngine_setAzimuth(JNIEnv* env, jobject /* this */, jfloat deg) {
    engine.setAzimuth(deg);
}

JNIEXPORT void JNICALL
Java_com_example_demouniversum_SpatialAudioEngine_setElevation(JNIEnv* env, jobject /* this */, jfloat deg) {
    engine.setElevation(deg);
}

JNIEXPORT void JNICALL
Java_com_example_demouniversum_SpatialAudioEngine_setDistance(JNIEnv* env, jobject /* this */, jfloat meters) {
    engine.setDistance(meters);
}

}