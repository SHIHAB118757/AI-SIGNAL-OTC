package com.aisignal.otc.model

import com.google.gson.annotations.SerializedName

enum class ExpiryTime(val label: String, val seconds: Int) {
    SEC_5("5 sec", 5),
    SEC_10("10 sec", 10),
    SEC_15("15 sec", 15),
    SEC_20("20 sec", 20),
    SEC_30("30 sec", 30),
    SEC_45("45 sec", 45),
    MIN_1("1 min", 60)
}

enum class SignalDirection {
    UP,
    DOWN,
    NO_SIGNAL
}

enum class CandleDirection {
    GREEN,
    RED,
    NEUTRAL
}

enum class CandlePatternType {
    DOJI,
    HAMMER,
    SHOOTING_STAR,
    BULLISH_ENGULFING,
    BEARISH_ENGULFING,
    MORNING_STAR,
    EVENING_STAR,
    STRONG_BULLISH,
    STRONG_BEARISH,
    NONE
}

enum class MarketStructureType {
    HIGHER_HIGH,
    LOWER_LOW,
    CONSOLIDATION,
    REVERSAL_SETUP,
    CONTINUATION
}

data class DetectedCandle(
    val index: Int,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val bodySize: Double,
    val upperWick: Double,
    val lowerWick: Double,
    val direction: CandleDirection,
    val relativeRange: Double,
    val relativeVolatility: Double
)

data class TechnicalIndicators(
    val ema9: List<Double>,
    val ema21: List<Double>,
    val ema50: List<Double>,
    val rsi14: List<Double>,
    val macdHistogram: List<Double>,
    val atr14: List<Double>,
    val latestPrice: Double,
    val priceChangePercent: Double
)

data class MarketStructure(
    val type: MarketStructureType,
    val recentHigh: Double,
    val recentLow: Double,
    val isHigherHigh: Boolean,
    val isHigherLow: Boolean,
    val isLowerHigh: Boolean,
    val isLowerLow: Boolean,
    val supportLevel: Double,
    val resistanceLevel: Double,
    val distanceToSupport: Double,
    val distanceToResistance: Double
)

data class GeminiAnalysisResponse(
    @SerializedName("trend") val trend: String?,
    @SerializedName("momentum") val momentum: String?,
    @SerializedName("structure") val structure: String?,
    @SerializedName("supportResistance") val supportResistance: String?,
    @SerializedName("candlePattern") val candlePattern: String?,
    @SerializedName("directionBias") val directionBias: String?,
    @SerializedName("confidence") val confidence: Double?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("timestamp") val timestamp: Long?
)

data class SignalResult(
    val signal: SignalDirection,
    val confidence: Int,
    val expiry: ExpiryTime,
    val marketPair: String,
    val captureTime: Long,
    val analysisTime: Long,
    val detectedCandlesCount: Int,
    val latestCandle: DetectedCandle?,
    val detectedPattern: CandlePatternType,
    val indicatorsSummary: String,
    val rsiValue: Double,
    val emaSummary: String,
    val structureSummary: String,
    val geminiReason: String?,
    val upScore: Int,
    val downScore: Int,
    val rejectionReason: String? = null
)
