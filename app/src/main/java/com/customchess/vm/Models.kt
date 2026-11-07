package com.customchess.vm

import com.customchess.core.GameResult
import com.customchess.core.Move
import com.customchess.core.Position
import com.customchess.net.OngoingGame

enum class Screen {
    LOGIN, HOME, PLAY, PUZZLES, LEARN, PROFILE,
    ENGINE_SETUP, API_SETUP, AI_SETTINGS, FRIENDS, LOCAL_SETUP,
    WIFI_HOST, WIFI_JOIN, BT_HOST, BT_JOIN, ONLINE, SETTINGS, APPEARANCE, GAME,
    BOTS, HISTORY, REVIEW, PUZZLE, LESSON, TOURNAMENTS,
}

/** Các màn hình gốc có thanh điều hướng dưới. */
val TAB_SCREENS = setOf(Screen.HOME, Screen.PLAY, Screen.PUZZLES, Screen.LEARN, Screen.PROFILE)

enum class Mode { ENGINE, API, LOCAL, LINK, LICHESS, BOT }

/** Thao tác với bản sao lưu trên Google Drive. */
enum class DriveOp { BACKUP, RESTORE }

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
    /** Nước vừa rồi có ăn quân không (cho âm thanh và hiệu ứng). */
    val lastCapture: Boolean = false,
    /** Điểm đánh giá theo quân trắng (centipawn) cho thanh đánh giá; null = chưa có. */
    val evalCp: Int? = null,
    /** Mã bot đang đấu (chế độ BOT). */
    val botId: String = "",
    /** Nước đang chờ xác nhận (khi bật "Xác nhận nước đi"), null nếu không có. */
    val pending: Move? = null,
    /** Bàn cờ xem trước khi đã đi nước đang chờ xác nhận. */
    val pendingBoard: IntArray? = null,
    val pendingSan: String = "",
    /** Ván cờ qua thư (Lichess): không có đồng hồ, thoát ra không bị tính bỏ cuộc. */
    val corr: Boolean = false,
    /** Nước đi trước (premove) đã đặt khi chưa tới lượt; tự đi khi đối thủ đi xong nếu còn hợp lệ. */
    val premove: Move? = null,
)

data class LichessUi(
    val connected: Boolean = false,
    val name: String = "",
    val busy: Boolean = false,
    val searching: Boolean = false,
    val error: String? = null,
    /** Điểm Lichess: blitz, rapid, classical (0 = chưa có hoặc chưa chơi đủ ván). */
    val ratings: List<Int> = listOf(0, 0, 0),
    /** Nhãn của lượt tìm đang chạy, ví dụ "10 + 0 · Giao hữu". */
    val searchLabel: String = "",
    /** Thông báo tích cực (không phải lỗi) cho màn sảnh. */
    val info: String? = null,
    /** Các ván đang chơi, gồm cả cờ qua thư. */
    val ongoing: List<OngoingGame> = emptyList(),
) {
    /** Ví dụ "Blitz 1520 · Rapid 1610"; rỗng nếu chưa có điểm nào. */
    val ratingText: String
        get() = listOf("Blitz", "Rapid", "Classical").zip(ratings)
            .filter { it.second > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }
}
