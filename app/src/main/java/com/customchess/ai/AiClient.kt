package com.customchess.ai

import com.customchess.core.Move
import com.customchess.core.MoveExtract
import com.customchess.core.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Ba hãng AI được hỗ trợ khi chơi online bằng API key của người dùng. */
enum class Provider(
    val label: String,
    val tagline: String,
    val defaultBase: String,
    val defaultModel: String,
    val keyUrl: String,
) {
    GEMINI(
        "Gemini", "Dễ lấy key nhất, có gói miễn phí",
        "https://generativelanguage.googleapis.com", "gemini-2.5-flash",
        "https://aistudio.google.com/apikey",
    ),
    OPENAI(
        "OpenAI", "Dùng được cả DeepSeek, Groq, OpenRouter... qua địa chỉ tùy chỉnh",
        "https://api.openai.com/v1", "gpt-4o-mini",
        "https://platform.openai.com/api-keys",
    ),
    CLAUDE(
        "Claude", "Sonnet 5.5 với effort thấp, trả lời nhanh",
        "https://api.anthropic.com", "claude-sonnet-5-5",
        "https://console.anthropic.com/settings/keys",
    );
}

data class AiConfig(val provider: Provider, val apiKey: String, val model: String, val baseUrl: String) {
    val ready: Boolean get() = apiKey.isNotBlank() && model.isNotBlank()
}

class AiException(message: String, val code: Int = 0) : Exception(message)

data class AiMove(val move: Move, val raw: String, val attempts: Int)

object AiClient {
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT =
        "You are a strong chess player in the middle of a game. You are given the current position as FEN, " +
            "the moves played so far, and the complete list of legal moves in UCI notation. " +
            "Choose the best move for the side to move. " +
            "Reply with ONLY that one move in UCI notation (for example e2e4 or e7e8q), with no explanation."

    // ------------------------------------------------------------------ nước đi

