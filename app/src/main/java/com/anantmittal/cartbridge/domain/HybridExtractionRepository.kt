package com.anantmittal.cartbridge.domain

import android.graphics.Bitmap
import android.util.Log
import com.anantmittal.cartbridge.data.CartItem
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class HybridExtractionRepository(
    private val remoteConfig: FirebaseRemoteConfig,
    private val firebaseAI: FirebaseAI
) {
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "HybridExtractionRepo"
        private const val PROMPT_CONFIG_KEY = "extraction_prompt"
        private const val DEFAULT_PROMPT = "Extract a JSON list of grocery items from this OCR text. Ignore prices and UI garbage. Output only JSON in the format: [{\"name\": \"Product Name\", \"qty\": 1}]. No markdown formatting."
    }

    suspend fun processCartImage(bitmap: Bitmap): List<CartItem> = withContext(Dispatchers.IO) {
        try {
            // 1. On-Device OCR
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = textRecognizer.process(image).await()
            val rawText = visionText.text

            if (rawText.isBlank()) {
                Log.w(TAG, "OCR found no text.")
                return@withContext emptyList()
            }

            // 2. Fetch System Prompt
            fetchRemoteConfig()
            val prompt = remoteConfig.getString(PROMPT_CONFIG_KEY).takeIf { it.isNotBlank() } ?: DEFAULT_PROMPT

            val combinedPrompt = "$prompt\n\nOCR Text:\n$rawText"

            // 3. Hybrid NLP Extraction
            var jsonString: String? = null
            
            // Step 3a: Attempt Android AICore (Gemini Nano)
            try {
                 // Note: Gemini Nano via AICore currently requires specific Early Access SDKs
                 // (e.g. com.google.ai.edge.aicore or ML Kit Generative AI). 
                 // We wrap in a conceptual try-catch to fallback safely to Vertex AI.
                 // In production, instantiate the appropriate Nano client here.
                 // val nanoModel = MLKitGenerativeModel("gemini-nano")
                 // val response = nanoModel.generateContent(combinedPrompt).await()
                 // jsonString = response.text
                 throw Exception("Nano SDK not integrated - falling back")
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Nano failed or unsupported, falling back to Vertex AI: ${e.message}")
            }

            // Step 3b: Fallback to Firebase AI Logic (Gemini)
            if (jsonString.isNullOrBlank()) {
                val model = firebaseAI.generativeModel("gemini-3.7-flash")
                val response = model.generateContent(combinedPrompt)
                jsonString = response.text
                Log.d(TAG, "Successfully extracted using Firebase AI Logic.")
            }

            // 4. Parse JSON
            parseExtractedJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Error in processCartImage", e)
            emptyList()
        }
    }

    private suspend fun fetchRemoteConfig() {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch remote config, using defaults", e)
        }
    }

    private fun parseExtractedJson(rawJson: String?): List<CartItem> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return try {
            // Remove markdown code blocks if the model included them
            val cleanedJson = rawJson.replace("```json", "").replace("```", "").trim()
            json.decodeFromString<List<CartItem>>(cleanedJson)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON: $rawJson", e)
            emptyList()
        }
    }
}
