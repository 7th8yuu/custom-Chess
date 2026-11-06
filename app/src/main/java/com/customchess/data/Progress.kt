package com.customchess.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Tiến độ của người chơi: điểm câu đố, chuỗi ngày chơi, bài học đã học, thành tích với bot. */
data class Progress(
    val puzzleRating: Int = 1000,
    val solved: Int = 0,
    val failed: Int = 0,
    val rushBest: Int = 0,
    val streak: Int = 0,
    val lastDay: Long = 0L,
    val days: List<Long> = emptyList(),
    val recent: List<String> = emptyList(),
    /** Mã bài học -> số bước đã hoàn thành. */
    val lessons: Map<String, Int> = emptyMap(),
    /** Mã bot -> số ván thắng. */
    val botWins: Map<String, Int> = emptyMap(),
    val played: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    /** Ngày (epoch day) đã giải câu đố hằng ngày. */
    val dailyDone: Long = 0L,
) {
    /** Ghi nhận hôm nay có hoạt động: cập nhật chuỗi ngày. */
    fun touch(day: Long = today()): Progress {
        if (lastDay == day) return this
        val s = if (lastDay == day - 1) streak + 1 else 1
        return copy(streak = s, lastDay = day, days = (days + day).distinct().takeLast(40))
    }

    /** Chuỗi hiện tại: về 0 nếu đã bỏ lỡ hơn một ngày. */
    fun streakNow(day: Long = today()): Int = if (lastDay >= day - 1) streak else 0

    fun afterPuzzle(puzzleId: String, puzzleRating: Int, ok: Boolean, rated: Boolean = true): Progress {
        val expected = 1.0 / (1.0 + 10.0.pow((puzzleRating - this.puzzleRating) / 400.0))
        val k = if (solved + failed < 30) 44.0 else 28.0
        val delta = if (rated) (k * ((if (ok) 1.0 else 0.0) - expected)).roundToInt() else 0
        return copy(
            puzzleRating = (this.puzzleRating + delta).coerceIn(100, 3200),
            solved = solved + if (ok) 1 else 0,
            failed = failed + if (ok) 0 else 1,
            recent = (recent + puzzleId).takeLast(80),
        ).touch()
    }

    fun afterGame(winnerIsMe: Boolean?, botId: String? = null): Progress {
        val p = copy(
            played = played + 1,
            wins = wins + if (winnerIsMe == true) 1 else 0,
            losses = losses + if (winnerIsMe == false) 1 else 0,
            draws = draws + if (winnerIsMe == null) 1 else 0,
        )
        val withBot = if (botId != null && winnerIsMe == true) p.copy(botWins = botWins + (botId to ((botWins[botId] ?: 0) + 1))) else p
        return withBot.touch()
    }

    fun toJson(): String = JSONObject().apply {
        put("pr", puzzleRating); put("sv", solved); put("fl", failed); put("rb", rushBest)
        put("st", streak); put("ld", lastDay); put("dd", dailyDone)
        put("days", JSONArray(days)); put("recent", JSONArray(recent))
        put("lessons", JSONObject(lessons.mapValues { it.value })); put("bw", JSONObject(botWins.mapValues { it.value }))
        put("pl", played); put("w", wins); put("d", draws); put("l", losses)
    }.toString()

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()

        fun fromJson(s: String): Progress {
            if (s.isBlank()) return Progress()
            return runCatching {
                val o = JSONObject(s)
                fun longs(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.optLong(it) } } ?: emptyList()
                fun strs(k: String) = o.optJSONArray(k)?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList()
                fun ints(k: String): Map<String, Int> = o.optJSONObject(k)?.let { j -> j.keys().asSequence().associateWith { j.optInt(it) } } ?: emptyMap()
                Progress(
                    puzzleRating = o.optInt("pr", 1000), solved = o.optInt("sv"), failed = o.optInt("fl"), rushBest = o.optInt("rb"),
                    streak = o.optInt("st"), lastDay = o.optLong("ld"), days = longs("days"), recent = strs("recent"),
                    lessons = ints("lessons"), botWins = ints("bw"),
                    played = o.optInt("pl"), wins = o.optInt("w"), draws = o.optInt("d"), losses = o.optInt("l"),
                    dailyDone = o.optLong("dd"),
                )
            }.getOrDefault(Progress())
        }
    }
}

/** Một ván đã chơi xong, lưu để xem lại và phân tích. */
data class SavedGame(
    val id: String,
    val time: Long,
    val mode: String,
    val white: String,
    val black: String,
    val whiteSub: String,
    val blackSub: String,
    /** Màu của mình (null = hai người chung máy). */
    val myWhite: Boolean?,
    /** Bên thắng: true trắng, false đen, null hòa. */
    val winnerWhite: Boolean?,
    val reason: String,
    val startFen: String,
    val moves: List<String>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("t", time); put("m", mode); put("w", white); put("b", black)
        put("ws", whiteSub); put("bs", blackSub)
        if (myWhite != null) put("me", myWhite)
        if (winnerWhite != null) put("win", winnerWhite)
        put("r", reason); put("fen", startFen); put("mv", moves.joinToString(" "))
    }

    /** Kết quả theo góc nhìn của mình: 1 thắng, 0 hòa, -1 thua. Hai người chung máy trả về null. */
    fun outcomeForMe(): Int? {
        val me = myWhite ?: return null
        return when (winnerWhite) { null -> 0; me -> 1; else -> -1 }
    }

    companion object {
        fun fromJson(o: JSONObject): SavedGame = SavedGame(
            id = o.optString("id"), time = o.optLong("t"), mode = o.optString("m"),
            white = o.optString("w"), black = o.optString("b"), whiteSub = o.optString("ws"), blackSub = o.optString("bs"),
            myWhite = if (o.has("me")) o.optBoolean("me") else null,
            winnerWhite = if (o.has("win")) o.optBoolean("win") else null,
            reason = o.optString("r"), startFen = o.optString("fen"),
            moves = o.optString("mv").split(' ').filter { it.isNotBlank() },
        )
    }
}

/** Lịch sử ván lưu trong một file JSON ở bộ nhớ riêng, tối đa [MAX] ván gần nhất. */
class HistoryStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "history.json")

    fun load(): List<SavedGame> = runCatching {
        if (!file.exists()) emptyList()
        else {
            val a = JSONArray(file.readText())
            List(a.length()) { SavedGame.fromJson(a.getJSONObject(it)) }
        }
    }.getOrDefault(emptyList())

    fun save(list: List<SavedGame>) {
        runCatching {
            val a = JSONArray()
            list.take(MAX).forEach { a.put(it.toJson()) }
            file.writeText(a.toString())
        }
    }

    fun clear() { runCatching { file.delete() } }

    companion object { const val MAX = 300 }
}

fun clampRating(v: Int) = max(100, min(3200, v))
