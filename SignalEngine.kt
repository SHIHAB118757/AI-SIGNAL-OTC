package com.aisignal.otc.engine

import com.aisignal.otc.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SignalEngine {

    fun generateSignal(
        expiry: ExpiryTime,
        marketPair: String,
        candles: List<DetectedCandle>,
        indicators: TechnicalIndicators,
        structure: MarketStructure,
        pattern: CandlePatternType,
        gemini: GeminiAnalysisResponse?,
        captureTime: Long
    ): SignalResult {
        val analysisTime = System.currentTimeMillis()

        if (candles.size < 5) {
            return SignalResult(
                signal = SignalDirection.NO_SIGNAL,
                confidence = 0,
                expiry = expiry,
                marketPair = marketPair,
                captureTime = captureTime,
                analysisTime = analysisTime,
                detectedCandlesCount = candles.size,
                latestCandle = candles.lastOrNull(),
                detectedPattern = CandlePatternType.NONE,
                indicatorsSummary = "Insufficient Candles",
                rsiValue = 50.0,
                emaSummary = "N/A",
                structureSummary = "N/A",
                geminiReason = null,
                upScore = 0,
                downScore = 0,
                rejectionReason = "Minimum 5 visible candles required for statistical validity."
            )
        }

        val isUltraShort = expiry.seconds <= 15
        val isShort = expiry.seconds in 20..30

        val trendWeight = if (isUltraShort) 10 else if (isShort) 18 else 25
        val momentumWeight = if (isUltraShort) 25 else if (isShort) 18 else 12
        val emaWeight = if (isUltraShort) 10 else if (isShort) 15 else 18
        val patternWeight = if (isUltraShort) 20 else if (isShort) 14 else 10
        val structureWeight = if (isUltraShort) 5 else if (isShort) 10 else 15

        var upScore = 0
        var downScore = 0

        if (indicators.priceChangePercent > 0.05) upScore += trendWeight
        else if (indicators.priceChangePercent < -0.05) downScore += trendWeight
        else {
            upScore += (trendWeight * 0.3).toInt()
            downScore += (trendWeight * 0.3).toInt()
        }

        val latest = candles.last()
        val prev = candles[candles.size - 2]
        if (latest.direction == CandleDirection.GREEN && prev.direction == CandleDirection.GREEN) {
            upScore += momentumWeight
        } else if (latest.direction == CandleDirection.RED && prev.direction == CandleDirection.RED) {
            downScore += momentumWeight
        } else if (latest.direction == CandleDirection.GREEN) {
            upScore += (momentumWeight * 0.65).toInt()
        } else {
            downScore += (momentumWeight * 0.65).toInt()
        }

        val ema9 = indicators.ema9.lastOrNull() ?: 0.0
        val ema21 = indicators.ema21.lastOrNull() ?: 0.0
        val ema50 = indicators.ema50.lastOrNull() ?: 0.0
        var emaSummary = "Neutral"

        if (ema9 > ema21 && ema21 >= ema50) {
            upScore += emaWeight
            emaSummary = "Bullish Alignment (9 > 21 > 50)"
        } else if (ema9 < ema21 && ema21 <= ema50) {
            downScore += emaWeight
            emaSummary = "Bearish Alignment (9 < 21 < 50)"
        } else if (ema9 > ema21) {
            upScore += (emaWeight * 0.6).toInt()
            emaSummary = "Mild Bullish"
        } else {
            downScore += (emaWeight * 0.6).toInt()
            emaSummary = "Mild Bearish"
        }

        val rsiVal = indicators.rsi14.lastOrNull() ?: 50.0
        if (rsiVal < 32.0) upScore += 10
        else if (rsiVal > 68.0) downScore += 10
        else if (rsiVal >= 52.0) upScore += 6
        else if (rsiVal <= 48.0) downScore += 6

        val hist = indicators.macdHistogram.lastOrNull() ?: 0.0
        if (hist > 0) upScore += 10 else downScore += 10

        when (pattern) {
            CandlePatternType.HAMMER, CandlePatternType.BULLISH_ENGULFING, CandlePatternType.MORNING_STAR ->
                upScore += patternWeight
            CandlePatternType.STRONG_BULLISH -> upScore += (patternWeight * 0.85).toInt()
            CandlePatternType.SHOOTING_STAR, CandlePatternType.BEARISH_ENGULFING, CandlePatternType.EVENING_STAR ->
                downScore += patternWeight
            CandlePatternType.STRONG_BEARISH -> downScore += (patternWeight * 0.85).toInt()
            else -> {}
        }

        when (structure.type) {
            MarketStructureType.HIGHER_HIGH -> upScore += structureWeight
            MarketStructureType.LOWER_LOW -> downScore += structureWeight
            else -> {}
        }

        if (structure.distanceToSupport < structure.distanceToResistance * 0.3) {
            upScore += 10
        } else if (structure.distanceToResistance < structure.distanceToSupport * 0.3) {
            downScore += 10
        }

        if (gemini != null) {
            val gemConf = ((gemini.confidence ?: 0.0) / 100.0).coerceIn(0.0, 1.0)
            if (gemini.directionBias == "UP") {
                upScore += (18.0 * gemConf).toInt()
            } else if (gemini.directionBias == "DOWN") {
                downScore += (18.0 * gemConf).toInt()
            }
        }

        val totalPossible = 118.0
        val normUp = ((upScore / totalPossible) * 100.0).toInt()
        val normDown = ((downScore / totalPossible) * 100.0).toInt()

        val diff = abs(normUp - normDown)
        val highest = max(normUp, normDown)

        if (diff < 14) {
            return createResult(
                SignalDirection.NO_SIGNAL,
                highest,
                expiry,
                marketPair,
                captureTime,
                analysisTime,
                candles,
                pattern,
                emaSummary,
                rsiVal,
                structure.type.name,
                gemini?.reason,
                normUp,
                normDown,
                "Market evidence is conflicting: UP ($normUp%) vs DOWN ($normDown%) are balanced."
            )
        }

        if (highest < 55) {
            return createResult(
                SignalDirection.NO_SIGNAL,
                highest,
                expiry,
                marketPair,
                captureTime,
                analysisTime,
                candles,
                pattern,
                emaSummary,
                rsiVal,
                structure.type.name,
                gemini?.reason,
                normUp,
                normDown,
                "Insufficient directional momentum (probability under 55%)."
            )
        }

        val finalSignal = if (normUp > normDown) SignalDirection.UP else SignalDirection.DOWN
        val confidence = min(88, max(62, highest))

        return createResult(
            finalSignal,
            confidence,
            expiry,
            marketPair,
            captureTime,
            analysisTime,
            candles,
            pattern,
            emaSummary,
            rsiVal,
            structure.type.name,
            gemini?.reason,
            normUp,
            normDown,
            null
        )
    }

    private fun createResult(
        signal: SignalDirection,
        confidence: Int,
        expiry: ExpiryTime,
        marketPair: String,
        captureTime: Long,
        analysisTime: Long,
        candles: List<DetectedCandle>,
        pattern: CandlePatternType,
        emaSummary: String,
        rsiVal: Double,
        structure: String,
        geminiReason: String?,
        upScore: Int,
        downScore: Int,
        rejectionReason: String?
    ): SignalResult {
        return SignalResult(
            signal = signal,
            confidence = confidence,
            expiry = expiry,
            marketPair = marketPair,
            captureTime = captureTime,
            analysisTime = analysisTime,
            detectedCandlesCount = candles.size,
            latestCandle = candles.lastOrNull(),
            detectedPattern = pattern,
            indicatorsSummary = "RSI: \${(rsiVal * 10).toInt() / 10.0} | EMA: \$emaSummary",
            rsiValue = rsiVal,
            emaSummary = emaSummary,
            structureSummary = structure,
            geminiReason = geminiReason,
            upScore = upScore,
            downScore = downScore,
            rejectionReason = rejectionReason
        )
    }
}
