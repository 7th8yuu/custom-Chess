package com.customchess.core

/** Nhóm tốc độ của Lichess (Bullet, Blitz, Rapid, Classical). Cờ qua thư tính riêng theo số ngày mỗi nước. */
enum class Speed(val label: String) { BULLET("Bullet"), BLITZ("Blitz"), RAPID("Rapid"), CLASSICAL("Classical") }

/** Một ô thời gian trong lưới "Xếp cặp nhanh": số phút ban đầu và số giây cộng thêm mỗi nước. */
data class TimeTile(val minutes: Int, val increment: Int) {
    val speed: Speed get() = Lobby.speed(minutes, increment)
    val label: String get() = Lobby.label(minutes, increment)
}

/**
 * Quy tắc sảnh Lichess dùng chung cho giao diện và kiểm tra đầu vào.
 * Lichess xếp loại theo thời gian ước tính = phút * 60 + 40 * số giây cộng thêm:
 * dưới 180 giây là Bullet, dưới 480 là Blitz, dưới 1500 là Rapid, còn lại là Classical.
 */
object Lobby {
    const val MAX_MINUTES = 180
    const val MAX_INCREMENT = 180
    const val MAX_RATING = 4000

    /** Số ngày mỗi nước mà Lichess cho phép khi đăng yêu cầu cờ qua thư. */
    val CORRESPONDENCE_DAYS = listOf(1, 2, 3, 5, 7, 10, 14)

    val TILES = listOf(
        TimeTile(1, 0), TimeTile(2, 1),
        TimeTile(3, 0), TimeTile(3, 2), TimeTile(5, 0), TimeTile(5, 3),
        TimeTile(10, 0), TimeTile(10, 5), TimeTile(15, 10),
        TimeTile(30, 0), TimeTile(30, 20),
    )

    fun estimateSeconds(minutes: Int, increment: Int): Int = minutes * 60 + 40 * increment

    fun speed(minutes: Int, increment: Int): Speed {
        val t = estimateSeconds(minutes, increment)
        return when {
            t < 180 -> Speed.BULLET
            t < 480 -> Speed.BLITZ
            t < 1500 -> Speed.RAPID
            else -> Speed.CLASSICAL
        }
    }

    /** Ghép trận ngẫu nhiên (seek) qua Board API chỉ cho Rapid trở lên. */
    fun seekAllowed(speed: Speed): Boolean = speed >= Speed.RAPID

    /** Thách đấu trực tiếp một người: Blitz trở lên (Bullet thì Lichess không cho ứng dụng ngoài). */
    fun challengeAllowed(speed: Speed): Boolean = speed >= Speed.BLITZ

    fun label(minutes: Int, increment: Int) = "$minutes + $increment"

    /** Đọc số nguyên dương từ ô nhập; rỗng hoặc sai thì trả [default]. */
    fun intOr(text: String, default: Int): Int = text.trim().toIntOrNull()?.takeIf { it >= 0 } ?: default

    /**
     * Kiểm tra thời gian tự chọn. Trả về thông báo lỗi tiếng Việt, hoặc null nếu hợp lệ.
     * [minutes] từ 1 đến 180, [increment] từ 0 đến 180.
     */
    fun checkClock(minutes: Int, increment: Int): String? = when {
        minutes < 1 || minutes > MAX_MINUTES -> "Số phút phải từ 1 đến $MAX_MINUTES"
        increment < 0 || increment > MAX_INCREMENT -> "Số giây cộng thêm phải từ 0 đến $MAX_INCREMENT"
        else -> null
    }

    /**
     * Khoảng điểm đối thủ, dạng "1500-1800" như Lichess yêu cầu.
     * Để trống cả hai ô thì trả null (không giới hạn). Sai thì ném [IllegalArgumentException] với thông báo tiếng Việt.
     */
    fun ratingRange(min: String, max: String): String? {
        val a = min.trim()
        val b = max.trim()
        if (a.isEmpty() && b.isEmpty()) return null
        if (a.isEmpty() || b.isEmpty()) throw IllegalArgumentException("Nhập đủ cả hai đầu khoảng điểm, hoặc để trống cả hai")
        val lo = a.toIntOrNull()
        val hi = b.toIntOrNull()
        if (lo == null || hi == null || lo < 0 || hi < 0) throw IllegalArgumentException("Khoảng điểm chỉ gồm chữ số")
        if (lo > MAX_RATING || hi > MAX_RATING) throw IllegalArgumentException("Điểm tối đa là $MAX_RATING")
        if (lo > hi) throw IllegalArgumentException("Điểm thấp nhất không được lớn hơn điểm cao nhất")
        return "$lo-$hi"
    }

    private val USERNAME = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{1,19}$")

    /** Tên tài khoản Lichess hợp lệ (2 đến 20 ký tự chữ, số, gạch dưới, gạch nối; bắt đầu bằng chữ hoặc số). */
    fun validUsername(name: String): Boolean = USERNAME.matches(name.trim())

    /** Thời gian còn lại cho một nước của ván cờ qua thư, ví dụ "2 ngày 3 giờ" hoặc "5 giờ". */
    fun leftText(seconds: Int): String {
        if (seconds <= 0) return ""
        val d = seconds / 86_400
        val h = (seconds % 86_400) / 3_600
        val m = (seconds % 3_600) / 60
        return when {
            d > 0 && h > 0 -> "$d ngày $h giờ"
            d > 0 -> "$d ngày"
            h > 0 -> "$h giờ"
            else -> "${maxOf(1, m)} phút"
        }
    }
}
