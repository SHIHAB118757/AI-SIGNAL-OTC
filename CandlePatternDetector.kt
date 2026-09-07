package com.aisignal.otc.engine

import com.aisignal.otc.model.CandleDirection
import com.aisignal.otc.model.CandlePatternType
import com.aisignal.otc.model.DetectedCandle
import kotlin.math.abs
import kotlin.math.max

object CandlePatternDetector {

    fun detectPattern(candles: List<DetectedCandle>): CandlePatternType {
        if (candles.size < 3) return CandlePatternType.NONE

        val n = candles.size
        val c1 = candles[n - 1]
        val c2 = candles[n - 2]
        val c3 = candles[n - 3]

        val range1 = max(c1.high - c1.low, 0.00001)
        val body1 = abs(c1.close - c1.open)
        val upperWick1 = c1.high - max(c1.open, c1.close)
        val lowerWick1 = minOf(c1.open, c1.close) - c1.low
        val isBullish1 = c1.direction == CandleDirection.GREEN

        val range2 = max(c2.high - c2.low, 0.00001)
        val body2 = abs(c2.close - c2.open)
        val isBullish2 = c2.direction == CandleDirection.GREEN

        if (body1 / range1 < 0.10) {
            return CandlePatternType.DOJI
        }

        if (lowerWick1 >= 2.0 * body1 && upperWick1 <= 0.25 * body1 && isBullish1) {
            return CandlePatternType.HAMMER
        }

        if (upperWick1 >= 2.0 * body1 && lowerWick1 <= 0.25 * body1 && !isBullish1) {
            return CandlePatternType.SHOOTING_STAR
        }

        if (!isBullish2 && isBullish1 && c1.open <= c2.close && c1.close >= c2.open && body1 > body2) {
            return CandlePatternType.BULLISH_ENGULFING
        }

        if (isBullish2 && !isBullish1 && c1.open >= c2.close && c1.close <= c2.open && body1 > body2) {
            return CandlePatternType.BEARISH_ENGULFING
        }

        val isBullish3 = c3.direction == CandleDirection.GREEN
        val body3 = abs(c3.close - c3.open)
        if (!isBullish3 && body2 < 0.4 * body3 && isBullish1 && c1.close > c3.close + 0.5 * body3) {
            return CandlePatternType.MORNING_STAR
        }

        if (isBullish3 && body2 < 0.4 * body3 && !isBullish1 && c1.close < c3.close - 0.5 * body3) {
            return CandlePatternType.EVENING_STAR
        }

        if (body1 / range1 > 0.82) {
            return if (isBullish1) CandlePatternType.STRONG_BULLISH else CandlePatternType.STRONG_BEARISH
        }

        return CandlePatternType.NONE
    }
}
