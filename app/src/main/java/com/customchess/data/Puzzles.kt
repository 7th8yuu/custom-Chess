package com.customchess.data

import android.content.Context
import com.customchess.core.Move
import com.customchess.core.Position
import org.json.JSONObject
import kotlin.math.abs

/**
 * Một câu đố. [moves] giống CSV của Lichess: nước đầu tiên là nước đối thủ vừa đi (hiện ra như thể ván đang diễn ra),
 * các nước sau là lời giải xen kẽ (người giải, đối thủ, người giải, ...). [fen] là thế TRƯỚC nước đầu tiên.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val moves: List<String>,
    val rating: Int,
    val themes: List<String>,
    /** "bundle" (có sẵn trong ứng dụng) hoặc "lichess". */
    val source: String = "bundle",
) {
    val solution: List<String> get() = moves.drop(1)

    /** Thế sau nước đối thủ vừa đi: lúc người giải bắt đầu. */
    fun start(): Position? {
        val p = Position.fromFen(fen) ?: return null
        val m = p.parseMove(moves.firstOrNull() ?: return null) ?: return null
        return p.apply(m)
    }

    fun setupMove(): Move? = Position.fromFen(fen)?.let { p -> moves.firstOrNull()?.let { p.parseMove(it) } }

    /** Người giải cầm quân trắng? */
    fun solverIsWhite(): Boolean = start()?.whiteToMove ?: true

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("fen", fen); put("moves", moves.joinToString(" ")); put("rating", rating)
        put("themes", themes.joinToString(" ")); put("src", source)
    }

    companion object {
        fun fromJson(o: JSONObject): Puzzle = Puzzle(
            o.optString("id"), o.optString("fen"), o.optString("moves").split(' ').filter { it.isNotBlank() },
            o.optInt("rating", 1200), o.optString("themes").split(' ').filter { it.isNotBlank() }, o.optString("src", "bundle"),
        )

        /** Dòng "id|fen|moves|rating|themes". */
        fun parseLine(line: String): Puzzle? {
            val f = line.trim().split('|')
            if (f.size < 5) return null
            val moves = f[2].split(' ').filter { it.isNotBlank() }
            if (moves.size < 2) return null
            return Puzzle(f[0], f[1], moves, f[3].toIntOrNull() ?: 1200, f[4].split(' ').filter { it.isNotBlank() })
        }

        /**
         * Câu đố từ API Lichess (/api/puzzle/next hoặc /daily). PGN gồm các nước SAN tới hết nước đối thủ vừa đi,
         * lời giải là danh sách UCI bắt đầu bằng nước của người giải.
         */
        fun fromLichess(json: String): Puzzle? = runCatching {
            val o = JSONObject(json)
            val pz = o.getJSONObject("puzzle")
            val pgn = o.getJSONObject("game").getString("pgn")
            val tokens = pgn.replace(Regex("\\{[^}]*\\}"), " ").split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.endsWith(".") && !it[0].isDigit() || it.length > 1 && it[0].isDigit() && !it.contains('.') && it.any { c -> c.isLetter() } }
            // lọc số thứ tự nước "12." và "12..." bị lẫn
            val sans = tokens.filter { !Regex("^\\d+\\.+$").matches(it) && it != "1-0" && it != "0-1" && it != "1/2-1/2" && it != "*" }
            if (sans.isEmpty()) return@runCatching null
            var pos = Position.fromFen(com.customchess.core.START_FEN) ?: return@runCatching null
            val ucis = ArrayList<String>()
            val fens = ArrayList<String>()
            for (s in sans) {
                val m = pos.parseMove(s) ?: return@runCatching null
                fens.add(pos.toFen())
                ucis.add(m.uci())
                pos = pos.apply(m)
            }
            val sol = pz.getJSONArray("solution")
            val moves = ArrayList<String>()
            moves.add(ucis.last())
            for (i in 0 until sol.length()) moves.add(sol.getString(i))
            val th = pz.optJSONArray("themes")
            Puzzle(
                id = pz.getString("id"), fen = fens.last(), moves = moves, rating = pz.optInt("rating", 1500),
                themes = if (th == null) emptyList() else List(th.length()) { th.getString(it) }, source = "lichess",
            )
        }.getOrNull()
    }
}

