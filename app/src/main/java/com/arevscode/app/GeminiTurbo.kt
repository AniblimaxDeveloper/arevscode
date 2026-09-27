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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var previousInteractionId: String? = null

    fun resetConversation() {
        previousInteractionId = null
    }

    private fun thinkingLevel(prompt: String): String {
        val p = prompt.lowercase()

        if (p.length < 90 &&
            !p.contains("error") &&
            !p.contains("bug") &&
            !p.contains("build") &&
            !p.contains("refactor")
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
            if (step.optString("type") != "model_output") continue

            val content = step.optJSONArray("content") ?: continue

            for (j in 0 until content.length()) {
                val block = content.optJSONObject(j) ?: continue
                if (block.optString("type") == "text") {
                    val text = block.optString("text")
                    if (text.isNotBlank()) parts += text
                }
            }
        }

        return parts.joinToString("\n").trim()
    }

    fun ask(prompt: String, code: String): String {
        val key = prefs.geminiKey.trim()
        if (key.isEmpty()) {
            return "Masukkan Gemini API key di Settings terlebih dahulu."
        }

        val model = "gemini-3.8-flash"
        val level = thinkingLevel(prompt)

        val input = buildString {
            append(prompt.trim())
            append("\n\n")
            append("CURRENT EDITOR CONTEXT:\n")
            append(code.take(120_000))
        }

        val body = JSONObject().apply {
            put("model", model)
            put("input", input)
            put("store", true)

            put(
                "system_instruction",
                """
                You are Avescode Copilot, a high-performance mobile coding agent.
                Focus on software engineering.
                Diagnose the root cause first.
                Prefer minimal safe edits.
                When modifying code, return complete copy-pasteable code or a clear patch.
                Never invent files or APIs when the provided context is sufficient.
                Keep answers direct but technically precise.
                """.trimIndent()
            )

            put(
                "generation_config",
                JSONObject().apply {
                    put("thinking_level", level)
                }
            )

            previousInteractionId?.let {
                if (it.isNotBlank()) {
                    put("previous_interaction_id", it)
                }
            }
        }.toString()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/interactions")
            .addHeader("x-goog-api-key", key)
            .addHeader("Content-Type", "application/json")
            .post(
                body.toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
            )
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return@use "Gemini HTTP ${response.code}: ${raw.take(700)}"
                }

                val json = JSONObject(raw)
                previousInteractionId =
                    json.optString("id").takeIf { it.isNotBlank() }

                extractOutput(json).ifBlank {
                    "Avescode Copilot tidak menerima output teks dari Gemini."
                }
            }
        }.getOrElse {
            "Copilot error: ${it.message ?: "unknown network error"}"
        }
    }
}
