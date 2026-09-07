package com.aisignal.otc.network

import android.graphics.Bitmap
import android.util.Base64
import com.aisignal.otc.model.GeminiAnalysisResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiApiClient(private val backendBaseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeChartWithGemini(
        chartBitmap: Bitmap,
        expiry: String,
        marketPair: String
    ): GeminiAnalysisResponse? = withContext(Dispatchers.IO) {
        try {
            val stream = ByteArrayOutputStream()
            chartBitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
            val byteArray = stream.toByteArray()
            val base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP)

            val jsonPayload = JSONObject().apply {
                put("imageBase64", base64Image)
                put("expiry", expiry)
                put("marketPair", marketPair)
            }

            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("\$backendBaseUrl/api/analyze-chart")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val responseBody = response.body?.string() ?: return@withContext null
            val obj = JSONObject(responseBody)

            GeminiAnalysisResponse(
                trend = obj.optString("trend", "NEUTRAL"),
                momentum = obj.optString("momentum", "NEUTRAL"),
                structure = obj.optString("structure", "CONSOLIDATION"),
                supportResistance = obj.optString("supportResistance", "MID_RANGE"),
                candlePattern = obj.optString("candlePattern", "NONE"),
                directionBias = obj.optString("directionBias", "NO_SIGNAL"),
                confidence = obj.optDouble("confidence", 0.0),
                reason = obj.optString("reason", "Analysis complete"),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
