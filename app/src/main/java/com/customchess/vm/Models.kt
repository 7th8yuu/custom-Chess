package com.customchess.vm

import com.customchess.core.GameResult
import com.customchess.core.Move
import com.customchess.core.Position

enum class Screen {
    LOGIN, HOME, ENGINE_SETUP, API_SETUP, AI_SETTINGS, FRIENDS, LOCAL_SETUP,
    WIFI_HOST, WIFI_JOIN, BT_HOST, BT_JOIN, ONLINE, SETTINGS, APPEARANCE, GAME,
}

enum class Mode { ENGINE, API, LOCAL, LINK, LICHESS }

/** baseSec = 0 nghĩa là không giới hạn thời gian. */
data class TimePreset(val label: String, val baseSec: Int, val incSec: Int) {
    val unlimited: Boolean get() = baseSec == 0
}

val TIME_PRESETS = listOf(
    TimePreset("Không giới hạn", 0, 0),
    TimePreset("3 + 2", 180, 2),
    TimePreset("5 + 0", 300, 0),
    TimePreset("10 + 0", 600, 0),
    TimePreset("15 + 10", 900, 10),
)

/** Các mức thời gian cho ghép trận Lichess (Board API chỉ chơi từ blitz trở lên). */
val ONLINE_PRESETS = listOf(
    TimePreset("3 + 2", 180, 2),
    TimePreset("5 + 3", 300, 3),
    TimePreset("10 + 0", 600, 0),
    TimePreset("15 + 10", 900, 10),
)

fun levelName(level: Int) = when (level) {
    1 -> "Tập sự"; 2 -> "Dễ"; 3 -> "Vừa"; 4 -> "Khó"; else -> "Cao thủ"
}

fun levelHint(level: Int) = when (level) {
    1 -> "Hay đi bừa, hợp người mới"
    2 -> "Nhìn trước 2 nước"
    3 -> "Nhìn trước 3 nước, đủ khó với người chơi giải trí"
    4 -> "Nhìn trước 4 nước, mất vài giây mỗi nước"
    else -> "Nhìn sâu nhất, nghĩ tới 6 giây mỗi nước"
}

data class GameUi(
    val active: Boolean = false,
    val mode: Mode = Mode.LOCAL,
    val board: IntArray = Position.start().board,
    val whiteToMove: Boolean = true,
    val lastMove: Move? = null,
    val checkSquare: Int = -1,
    val selected: Int = -1,
    val targets: Set<Int> = emptySet(),
    val flipped: Boolean = false,
    /** null = hai người chơi chung một máy. */
    val myWhite: Boolean? = true,
    val sans: List<String> = emptyList(),
    val plyCount: Int = 0,
    val thinking: Boolean = false,
    val result: GameResult? = null,
    val whiteName: String = "",
    val blackName: String = "",
    val whiteSub: String = "",
    val blackSub: String = "",
    val timed: Boolean = false,
    /** Đối thủ đang xin hòa. */
    val drawOffered: Boolean = false,
    /** Mình đã gửi lời xin hòa. */
    val drawSent: Boolean = false,
    val promotion: Pair<Int, Int>? = null,
    val canUndo: Boolean = false,
    val notice: String? = null,
    val aiError: String? = null,
    val disconnected: Boolean = false,
    /** Tăng mỗi nước đi, để giao diện rung nhẹ. */
    val moveTick: Int = 0,
)

data class LichessUi(
    val connected: Boolean = false,
    val name: String = "",
    val busy: Boolean = false,
    val searching: Boolean = false,
    val error: String? = null,
    /** Điểm Lichess: blitz, rapid, classical (0 = chưa có hoặc chưa chơi đủ ván). */
    val ratings: List<Int> = listOf(0, 0, 0),
) {
    /** Ví dụ "Blitz 1520 · Rapid 1610"; rỗng nếu chưa có điểm nào. */
    val ratingText: String
        get() = listOf("Blitz", "Rapid", "Classical").zip(ratings)
            .filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }
}