/** Bộ câu đố có sẵn, đọc từ assets/puzzles.txt (sinh offline bằng tools/PuzzleGen.kt). */
object PuzzleBank {
    @Volatile private var cache: List<Puzzle>? = null

    fun all(context: Context): List<Puzzle> {
        cache?.let { return it }
        val list = runCatching {
            context.applicationContext.assets.open("puzzles.txt").bufferedReader().useLines { seq -> seq.mapNotNull { Puzzle.parseLine(it) }.toList() }
        }.getOrDefault(emptyList())
        cache = list
        return list
    }

    /** Câu hằng ngày dự phòng khi không có mạng: cố định theo ngày. */
    fun dailyFallback(context: Context, day: Long): Puzzle? {
        val l = all(context)
        if (l.isEmpty()) return null
        val mid = l.filter { it.rating in 900..1600 }.ifEmpty { l }
        return mid[(abs(day * 2654435761L) % mid.size).toInt()]
    }
}

/** Nhóm chủ đề để lọc, kèm tên hiển thị. */
data class PuzzleTheme(val key: String, val label: String)

val PUZZLE_FILTERS = listOf(
    PuzzleTheme("", "Tất cả"),
    PuzzleTheme("mateIn1", "Chiếu hết 1 nước"),
    PuzzleTheme("mateIn2", "Chiếu hết 2 nước"),
    PuzzleTheme("mateIn3", "Chiếu hết 3 nước"),
    PuzzleTheme("fork", "Đòn chĩa"),
    PuzzleTheme("hangingPiece", "Quân bị treo"),
    PuzzleTheme("sacrifice", "Hy sinh"),
    PuzzleTheme("backRankMate", "Chiếu hết hàng cuối"),
    PuzzleTheme("promotion", "Phong cấp"),
    PuzzleTheme("endgame", "Tàn cuộc"),
    PuzzleTheme("opening", "Khai cuộc"),
)

fun themeLabel(key: String): String = when (key) {
    "mate" -> "Chiếu hết"
    "mateIn1" -> "Chiếu hết 1 nước"
    "mateIn2" -> "Chiếu hết 2 nước"
    "mateIn3" -> "Chiếu hết 3 nước"
    "mateIn4", "mateIn5" -> "Chiếu hết dài"
    "fork" -> "Đòn chĩa"
    "hangingPiece" -> "Quân bị treo"
    "sacrifice" -> "Hy sinh"
    "backRankMate" -> "Chiếu hết hàng cuối"
    "smotheredMate" -> "Chiếu hết nghẹt"
    "promotion" -> "Phong cấp"
    "endgame" -> "Tàn cuộc"
    "middlegame" -> "Trung cuộc"
    "opening" -> "Khai cuộc"
    "crushing" -> "Thắng thế lớn"
    "advantage" -> "Giành lợi thế"
    "short" -> "Ngắn"
    "long" -> "Dài"
    "oneMove" -> "Một nước"
    "quietMove" -> "Nước yên lặng"
    "check" -> "Chiếu"
    "pin" -> "Ghim"
    "skewer" -> "Xiên"
    "discoveredAttack" -> "Tấn công mở"
    "deflection" -> "Lôi kéo"
    "attraction" -> "Dụ"
    "zugzwang" -> "Zugzwang"
    "capturingDefender" -> "Bắt quân bảo vệ"
    "trappedPiece" -> "Quân bị nhốt"
    "exposedKing" -> "Vua trơ trụi"
    "kingsideAttack" -> "Tấn công cánh vua"
    "queensideAttack" -> "Tấn công cánh hậu"
    "defensiveMove" -> "Phòng thủ"
    "intermezzo" -> "Nước xen"
    "veryLong" -> "Rất dài"
    "master", "masterVsMaster", "superGM" -> "Ván cao thủ"
    else -> key
}
