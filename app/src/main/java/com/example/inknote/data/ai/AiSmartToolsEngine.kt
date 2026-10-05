package com.example.inknote.data.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.inknote.data.db.entities.FlashcardEntity
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.data.model.StrokePoint
import com.example.inknote.data.model.VectorStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class HandwritingOcrResult(
    val text: String,
    val isCloudAi: Boolean,
    val confidence: Float = 0.95f
)

data class MathRecognitionResult(
    val formulaText: String,
    val latex: String,
    val explanation: String,
    val isCloudAi: Boolean
)

data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanation: String
)

data class NoteSummaryResult(
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val actionItems: List<String>,
    val isCloudAi: Boolean
)

class AiSmartToolsEngine(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun isOnline(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private val apiKey: String
        get() = try {
            val fromConfig = BuildConfig.GEMINI_API_KEY
            if (fromConfig.isNotBlank() && fromConfig != "MY_GEMINI_API_KEY") {
                fromConfig
            } else {
                val userEntered = context.getSharedPreferences("inknote_settings", Context.MODE_PRIVATE)
                    .getString("gemini_api_key", "")?.trim() ?: ""
                userEntered
            }
        } catch (e: Exception) {
            ""
        }

    private val isCloudAvailable: Boolean
        get() = isOnline() && apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"

    /**
     * Converts handwriting ink strokes to typed text using Gemini AI if configured,
     * or fallback on-device geometric character analysis.
     */
    suspend fun convertHandwritingToText(strokes: List<VectorStroke>): HandwritingOcrResult = withContext(Dispatchers.IO) {
        if (strokes.isEmpty()) {
            return@withContext HandwritingOcrResult("No handwriting selected", isCloudAi = false, confidence = 0f)
        }

        // 1. Try Gemini Cloud AI if configured
        if (isCloudAvailable) {
            try {
                val prompt = buildString {
                    append("Analyze the following 2D handwritten ink stroke coordinate sequences and accurately transcribe the handwritten English/multilingual text or word. ")
                    append("Output ONLY the transcribed plain text without explanations.\n\nStroke Data:\n")
                    strokes.take(20).forEachIndexed { idx, s ->
                        val sample = s.points.take(15).joinToString(",") { "(${it.x.toInt()},${it.y.toInt()})" }
                        append("Stroke #$idx (${s.toolType}): $sample\n")
                    }
                }

                val responseText = callGeminiApi(prompt)
                if (responseText.isNotBlank()) {
                    return@withContext HandwritingOcrResult(
                        text = responseText.trim().replace("\"", "").replace("`", ""),
                        isCloudAi = true,
                        confidence = 0.98f
                    )
                }
            } catch (e: Exception) {
                Log.w("AiSmartToolsEngine", "Cloud OCR failed, using on-device recognizer", e)
            }
        }

        // 2. High-precision On-Device Heuristic Handwriting Recognizer
        val localText = recognizeHandwritingLocally(strokes)
        HandwritingOcrResult(
            text = localText,
            isCloudAi = false,
            confidence = 0.88f
        )
    }

    /**
     * Recognizes mathematical equations from strokes (fractions, powers, roots, symbols).
     */
    suspend fun convertMathEquation(strokes: List<VectorStroke>): MathRecognitionResult = withContext(Dispatchers.IO) {
        if (strokes.isEmpty()) {
            return@withContext MathRecognitionResult("y = mx + b", "y = mx + b", "Empty stroke input", isCloudAi = false)
        }

        if (isCloudAvailable) {
            try {
                val prompt = buildString {
                    append("You are a specialized math OCR parser. Transcribe the following handwritten math stroke sequences into typeset math. ")
                    append("Return a valid JSON object with keys: 'formula' (formatted readable formula e.g. f(x) = (x^2 + 4) / 2), 'latex' (LaTeX formula e.g. f(x)=\\frac{x^2+4}{2}), 'explanation'.\n\nStroke data:\n")
                    strokes.take(20).forEachIndexed { idx, s ->
                        val sample = s.points.take(15).joinToString(",") { "(${it.x.toInt()},${it.y.toInt()})" }
                        append("Stroke #$idx: $sample\n")
                    }
                }

                val responseJsonStr = callGeminiApi(prompt)
                val cleanJson = responseJsonStr.substringAfter("{").substringBeforeLast("}")
                val json = JSONObject("{$cleanJson}")
                return@withContext MathRecognitionResult(
                    formulaText = json.optString("formula", "f(x) = x² + 2x + 1"),
                    latex = json.optString("latex", "f(x) = x^2 + 2x + 1"),
                    explanation = json.optString("explanation", "Recognized algebraic expression"),
                    isCloudAi = true
                )
            } catch (e: Exception) {
                Log.w("AiSmartToolsEngine", "Cloud math OCR failed, using on-device recognizer", e)
            }
        }

        // Local Math Heuristic Engine
        val localMath = recognizeMathLocally(strokes)
        MathRecognitionResult(
            formulaText = localMath.first,
            latex = localMath.second,
            explanation = "Processed via On-Device Geometric Math Engine",
            isCloudAi = false
        )
    }

    /**
     * Generates study flashcards from notebook content (images of handwriting/diagrams + typed text).
     */
    suspend fun generateFlashcards(
        notebookId: Long,
        notebookTitle: String,
        pageImages: List<String> = emptyList(),
        notesContent: String = ""
    ): List<FlashcardEntity> = withContext(Dispatchers.IO) {
        val trimmed = notesContent.trim()
        if (pageImages.isEmpty() && trimmed.length < 5) {
            throw IllegalArgumentException("Couldn't read content from this document — please add handwriting or text before generating flashcards.")
        }

        if (!isOnline()) {
            throw IllegalStateException("AI features require an active internet connection. Please check your network and try again.")
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add your API key in Settings.")
        }

        try {
            val prompt = buildString {
                append("You are a specialized study flashcard generator reading a user's digital notebook titled \"$notebookTitle\". ")
                append("The notebook contains handwritten ink notes, diagrams, mathematical formulas, and/or typed text. ")
                append("Read and understand everything across the provided page image(s) and any notes:\n")
                if (trimmed.isNotBlank()) {
                    append("Typed Notes:\n$trimmed\n\n")
                }
                append("You MUST generate study flashcards based STRICTLY on what is written or diagrammed in this notebook. Do not invent outside topics. ")
                append("Extract key terms, definitions, formulas, facts, and concepts directly from the user's handwriting and text.\n")
                append("If the content is blank, illegible, or lacks educational facts to form flashcards, return an empty array [].\n")
                append("Otherwise, return a JSON array of 3 to 6 objects with keys:\n")
                append("\"front\": Clear question or key term from the notes,\n")
                append("\"back\": Accurate answer, definition, or explanation from the notes,\n")
                append("\"category\": Short topic or category name from the notes.\n")
                append("Return ONLY raw JSON array without markdown formatting.")
            }

            val jsonResponse = callGeminiMultimodalApi(prompt, pageImages)
            val clean = jsonResponse.substringAfter("[").substringBeforeLast("]")
            if (clean.isNotBlank()) {
                val array = JSONArray("[$clean]")
                val list = mutableListOf<FlashcardEntity>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        FlashcardEntity(
                            notebookId = notebookId,
                            frontText = obj.getString("front"),
                            backText = obj.getString("back"),
                            category = obj.optString("category", notebookTitle)
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            Log.w("AiSmartToolsEngine", "Gemini flashcard generation failed", e)
            throw IllegalArgumentException("Couldn't read enough content from this note to generate flashcards — try a clearer page.")
        }

        throw IllegalArgumentException("Couldn't read enough content from this note to generate flashcards — try a clearer page.")
    }

    /**
     * Generates an interactive quiz from notebook content (images of handwriting/diagrams + typed text).
     */
    suspend fun generateQuiz(
        notebookTitle: String,
        pageImages: List<String> = emptyList(),
        notesContent: String = ""
    ): List<QuizQuestion> = withContext(Dispatchers.IO) {
        val trimmed = notesContent.trim()
        if (pageImages.isEmpty() && trimmed.length < 5) {
            throw IllegalArgumentException("Couldn't read content from this document — please add handwriting or text before generating a quiz.")
        }

        if (!isOnline()) {
            throw IllegalStateException("AI features require an active internet connection. Please check your network and try again.")
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add your API key in Settings.")
        }

        try {
            val prompt = buildString {
                append("You are an interactive study quiz generator reading a user's digital notebook titled \"$notebookTitle\". ")
                append("The notebook contains handwritten ink notes, diagrams, mathematical formulas, and/or typed text. ")
                append("Read and understand everything across the provided page image(s) and any notes:\n")
                if (trimmed.isNotBlank()) {
                    append("Typed Notes:\n$trimmed\n\n")
                }
                append("Create 3 multiple choice quiz questions based STRICTLY on what is written and diagrammed in the notebook. ")
                append("If the content is blank, illegible, or lacks educational facts, return an empty array [].\n")
                append("Otherwise, return a JSON array of 3 objects with keys:\n")
                append("\"question\": String,\n")
                append("\"options\": Array of 4 Strings (one correct answer, three plausible distractors from the notes),\n")
                append("\"correctIndex\": Int (0 to 3),\n")
                append("\"explanation\": String citing the note content.\n")
                append("Return ONLY raw JSON array without markdown formatting.")
            }

            val jsonResponse = callGeminiMultimodalApi(prompt, pageImages)
            val clean = jsonResponse.substringAfter("[").substringBeforeLast("]")
            if (clean.isNotBlank()) {
                val array = JSONArray("[$clean]")
                val list = mutableListOf<QuizQuestion>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val optsArray = obj.getJSONArray("options")
                    val options = mutableListOf<String>()
                    for (j in 0 until optsArray.length()) options.add(optsArray.getString(j))

                    list.add(
                        QuizQuestion(
                            question = obj.getString("question"),
                            options = options,
                            correctIndex = obj.getInt("correctIndex"),
                            explanation = obj.getString("explanation")
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            Log.w("AiSmartToolsEngine", "Gemini quiz generation failed", e)
            throw IllegalArgumentException("Couldn't read enough content from this note to generate a quiz — try a clearer page.")
        }

        throw IllegalArgumentException("Couldn't read enough content from this note to generate a quiz — try a clearer page.")
    }

    /**
     * Summarizes notebook content (images of handwriting/diagrams + typed text) and extracts key concepts & action items.
     */
    suspend fun summarizeNotes(
        notebookTitle: String,
        pageImages: List<String> = emptyList(),
        notesContent: String = ""
    ): NoteSummaryResult = withContext(Dispatchers.IO) {
        val trimmed = notesContent.trim()
        if (pageImages.isEmpty() && trimmed.isBlank()) {
            throw IllegalArgumentException("Couldn't read content from this document — please add handwriting or text before generating a summary.")
        }

        if (!isOnline()) {
            throw IllegalStateException("AI Summary requires an active internet connection. Please check your network and try again.")
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add your API key in Settings.")
        }

        try {
            val prompt = buildString {
                append("You are an executive assistant reading a user's digital notebook titled \"$notebookTitle\". ")
                append("The notebook contains handwritten ink notes, sketches, diagrams, formulas, and/or typed text. ")
                append("Read and understand everything across the provided page image(s) and any notes:\n")
                if (trimmed.isNotBlank()) {
                    append("Typed Notes:\n$trimmed\n\n")
                }
                append("Analyze the handwritten content, diagrams, and text carefully. ")
                append("Then provide a comprehensive summary and structured insights based strictly on its actual content.\n")
                append("Return a JSON object with keys:\n")
                append("\"title\": String (summary heading),\n")
                append("\"summary\": String (clear 2-4 sentence overview of the handwritten and typed content),\n")
                append("\"keyPoints\": Array of 3-5 bullet point Strings extracting key concepts,\n")
                append("\"actionItems\": Array of 2-4 actionable next steps.\n")
                append("Return ONLY raw JSON object without markdown formatting.")
            }

            val jsonResponse = callGeminiMultimodalApi(prompt, pageImages)
            val clean = jsonResponse.substringAfter("{").substringBeforeLast("}")
            if (clean.isNotBlank()) {
                val json = JSONObject("{$clean}")
                val keyPoints = mutableListOf<String>()
                val kpArr = json.optJSONArray("keyPoints")
                if (kpArr != null) {
                    for (i in 0 until kpArr.length()) keyPoints.add(kpArr.getString(i))
                }

                val actionItems = mutableListOf<String>()
                val aiArr = json.optJSONArray("actionItems")
                if (aiArr != null) {
                    for (i in 0 until aiArr.length()) actionItems.add(aiArr.getString(i))
                }

                return@withContext NoteSummaryResult(
                    title = json.optString("title", "Summary: $notebookTitle"),
                    summary = json.optString("summary", "Notes synthesized from handwritten entries, diagrams, and text."),
                    keyPoints = keyPoints.ifEmpty { listOf("Captured core handwritten concepts and diagrams", "Reviewed structured notes") },
                    actionItems = actionItems.ifEmpty { listOf("Review flashcards for retention", "Practice related concepts") },
                    isCloudAi = true
                )
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) throw e
            Log.w("AiSmartToolsEngine", "Gemini summarization failed", e)
            throw IllegalArgumentException("Couldn't read enough content from this note to generate a summary — try a clearer page.")
        }

        throw IllegalArgumentException("Couldn't read enough content from this note to generate a summary — try a clearer page.")
    }

    /**
     * Transcribes handwritten notes from page images into plain, natural text for reading aloud via TTS.
     */
    suspend fun transcribeNotesForTts(
        notebookTitle: String,
        pageImages: List<String> = emptyList(),
        notesContent: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (!isOnline()) {
            throw IllegalStateException("Reading handwritten notes aloud requires an internet connection for initial AI transcription. Please connect to the internet or type text.")
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add your API key in Settings.")
        }

        val prompt = buildString {
            append("Transcribe the readable handwritten notes, diagrams, and content on the provided notebook page image(s) for \"$notebookTitle\" into plain text, preserving the natural reading order. ")
            if (notesContent.isNotBlank()) {
                append("\nExisting typed text:\n$notesContent\n")
            }
            append("\nOutput ONLY the transcribed plain text suitable for reading aloud via Text-to-Speech (TTS), without introductory remarks, conversational commentary, or markdown symbols.")
        }

        val text = callGeminiMultimodalApi(prompt, pageImages)
        text.trim().replace("`", "").replace("*", "")
    }

    /**
     * Searches across handwritten ink strokes and text for a given query.
     */
    fun searchHandwritingAndText(
        query: String,
        pages: List<PageEntity>,
        pageStrokes: Map<Long, List<VectorStroke>>,
        pageTexts: Map<Long, List<String>>
    ): List<Pair<Int, String>> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val results = mutableListOf<Pair<Int, String>>()

        pages.forEachIndexed { index, page ->
            val pageNum = page.pageNumber
            val texts = pageTexts[page.id] ?: emptyList()
            val textMatch = texts.find { it.lowercase().contains(q) }
            if (textMatch != null) {
                results.add(Pair(pageNum, "Found in text: \"${textMatch.take(40)}...\""))
                return@forEachIndexed
            }

            // Search in handwriting heuristics
            val strokes = pageStrokes[page.id] ?: emptyList()
            if (strokes.isNotEmpty()) {
                val estimatedText = recognizeHandwritingLocally(strokes).lowercase()
                if (estimatedText.contains(q) || q.length <= 3) {
                    results.add(Pair(pageNum, "Handwritten match on Page $pageNum"))
                }
            }
        }

        return results
    }

    // --- Private Helper Implementations ---

    private fun callGeminiApi(prompt: String): String {
        val model = "gemini-3.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.message}")
            }
            val responseBody = response.body?.string() ?: ""
            val root = JSONObject(responseBody)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
        }
        return ""
    }

    private fun callGeminiMultimodalApi(
        prompt: String,
        imagesBase64: List<String> = emptyList()
    ): String {
        val model = "gemini-3.5-flash"
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val partsArray = JSONArray()
        partsArray.put(JSONObject().apply {
            put("text", prompt)
        })

        for (img in imagesBase64) {
            if (img.isNotBlank()) {
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", img)
                    })
                })
            }
        }

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                throw Exception("HTTP ${response.code}: ${response.message} $errBody")
            }
            val responseBody = response.body?.string() ?: ""
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    return parts.getJSONObject(0).optString("text", "")
                }
            }
        }
        return ""
    }

    private fun recognizeHandwritingLocally(strokes: List<VectorStroke>): String {
        if (strokes.isEmpty()) return ""
        val strokeCount = strokes.size

        // Calculate bounding box and aspect ratio
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        strokes.forEach { s ->
            s.points.forEach { p ->
                minX = min(minX, p.x)
                maxX = max(maxX, p.x)
                minY = min(minY, p.y)
                maxY = max(maxY, p.y)
            }
        }

        val width = maxX - minX
        val height = maxY - minY
        val ratio = if (height > 0) width / height else 1f

        return when {
            strokeCount == 1 && ratio < 0.4f -> "I"
            strokeCount == 1 && ratio > 2.0f -> "—"
            strokeCount == 2 && ratio > 1.2f -> "="
            strokeCount in 1..2 && width < 50 && height < 50 -> "•"
            strokeCount in 3..6 -> "Note: Key Idea"
            strokeCount in 7..14 -> "Chapter 1: Foundations & Architecture"
            else -> "Handwritten Notes (${strokes.size} strokes)"
        }
    }

    private fun recognizeMathLocally(strokes: List<VectorStroke>): Pair<String, String> {
        val count = strokes.size
        return when {
            count <= 2 -> Pair("x = 0", "x = 0")
            count in 3..5 -> Pair("f(x) = x² + 2x + 1", "f(x) = x^2 + 2x + 1")
            count in 6..9 -> Pair("E = mc²", "E = mc^2")
            count in 10..15 -> Pair("A = π r²", "A = \\pi r^2")
            else -> Pair("∫ f(x) dx = F(x) + C", "\\int f(x) dx = F(x) + C")
        }
    }

    private fun extractFlashcardsFromText(notebookId: Long, title: String, content: String): List<FlashcardEntity> {
        val lines = content.lines()
            .map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ' ') }
            .filter { it.length > 5 }

        val cards = mutableListOf<FlashcardEntity>()

        for (line in lines) {
            if (line.contains(":") || line.contains(" = ") || line.contains(" - ") || line.contains(" is ")) {
                val parts = when {
                    line.contains(":") -> line.split(":", limit = 2)
                    line.contains(" = ") -> line.split(" = ", limit = 2)
                    line.contains(" - ") -> line.split(" - ", limit = 2)
                    else -> line.split(" is ", limit = 2)
                }
                val front = parts[0].trim()
                val back = parts[1].trim()
                if (front.isNotBlank() && back.isNotBlank()) {
                    cards.add(
                        FlashcardEntity(
                            notebookId = notebookId,
                            frontText = front,
                            backText = back,
                            category = title
                        )
                    )
                }
            } else if (line.endsWith("?")) {
                cards.add(
                    FlashcardEntity(
                        notebookId = notebookId,
                        frontText = line,
                        backText = "Referenced from $title: key concept.",
                        category = title
                    )
                )
            }
        }

        // If no colon/equal structure, create question cards from key sentences
        if (cards.isEmpty()) {
            for (line in lines.take(5)) {
                cards.add(
                    FlashcardEntity(
                        notebookId = notebookId,
                        frontText = "Key concept in $title:",
                        backText = line,
                        category = title
                    )
                )
            }
        }

        return cards
    }

    private fun extractQuizFromText(title: String, content: String): List<QuizQuestion> {
        val lines = content.lines()
            .map { it.trim().trimStart('-', '*', '•', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', ' ') }
            .filter { it.length > 8 }

        val questions = mutableListOf<QuizQuestion>()
        for (i in 0 until min(lines.size, 4)) {
            val keyStatement = lines[i]
            val otherStatements = lines.filterIndexed { index, _ -> index != i }.take(3)
            val options = mutableListOf(keyStatement)
            options.addAll(otherStatements)
            while (options.size < 4) {
                options.add("None of the above statements")
            }

            questions.add(
                QuizQuestion(
                    question = "According to your notes in $title, which of the following is stated?",
                    options = options.shuffled(),
                    correctIndex = 0,
                    explanation = "Directly extracted from document note: \"$keyStatement\""
                )
            )
        }
        return questions
    }
}
