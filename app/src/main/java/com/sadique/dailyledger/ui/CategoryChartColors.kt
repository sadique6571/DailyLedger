package com.sadique.dailyledger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/** Stable for a category set regardless of spending order. No modulo palette reuse. */
object CategoryChartColors {
    private data class Swatch(val argb: Int, val lab: DoubleArray)
    private fun lab(argb: Int): DoubleArray {
        fun linear(shift: Int): Double {
            val c = (argb ushr shift and 255) / 255.0
            return if (c <= .04045) c / 12.92 else ((c + .055) / 1.055).pow(2.4)
        }
        val r = linear(16); val g = linear(8); val b = linear(0)
        val l = Math.cbrt(.4122214708 * r + .5363325363 * g + .0514459929 * b)
        val m = Math.cbrt(.2119034982 * r + .6806995451 * g + .1073969566 * b)
        val s = Math.cbrt(.0883024619 * r + .2817188376 * g + .6299787005 * b)
        return doubleArrayOf(.2104542553 * l + .7936177850 * m - .0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + .4505937099 * s,
            .0259040371 * l + .7827717662 * m - .8086757660 * s)
    }
    private val candidates by lazy {
        (0 until 36).flatMap { hue ->
            listOf(.60f, .80f).flatMap { saturation ->
                listOf(.40f, .57f, .70f).map { lightness -> Color.hsl(hue * 10f, saturation, lightness).toArgb() }
            }
        }.distinct().map { Swatch(it, lab(it)) }
    }
    fun colors(labels: Collection<String>): Map<String, Color> {
        val keys = labels.distinct().sorted()
        val used = hashSetOf<Int>()
        val distance = DoubleArray(candidates.size) { Double.POSITIVE_INFINITY }
        val assigned = linkedMapOf<String, Color>()
        keys.forEachIndexed { index, key ->
            val hash = key.fold(0x811c9dc5L) { h, ch -> ((h xor ch.code.toLong()) * 0x01000193L) and 0xffffffffL }
            val argb: Int
            // Maximise the minimum perceptual distance for normal-size charts, rather
            // than accepting several nearly identical hashed greens or purples.
            if (index < 64) {
                val chosen = if (index == 0) (hash % candidates.size).toInt()
                    else candidates.indices.filter { candidates[it].argb !in used }.maxBy { distance[it] }
                argb = candidates[chosen].argb
                used.add(argb)
                val selected = candidates[chosen].lab
                candidates.forEachIndexed { i, candidate ->
                    val delta = candidate.lab.indices.sumOf { k -> (candidate.lab[k] - selected[k]).pow(2) }
                    distance[i] = minOf(distance[i], delta)
                }
            } else {
                var attempt = 0
                var value: Int
                do {
                    val hue = ((hash % 36000L) / 100f + attempt * 137.50776f) % 360f
                    val saturation = .62f + ((hash / 36000L + attempt) % 19L) / 100f
                    val lightness = .43f + ((hash / 684000L + attempt) % 16L) / 100f
                    value = Color.hsl(hue, saturation, lightness).toArgb()
                    attempt++
                } while (!used.add(value))
                argb = value
            }
            assigned[key] = Color(argb)
        }
        return assigned
    }
}
