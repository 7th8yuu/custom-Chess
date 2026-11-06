package com.customchess.net

import com.customchess.data.Puzzle
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Một giải đấu Arena trên Lichess. [status]: 10 sắp diễn ra, 20 đang diễn ra, 30 đã kết thúc. */
data class Tournament(
    val id: String,
    val name: String,
    val minutes: Int,
    val clock: String,
    val players: Int,
    val startsAt: Long,
    val status: Int,
    val rated: Boolean,
    val variant: String,
    val perf: String,
)

/** Các API công khai của Lichess, không cần đăng nhập. Mọi hàm blocking: gọi từ luồng nền. */
object PublicApi {
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }

    private fun get(url: String): String {
        val req = Request.Builder().url(url).header("Accept", "application/json").header("User-Agent", "CustomChess-Android").build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw LichessException(if (r.code == 429) "Lichess đang giới hạn tốc độ, thử lại sau ít phút" else "Lichess trả về lỗi ${r.code}", r.code)
            return body
        }
    }

    /** Câu đố ngẫu nhiên từ Lichess. */
    fun puzzleNext(): Puzzle = Puzzle.fromLichess(get("https://lichess.org/api/puzzle/next")) ?: throw LichessException("Không đọc được câu đố từ Lichess")

    /** Câu đố trong ngày của Lichess. */
    fun puzzleDaily(): Puzzle = Puzzle.fromLichess(get("https://lichess.org/api/puzzle/daily")) ?: throw LichessException("Không đọc được câu đố hằng ngày")

    fun tournaments(): List<Tournament> {
        val o = JSONObject(get("https://lichess.org/api/tournament"))
        val out = ArrayList<Tournament>()
        for (group in listOf("started", "created")) {
            val a = o.optJSONArray(group) ?: continue
            for (i in 0 until a.length()) {
                val t = a.optJSONObject(i) ?: continue
                val clock = t.optJSONObject("clock")
                val limit = clock?.optInt("limit") ?: 0
                val inc = clock?.optInt("increment") ?: 0
                out.add(
                    Tournament(
                        id = t.optString("id"),
                        name = t.optString("fullName").ifBlank { "Giải Arena" },
                        minutes = t.optInt("minutes"),
                        clock = if (clock == null) "" else "${limit / 60}+$inc",
                        players = t.optInt("nbPlayers"),
                        startsAt = t.optLong("startsAt"),
                        status = t.optInt("status"),
                        rated = t.optBoolean("rated", true),
                        variant = t.optJSONObject("variant")?.optString("name").orEmpty(),
                        perf = t.optJSONObject("perf")?.optString("name").orEmpty(),
                    ),
                )
            }
        }
        return out.filter { it.id.isNotBlank() }
    }
}
