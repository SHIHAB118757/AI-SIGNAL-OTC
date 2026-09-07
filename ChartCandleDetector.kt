package com.aisignal.otc.cv

import android.graphics.Bitmap
import android.graphics.Color
import com.aisignal.otc.model.CandleDirection
import com.aisignal.otc.model.DetectedCandle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object ChartCandleDetector {

    data class DetectionResult(
        val isSuccess: Boolean,
        val candles: List<DetectedCandle>,
        val errorMessage: String? = null
    )

    fun detectCandles(bitmap: Bitmap): DetectionResult {
        val width = bitmap.width
        val height = bitmap.height

        val startX = (width * 0.05).toInt()
        val endX = (width * 0.92).toInt()
        val startY = (height * 0.10).toInt()
        val endY = (height * 0.88).toInt()

        val activeColumns = mutableListOf<Int>()
        val columnCandlePixels = mutableMapOf<Int, MutableList<Int>>()
        val columnColors = mutableMapOf<Int, CandleDirection>()

        var totalGreen = 0
        var totalRed = 0

        for (x in startX until endX step 2) {
            val yList = mutableListOf<Int>()
            var greenCount = 0
            var redCount = 0

            for (y in startY until endY step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val isGreen = g > 130 && g > r * 1.35 && g > b * 1.15
                val isRed = r > 140 && r > g * 1.35 && r > b * 1.2

                if (isGreen) {
                    yList.add(y)
                    greenCount++
                    totalGreen++
                } else if (isRed) {
                    yList.add(y)
                    redCount++
                    totalRed++
                }
            }

            if (yList.size >= 4) {
                activeColumns.add(x)
                columnCandlePixels[x] = yList
                columnColors[x] = if (greenCount >= redCount) CandleDirection.GREEN else CandleDirection.RED
            }
        }

        if (totalGreen + totalRed < 180) {
            return DetectionResult(
                isSuccess = false,
                candles = emptyList(),
                errorMessage = "Insufficient candle pixels detected. Please ensure Quotex chart is clearly displayed."
            )
        }

        val clusters = mutableListOf<List<Int>>()
        var currentCluster = mutableListOf<Int>()

        for (i in activeColumns.indices) {
            val col = activeColumns[i]
            if (currentCluster.isEmpty()) {
                currentCluster.add(col)
            } else {
                val prev = currentCluster.last()
                if (col - prev <= 8) {
                    currentCluster.add(col)
                } else {
                    if (currentCluster.size >= 2) {
                        clusters.add(currentCluster)
                    }
                    currentCluster = mutableListOf(col)
                }
            }
        }
        if (currentCluster.size >= 2) {
            clusters.add(currentCluster)
        }

        if (clusters.size < 4) {
            return DetectionResult(
                isSuccess = false,
                candles = emptyList(),
                errorMessage = "Detected only \${clusters.size} candles. Minimum 5 candles required for reliable signal."
            )
        }

        val detectedCandles = mutableListOf<DetectedCandle>()
        val chartHeight = (endY - startY).toDouble()

        clusters.forEachIndexed { index, clusterCols ->
            val allY = mutableListOf<Int>()
            var greenPixels = 0
            var redPixels = 0

            clusterCols.forEach { col ->
                val list = columnCandlePixels[col] ?: emptyList()
                allY.addAll(list)
                if (columnColors[col] == CandleDirection.GREEN) greenPixels += list.size
                else redPixels += list.size
            }

            if (allY.isNotEmpty()) {
                val isBullish = greenPixels >= redPixels
                val direction = if (isBullish) CandleDirection.GREEN else CandleDirection.RED

                val minY = allY.minOrNull() ?: startY
                val maxY = allY.maxOrNull() ?: endY

                val yCounts = allY.groupingBy { (it / 4) * 4 }.eachCount()
                val bodyYs = yCounts.filter { it.value >= max((clusterCols.size * 0.35).toInt(), 2) }
                    .keys.sorted()

                val bodyTopY = if (bodyYs.isNotEmpty()) bodyYs.first() else minY + (maxY - minY) / 4
                val bodyBottomY = if (bodyYs.isNotEmpty()) bodyYs.last() else maxY - (maxY - minY) / 4

                val priceHigh = 100.0 + ((endY - minY) / chartHeight) * 5.0
                val priceLow = 100.0 + ((endY - maxY) / chartHeight) * 5.0
                val priceBodyTop = 100.0 + ((endY - bodyTopY) / chartHeight) * 5.0
                val priceBodyBottom = 100.0 + ((endY - bodyBottomY) / chartHeight) * 5.0

                val open = if (isBullish) priceBodyBottom else priceBodyTop
                val close = if (isBullish) priceBodyTop else priceBodyBottom
                val high = max(priceHigh, max(open, close))
                val low = min(priceLow, min(open, close))

                val bodySize = abs(close - open)
                val upperWick = high - max(open, close)
                val lowerWick = min(open, close) - low
                val relativeRange = high - low
                val volatility = if (bodySize > 0.001) relativeRange / bodySize else 1.0

                detectedCandles.add(
                    DetectedCandle(
                        index = index,
                        open = (open * 1000.0).toInt() / 1000.0,
                        high = (high * 1000.0).toInt() / 1000.0,
                        low = (low * 1000.0).toInt() / 1000.0,
                        close = (close * 1000.0).toInt() / 1000.0,
                        bodySize = (bodySize * 1000.0).toInt() / 1000.0,
                        upperWick = (upperWick * 1000.0).toInt() / 1000.0,
                        lowerWick = (lowerWick * 1000.0).toInt() / 1000.0,
                        direction = direction,
                        relativeRange = (relativeRange * 1000.0).toInt() / 1000.0,
                        relativeVolatility = (volatility * 100.0).toInt() / 100.0
                    )
                )
            }
        }

        return DetectionResult(isSuccess = true, candles = detectedCandles)
    }
}
