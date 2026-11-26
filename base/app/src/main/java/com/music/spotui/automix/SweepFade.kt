/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

/**
 * The DJ sweep transition ("Barrido DJ"): during the crossfade overlap the song going out has a
 * low-pass filter closing on it (highs first, then mids, then lows) and the song coming in a
 * high-pass filter opening (highs and mids first, lows last). The levels are the crossfade's own
 * (see CrossfadeRamp); this only decides the three EQ bands of each player, in dB, at [x] of the fade.
 */
object SweepFade {
    /** The three bands of a deck, in dB (0 = untouched, −40 = out). */
    data class Bands(val low: Double, val mid: Double, val high: Double)

    private const val DEPTH_DB = 40.0

    /** 0 before [a], 1 after [b], a smooth step between. */
    private fun ramp(x: Double, a: Double, b: Double): Double {
        val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0)
        return t * t * (3 - 2 * t)
    }

    /** The song going out, [x] in 0..1 of the fade: the filter closes from the top. */
    fun outgoing(x: Double): Bands = Bands(
        low = -DEPTH_DB * ramp(x, 0.60, 1.00),
        mid = -DEPTH_DB * ramp(x, 0.25, 0.85),
        high = -DEPTH_DB * ramp(x, 0.00, 0.55),
    )

    /** The song coming in, [x] in 0..1 of the fade: the filter opens from the bottom up (lows last). */
    fun incoming(x: Double): Bands = Bands(
        low = -DEPTH_DB * (1 - ramp(x, 0.40, 0.80)),
        mid = -DEPTH_DB * (1 - ramp(x, 0.00, 0.40)),
        high = 0.0,
    )

    /** Puts [eq] (a player's) on the going-out song's bands at [x]. */
    fun applyOutgoing(eq: DeckEq?, x: Double) { eq?.let { val b = outgoing(x); it.setBands(b.low, b.mid, b.high) } }

    /** Puts [eq] (a player's) on the coming-in song's bands at [x]. */
    fun applyIncoming(eq: DeckEq?, x: Double) { eq?.let { val b = incoming(x); it.setBands(b.low, b.mid, b.high) } }
}
