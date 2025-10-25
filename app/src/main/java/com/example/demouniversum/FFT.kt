package com.example.demouniversum

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class FFT(private val n: Int) {

    private val logN = 31 - Integer.numberOfLeadingZeros(n)

    init {
        if (n <= 0 || (n and (n - 1)) != 0) {
            throw IllegalArgumentException("n must be a power of 2")
        }
    }

    // Pre-calculated bit-reversal indices
    private val bitReversalTable = IntArray(n) { i ->
        var reversed = 0
        var current = i
        for (j in 0 until logN) {
            reversed = (reversed shl 1) or (current and 1)
            current = current shr 1
        }
        reversed
    }

    // Pre-calculated twiddle factors
    private val twiddleFactors = Array(n / 2) { k ->
        val angle = -2.0 * PI * k / n
        Complex(cos(angle), sin(angle))
    }

    /**
     * Computes the FFT in-place.
     * @param x The input array of complex numbers, which will be overwritten with the FFT result.
     */
    fun fft(x: Array<Complex>) {
        if (x.size != n) {
            throw IllegalArgumentException("Input array size must be $n")
        }

        // 1. Bit-reversal permutation
        for (i in 0 until n) {
            val j = bitReversalTable[i]
            if (i < j) {
                // Swap
                val temp = x[i]
                x[i] = x[j]
                x[j] = temp
            }
        }

        // 2. Iterative Cooley-Tukey
        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val twiddleStep = n / len
            for (i in 0 until n step len) {
                var k = 0
                for (j in i until i + halfLen) {
                    val twiddle = twiddleFactors[k]
                    val t = twiddle * x[j + halfLen]
                    x[j + halfLen] = x[j] - t
                    x[j] = x[j] + t
                    k += twiddleStep
                }
            }
            len *= 2
        }
    }
}
