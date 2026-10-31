package com.customchess.net

import android.net.Uri
import android.util.Base64
import okhttp3.Call
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class LichessException(message: String, val code: Int = 0) : Exception(message)

fun interface Cancelable {
    fun cancel()
}

/** [ratings] = blitz, rapid, classical; 0 nếu chưa chơi ván tính điểm nào ở thể loại đó. */
class LichessProfile(val name: String, val ratings: List<Int>)

fun parseProfile(json: String): LichessProfile {
    val o = JSONObject(json)
    val perfs = o.optJSONObject("perfs")
    fun rating(key: String): Int {
        val p = perfs?.optJSONObject(key) ?: return 0
        return if (p.optInt("games", 0) > 0) p.optInt("rating", 0) else 0
    }
    val name = o.optString("username").ifBlank { o.optString("id") }
    return LichessProfile(name, listOf(rating("blitz"), rating("rapid"), rating("classical")))
}

/**
 * Ghép trận online qua Lichess Board API (miễn phí, dành cho ứng dụng bên thứ ba).
 * Chess.com không có API để chơi từ ứng dụng ngoài, nên Lichess là lựa chọn thay thế.
 * Mọi hàm blocking: gọi từ luồng nền.
 */
class LichessClient(private val token: String) {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // luồng sự kiện giữ kết nối rất lâu
        .retryOnConnectionFailure(true)
        .build()

    private fun req(path: String) = Request.Builder().url("$BASE$path")
        .header("Authorization", "Bearer $token")
        .header("User-Agent", "CustomChess-Android")

    private fun fail(resp: Response): LichessException {
        val text = try { resp.body?.string().orEmpty() } catch (_: Exception) { "" }
        val msg = try { JSONObject(text).optString("error").ifBlank { text } } catch (_: Exception) { text }
        val hint = when (resp.code) {
            401 -> "Token không hợp lệ hoặc đã hết hạn"
            403 -> "Token thiếu quyền board:play, hoặc tài khoản không dùng được Board API"
            429 -> "Gọi quá nhanh, hãy thử lại sau ít phút"
            else -> "Lỗi ${resp.code}"
        }
        return LichessException(if (msg.isNotBlank()) "$hint: ${msg.take(160)}" else hint, resp.code)
    }

    /** Hồ sơ của tài khoản đang đăng nhập: tên và điểm xếp hạng (cũng dùng để kiểm tra token còn hiệu lực). */
    fun profile(): LichessProfile {
        http.newCall(req("/api/account").get().build()).execute().use { r ->
            if (!r.isSuccessful) throw fail(r)
            return parseProfile(r.body?.string().orEmpty())
        }
    }

    /** Luồng sự kiện chung của tài khoản (gameStart, gameFinish...). */
    fun streamEvents(onEvent: (JSONObject) -> Unit, onEnd: (Throwable?) -> Unit): Cancelable =
        stream(req("/api/stream/event").get().build(), onEvent, onEnd)

    /** Luồng trạng thái một ván (gameFull, gameState, chatLine...). */
    fun streamGame(gameId: String, onEvent: (JSONObject) -> Unit, onEnd: (Throwable?) -> Unit): Cancelable =
        stream(req("/api/board/game/stream/$gameId").get().build(), onEvent, onEnd)

    /**
     * Tạo yêu cầu tìm đối thủ. Phải giữ kết nối mở cho tới khi có người nhận;
     * hủy [Cancelable] để thôi tìm.
     */
    fun seek(minutes: Int, increment: Int, rated: Boolean, onEnd: (Throwable?) -> Unit): Cancelable {
        val body = FormBody.Builder()
            .add("rated", rated.toString())
            .add("time", minutes.toString())
            .add("increment", increment.toString())
            .add("variant", "standard")
            .add("color", "random")
            .build()
        return stream(req("/api/board/seek").post(body).build(), { }, onEnd)
    }

    fun move(gameId: String, uci: String) = post("/api/board/game/$gameId/move/$uci")
    fun resign(gameId: String) = post("/api/board/game/$gameId/resign")
    fun abort(gameId: String) = post("/api/board/game/$gameId/abort")
    fun draw(gameId: String, accept: Boolean) = post("/api/board/game/$gameId/draw/${if (accept) "yes" else "no"}")

    private fun post(path: String) {
        http.newCall(req(path).post(FormBody.Builder().build()).build()).execute().use { r ->
            if (!r.isSuccessful) throw fail(r)
        }
    }

    private fun stream(request: Request, onEvent: (JSONObject) -> Unit, onEnd: (Throwable?) -> Unit): Cancelable {
        val call: Call = http.newCall(request)
        thread(isDaemon = true, name = "lichess-stream") {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        onEnd(fail(resp))
                        return@thread
                    }
                    val source = resp.body?.source()
                    if (source != null) {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.isBlank()) continue
                            try { onEvent(JSONObject(line)) } catch (_: org.json.JSONException) { }
                        }
                    }
                }
                onEnd(null)
            } catch (e: IOException) {
                onEnd(if (call.isCanceled()) null else e)
            } catch (e: Exception) {
                onEnd(e)
            }
        }
        return Cancelable { call.cancel() }
    }

    companion object {
        const val BASE = "https://lichess.org"
        const val CLIENT_ID = "customchess-android"
        const val REDIRECT = "customchess://oauth"
        const val SCOPE = "board:play"

        private fun b64url(bytes: ByteArray) =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        fun randomString(bytes: Int = 48): String {
            val b = ByteArray(bytes)
            SecureRandom().nextBytes(b)
            return b64url(b)
        }

        fun challenge(verifier: String): String =
            b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        /** Địa chỉ mở trong trình duyệt để người dùng cấp quyền (PKCE, không cần đăng ký ứng dụng). */
        fun authorizeUrl(state: String, verifier: String): String =
            "$BASE/oauth?response_type=code" +
                "&client_id=${Uri.encode(CLIENT_ID)}" +
                "&redirect_uri=${Uri.encode(REDIRECT)}" +
                "&scope=${Uri.encode(SCOPE)}" +
                "&code_challenge_method=S256" +
                "&code_challenge=${challenge(verifier)}" +
                "&state=$state"

        /** Đổi mã xác thực lấy token (blocking). */
        fun exchangeCode(code: String, verifier: String): String {
            val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
            val form = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", verifier)
                .add("redirect_uri", REDIRECT)
                .add("client_id", CLIENT_ID)
                .build()
            client.newCall(Request.Builder().url("$BASE/api/token").post(form).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw LichessException("Không đổi được mã đăng nhập (${r.code})", r.code)
                val token = JSONObject(text).optString("access_token")
                if (token.isBlank()) throw LichessException("Lichess không trả về token")
                return token
            }
        }
    }
}