    /** Hỏi AI một nước đi; thử lại tối đa 3 lần nếu AI trả về nước không hợp lệ. */
    suspend fun chooseMove(cfg: AiConfig, pos: Position, movetext: String): AiMove {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) throw AiException("Không còn nước đi hợp lệ")
        var note = ""
        var lastRaw = ""
        for (attempt in 1..3) {
            val raw = chat(cfg, SYSTEM_PROMPT, userPrompt(pos, movetext, legal, note))
            lastRaw = raw
            val mv = extractMove(pos, legal, raw)
            if (mv != null) return AiMove(mv, raw, attempt)
            note = "Your previous reply \"${raw.take(80).replace('\n', ' ')}\" was not a legal move. " +
                "Choose exactly one move from the legal list and answer with that UCI move only."
        }
        throw AiException("AI không trả về nước đi hợp lệ: \"${lastRaw.take(60).replace('\n', ' ')}\"")
    }

    private fun userPrompt(pos: Position, movetext: String, legal: List<Move>, note: String): String {
        val side = if (pos.whiteToMove) "White" else "Black"
        return buildString {
            append("You play $side.\n")
            append("FEN: ${pos.toFen()}\n")
            append("Moves so far: ${movetext.ifEmpty { "(none)" }}\n")
            append("Legal moves (UCI): ${legal.joinToString(" ") { it.uci() }}\n")
            if (note.isNotEmpty()) append(note).append('\n')
            append("Reply with only your chosen move in UCI notation.")
        }
    }

    fun extractMove(pos: Position, legal: List<Move>, text: String): Move? = MoveExtract.fromReply(pos, legal, text)

    // ------------------------------------------------------------------ gọi API

    suspend fun chat(cfg: AiConfig, system: String, user: String): String {
        if (cfg.apiKey.isBlank()) throw AiException("Chưa nhập API key")
        return try {
            parseReply(cfg.provider, execute(buildChatRequest(cfg, system, user, withEffort = true)))
        } catch (e: AiException) {
            // Một số model Claude (đời cũ) không hỗ trợ effort: thử lại không có effort.
            if (cfg.provider == Provider.CLAUDE && e.code == 400 && e.message.orEmpty().contains("effort", true)) {
                parseReply(cfg.provider, execute(buildChatRequest(cfg, system, user, withEffort = false)))
            } else throw e
        }
    }

    private fun buildChatRequest(cfg: AiConfig, system: String, user: String, withEffort: Boolean): Request {
        val base = cfg.baseUrl.trim().ifEmpty { cfg.provider.defaultBase }.trimEnd('/')
        return when (cfg.provider) {
            Provider.OPENAI -> {
                val body = JSONObject()
                    .put("model", cfg.model.trim())
                    .put(
                        "messages",
                        JSONArray()
                            .put(JSONObject().put("role", "system").put("content", system))
                            .put(JSONObject().put("role", "user").put("content", user)),
                    )
                Request.Builder().url("$base/chat/completions")
                    .header("Authorization", "Bearer ${cfg.apiKey.trim()}")
                    .post(body.toString().toRequestBody(JSON)).build()
            }
            Provider.CLAUDE -> {
                val body = JSONObject()
                    .put("model", cfg.model.trim())
                    .put("max_tokens", 2048)
                    .put("system", system)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
                if (withEffort) body.put("output_config", JSONObject().put("effort", "low"))
                Request.Builder().url("$base/v1/messages")
                    .header("x-api-key", cfg.apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .post(body.toString().toRequestBody(JSON)).build()
            }
            Provider.GEMINI -> {
                val body = JSONObject()
                    .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put(
                        "contents",
                        JSONArray().put(
                            JSONObject().put("role", "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", user))),
                        ),
                    )
                val model = cfg.model.trim().removePrefix("models/")
                Request.Builder().url("$base/v1beta/models/$model:generateContent")
                    .header("x-goog-api-key", cfg.apiKey.trim())
                    .post(body.toString().toRequestBody(JSON)).build()
            }
        }
    }

    private fun parseReply(provider: Provider, body: String): String {
        val j = try { JSONObject(body) } catch (e: Exception) { throw AiException("Phản hồi không đọc được") }
        val text = when (provider) {
            Provider.OPENAI -> j.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content").orEmpty()
            Provider.CLAUDE -> {
                val sb = StringBuilder()
                val arr = j.optJSONArray("content")
                if (arr != null) for (i in 0 until arr.length()) {
                    val part = arr.optJSONObject(i) ?: continue
                    if (part.optString("type") == "text") sb.append(part.optString("text"))
                }
                sb.toString()
            }
            Provider.GEMINI -> {
                val sb = StringBuilder()
                val parts = j.optJSONArray("candidates")?.optJSONObject(0)
                    ?.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null) for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    if (!part.optBoolean("thought", false)) sb.append(part.optString("text"))
                }
                sb.toString()
            }
        }
        if (text.isBlank()) throw AiException("AI trả về nội dung trống")
        return text.trim()
    }

    // ------------------------------------------------------------------ danh sách model

    suspend fun listModels(cfg: AiConfig): List<String> {
        if (cfg.apiKey.isBlank()) throw AiException("Chưa nhập API key")
        val base = cfg.baseUrl.trim().ifEmpty { cfg.provider.defaultBase }.trimEnd('/')
        val key = cfg.apiKey.trim()
        val req = when (cfg.provider) {
            Provider.OPENAI -> Request.Builder().url("$base/models").header("Authorization", "Bearer $key").build()
            Provider.CLAUDE -> Request.Builder().url("$base/v1/models?limit=100")
                .header("x-api-key", key).header("anthropic-version", "2023-06-01").build()
            Provider.GEMINI -> Request.Builder().url("$base/v1beta/models?pageSize=200")
                .header("x-goog-api-key", key).build()
        }
        val j = try { JSONObject(execute(req)) } catch (e: AiException) { throw e } catch (e: Exception) {
            throw AiException("Phản hồi không đọc được")
        }
        val out = ArrayList<String>()
        when (cfg.provider) {
            Provider.GEMINI -> {
                val arr = j.optJSONArray("models") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val m = arr.optJSONObject(i) ?: continue
                    val methods = m.optJSONArray("supportedGenerationMethods")
                    var ok = methods == null
                    if (methods != null) for (k in 0 until methods.length()) if (methods.optString(k) == "generateContent") ok = true
                    if (ok) out.add(m.optString("name").removePrefix("models/"))
                }
            }
            else -> {
                val arr = j.optJSONArray("data") ?: JSONArray()
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            }
        }
        return out.distinct().sorted()
    }

    /** Gửi một câu ngắn để kiểm tra key và model. Trả về câu trả lời của AI. */
    suspend fun test(cfg: AiConfig): String = chat(cfg, "You are a connectivity test. Reply with the single word OK.", "ping")

    // ------------------------------------------------------------------ HTTP

    private suspend fun execute(req: Request): String = withContext(Dispatchers.IO) {
        val resp = try {
            http.newCall(req).await()
        } catch (e: IOException) {
            throw AiException("Không kết nối được tới máy chủ: ${e.message ?: "lỗi mạng"}")
        }
        resp.use {
            val text = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw AiException(errorMessage(it.code, text), it.code)
            text
        }
    }

    private fun errorMessage(code: Int, body: String): String {
        val msg = try {
            when (val e = JSONObject(body).opt("error")) {
                is JSONObject -> e.optString("message")
                is String -> e
                else -> ""
            }
        } catch (_: Exception) { "" }
        val hint = when (code) {
            401, 403 -> " (kiểm tra lại API key)"
            404 -> " (model không tồn tại, hãy bấm \"Tải danh sách model\")"
            429 -> " (hết hạn mức hoặc gọi quá nhanh)"
            else -> ""
        }
        return "Lỗi $code$hint" + if (msg.isNotBlank()) ": ${msg.take(200)}" else ""
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }
}
