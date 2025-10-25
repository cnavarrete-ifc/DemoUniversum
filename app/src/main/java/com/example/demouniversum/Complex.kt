package com.example.demouniversum

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Complex(val re: Double, val im: Double) {
    operator fun plus(other: Complex) = Complex(re + other.re, im + other.im)
    operator fun minus(other: Complex) = Complex(re - other.re, im - other.im)
    operator fun times(other: Complex) = Complex(re * other.re - im * other.im, re * other.im + im * other.re)
    fun abs() = sqrt(re * re + im * im)
    fun conjugate() = Complex(re, -im)
}

fun exp(i: Double): Complex {
    return Complex(cos(i), sin(i))
}