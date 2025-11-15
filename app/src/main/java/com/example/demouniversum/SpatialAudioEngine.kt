package com.example.demouniversum

class SpatialAudioEngine {

    init {
        System.loadLibrary("spatial_audio")
    }

    external fun start()
    external fun stop()

    external fun setAudioData(pcm: ShortArray)
    external fun setAzimuth(deg: Float)
    external fun setElevation(deg: Float)
    external fun setDistance(meters: Float)
}