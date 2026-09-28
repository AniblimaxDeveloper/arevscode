package com.arevscode.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiTurbo(private val prefs: Prefs) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var previousInteractionId: String? = null

    fun resetConversation() {
        previousInteractionId = null
    }

    private fun thinkingLevel(prompt: String): String {
        val p = prompt.lowercase()

        if (
            p.length < 120 &&
            !p.contains("error") &&
            !p.contains("bug") &&
            !p.contains("build") &&
            !p.contains("refactor") &&
            !p.contains("debug") &&
            !p.contains("compile")
        ) {
            return "low"
        }

        return if (
            p.contains("error") ||
            p.contains("bug") ||
            p.contains("debug") ||
            p.contains("refactor") ||
            p.contains("compile") ||
            p.contains("build") ||
            p.contains("crash") ||
            p.contains("optimize")
        ) {
            "high"
        } else {
            "medium"
        }
    }

    private fun extractOutput(json: JSONObject): String {
        val steps = json.optJSONArray("steps") ?: return ""
        val parts = mutableListOf<String>()

        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i) ?: continue

            if (step.optString("type") != "model_output") {
                continue
            }

            val content =
                step.optJSONArray("content")
                    ?: continue

            for (j in 0 until content.length()) {
                val block =
                    content.optJSONObject(j)
                        ?: continue

                if (block.optString("type") == "text") {
                    val text =
                        block.optString("text")

                    if (text.isNotBlank()) {
                        parts += text
                    }
                }
            }
        }

        return parts.joinToString("\n").trim()
    }

    private fun modelChain(): List<String> {
        val preferred =
            prefs.model.trim()
                .ifBlank { "gemini-3.5-flash" }

        return linkedSetOf(
            preferred,
            "gemini-3.8-flash",
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite"
        ).toList()
    }

    private fun shouldRetry(code: Int): Boolean =
        code == 429 || code == 500 || code == 502 ||
            code == 503 || code == 504

    private fun request(
        model: String,
        apiKey: String,
        payload: String
    ): Pair<Int, String> {
        val request =
            Request.Builder()
                .url(
                    "https://generativelanguage.googleapis.com/v1beta/interactions"
                )
                .addHeader(
                    "x-goog-api-key",
                    apiKey
                )
                .addHeader(
                    "Content-Type",
                    "application/json"
                )
                .post(
                    payload.toRequestBody(
                        "application/json; charset=utf-8".toMediaType()
                    )
                )
                .build()

        return client.newCall(request).execute().use { response ->
            response.code to
                response.body?.string().orEmpty()
        }
    }

    fun ask(
        prompt: String,
        code: String
    ): String {
        val key = prefs.geminiKey.trim()

        if (key.isEmpty()) {
            return "Gemini API key belum diisi. Buka Settings → Gemini API key."
        }

        val input =
            buildString {
                append(prompt.trim())
                append(
                    "\n\nCURRENT EDITOR CONTEXT:\n"
                )
                append(code.take(100_000))
            }

        val thinking =
            thinkingLevel(prompt)

        var lastError =
            "Gemini belum memberikan respons."

        for (model in modelChain()) {
            var attempt = 0

            while (attempt < 2) {
                val payload =
                    JSONObject().apply {
                        put(
                            "model",
                            model
                        )

                        put(
                            "input",
                            input
                        )

                        put(
                            "store",
                            true
                        )

                        put(
                            "system_instruction",
                            """
                            You are Avescode Copilot, a precise mobile coding agent.
                            Answer the user's request directly.
                            For coding tasks, identify the requested change first.
                            Keep output concise unless detail is necessary.
                            Never invent project APIs when context is provided.
                            When proposing code changes, prefer complete safe patches.
                            """.trimIndent()
                        )

                        put(
                            "generation_config",
                            JSONObject().apply {
                                put(
                                    "thinking_level",
                                    thinking
                                )
                            }
                        )

                        previousInteractionId?.let {
                            if (
                                it.isNotBlank() &&
                                attempt == 0 &&
                                model == prefs.model.trim()
                            ) {
                                put(
                                    "previous_interaction_id",
                                    it
                                )
                            }
                        }
                    }.toString()

                val result =
                    runCatching {
                        request(
                            model,
                            key,
                            payload
                        )
                    }.getOrElse {
                        -1 to
                            "Network error: ${
                                it.message
                                    ?: "unknown"
                            }"
                    }

                val codeValue =
                    result.first

                val raw =
                    result.second

                if (codeValue in 200..299) {
                    val json =
                        runCatching {
                            JSONObject(raw)
                        }.getOrElse {
                            return "Gemini mengembalikan respons yang tidak valid."
                        }

                    previousInteractionId =
                        json.optString("id")
                            .takeIf { it.isNotBlank() }

                    val output =
                        extractOutput(json)

                    if (output.isNotBlank()) {
                        return output
                    }

                    return "Avescode Copilot menerima respons kosong."
                }

                lastError =
                    "Gemini HTTP $codeValue: ${
                        raw.take(500)
                    }"

                if (
                    shouldRetry(codeValue) &&
                    attempt == 0
                ) {
                    Thread.sleep(650L)
                    attempt++
                    continue
                }

                // Don't carry an interaction ID from a failed
                // model into the fallback model.
                if (model != prefs.model.trim()) {
                    previousInteractionId = null
                }

                break
            }
        }

        return when {
            lastError.contains("HTTP 503") ||
                lastError.contains("HTTP 429") ->
                "Gemini sedang padat. Avescode sudah mencoba model fallback. Coba lagi beberapa detik."

            else ->
                "Copilot gagal: $lastError"
        }
    }
}
