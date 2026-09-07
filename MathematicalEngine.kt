package com.aisignal.otc.engine

import com.aisignal.otc.model.DetectedCandle
import com.aisignal.otc.model.MarketStructure
import com.aisignal.otc.model.MarketStructureType
import com.aisignal.otc.model.TechnicalIndicators
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object MathematicalEngine {

    fun calculateEMA(prices: List<Double>, period: Int): List<Double> {
        if (prices.size < period) return List(prices.size) { prices.lastOrNull() ?: 0.0 }
        val k = 2.0 / (period + 1)
        val ema = mutableListOf<Double>()

        val initialSma = prices.take(period).average()
        var prevEma = initialSma

        for (i in prices.indices) {
            if (i < period - 1) {
                ema.add(prices[i])
            } else if (i == period - 1) {
                ema.add(prevEma)
            } else {
                val curEma = prices[i] * k + prevEma * (1 - k)
                ema.add(curEma)
                prevEma = curEma
            }
        }
        return ema
    }

    fun calculateRSI(prices: List<Double>, period: Int = 14): List<Double> {
        if (prices.size <= period) return List(prices.size) { 50.0 }
        val rsi = mutableListOf<Double>()
        val gains = mutableListOf<Double>()
        val losses = mutableListOf<Double>()

        for (i in 1 until prices.size) {
            val diff = prices[i] - prices[i - 1]
            gains.add(if (diff > 0) diff else 0.0)
            losses.add(if (diff < 0) abs(diff) else 0.0)
        }

        var avgGain = gains.take(period).average()
        var avgLoss = losses.take(period).average()

        repeat(period) { rsi.add(50.0) }

        for (i in period until prices.size) {
            val currentGain = gains[i - 1]
            val currentLoss = losses[i - 1]

            avgGain = (avgGain * (period - 1) + currentGain) / period
            avgLoss = (avgLoss * (period - 1) + currentLoss) / period

            if (avgLoss == 0.0) {
                rsi.add(100.0)
            } else {
                val rs = avgGain / avgLoss
                val currentRsi = 100.0 - (100.0 / (1.0 + rs))
                rsi.add(currentRsi)
            }
        }
        return rsi
    }

    fun calculateMACD(prices: List<Double>): List<Double> {
        val fastEMA = calculateEMA(prices, 12)
        val slowEMA = calculateEMA(prices, 26)
        val macdLine = fastEMA.zip(slowEMA) { f, s -> f - s }
        val signalLine = calculateEMA(macdLine, 9)
        return macdLine.zip(signalLine) { m, s -> m - s }
    }

    fun calculateATR(candles: List<DetectedCandle>, period: Int = 14): List<Double> {
        if (candles.size < 2) return List(candles.size) { 0.0 }
        val trueRanges = mutableListOf<Double>()
        for (i in candles.indices) {
            if (i == 0) {
                trueRanges.add(candles[i].high - candles[i].low)
            } else {
                val tr = max(
                    candles[i].high - candles[i].low,
                    max(
                        abs(candles[i].high - candles[i - 1].close),
                        abs(candles[i].low - candles[i - 1].close)
                    )
                )
                trueRanges.add(tr)
            }
        }
        return calculateEMA(trueRanges, period)
    }

    fun computeIndicators(candles: List<DetectedCandle>): TechnicalIndicators {
        val closePrices = candles.map { it.close }
        val ema9 = calculateEMA(closePrices, 9)
        val ema21 = calculateEMA(closePrices, 21)
        val ema50 = calculateEMA(closePrices, 50)
        val rsi14 = calculateRSI(closePrices, 14)
        val macdHistogram = calculateMACD(closePrices)
        val atr14 = calculateATR(candles, 14)

        val latest = closePrices.lastOrNull() ?: 100.0
        val first = closePrices.firstOrNull() ?: latest
        val changePct = if (first != 0.0) ((latest - first) / first) * 100.0 else 0.0

        return TechnicalIndicators(
            ema9 = ema9,
            ema21 = ema21,
            ema50 = ema50,
            rsi14 = rsi14,
            macdHistogram = macdHistogram,
            atr14 = atr14,
            latestPrice = latest,
            priceChangePercent = changePct
        )
    }

    fun analyzeMarketStructure(candles: List<DetectedCandle>): MarketStructure {
        val closePrices = candles.map { it.close }
        val allHighs = candles.map { it.high }
        val allLows = candles.map { it.low }
        val latestPrice = closePrices.lastOrNull() ?: 100.0

        val swingHighs = mutableListOf<Double>()
        val swingLows = mutableListOf<Double>()

        for (i in 1 until candles.size - 1) {
            if (candles[i].high > candles[i - 1].high && candles[i].high > candles[i + 1].high) {
                swingHighs.add(candles[i].high)
            }
            if (candles[i].low < candles[i - 1].low && candles[i].low < candles[i + 1].low) {
                swingLows.add(candles[i].low)
            }
        }

        val isHigherHigh = if (swingHighs.size >= 2) swingHighs.last() > swingHighs[swingHighs.size - 2] else false
        val isLowerHigh = if (swingHighs.size >= 2) swingHighs.last() < swingHighs[swingHighs.size - 2] else false
        val isHigherLow = if (swingLows.size >= 2) swingLows.last() > swingLows[swingLows.size - 2] else false
        val isLowerLow = if (swingLows.size >= 2) swingLows.last() < swingLows[swingLows.size - 2] else false

        val type = when {
            isHigherHigh && isHigherLow -> MarketStructureType.HIGHER_HIGH
            isLowerHigh && isLowerLow -> MarketStructureType.LOWER_LOW
            isHigherHigh && !isHigherLow -> MarketStructureType.REVERSAL_SETUP
            isLowerLow && !isLowerHigh -> MarketStructureType.CONTINUATION
            else -> MarketStructureType.CONSOLIDATION
        }

        val supportLevel = swingLows.lastOrNull() ?: (allLows.minOrNull() ?: latestPrice)
        val resistanceLevel = swingHighs.lastOrNull() ?: (allHighs.maxOrNull() ?: latestPrice)

        return MarketStructure(
            type = type,
            recentHigh = resistanceLevel,
            recentLow = supportLevel,
            isHigherHigh = isHigherHigh,
            isHigherLow = isHigherLow,
            isLowerHigh = isLowerHigh,
            isLowerLow = isLowerLow,
            supportLevel = supportLevel,
            resistanceLevel = resistanceLevel,
            distanceToSupport = max(0.0, latestPrice - supportLevel),
            distanceToResistance = max(0.0, resistanceLevel - latestPrice)
        )
    }
}
