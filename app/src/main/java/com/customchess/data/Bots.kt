package com.customchess.data

import com.customchess.core.Engine
import com.customchess.core.Move
import com.customchess.core.Position
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Một bot đấu cờ. Điểm [rating] chỉ là ước lượng tương đối giữa các bot trong ứng dụng (không phải điểm Elo chuẩn).
 * [depth] là độ sâu tính nước, [noise] (centipawn) là độ "run tay": bot chọn nước có điểm cộng thêm nhiễu ngẫu nhiên,
 * nhiễu lớn thì hay đi sai. [noise] = 0 nghĩa là chơi hết sức bằng engine cấp [engineLevel].
 */
data class Bot(
    val id: String,
    val name: String,
    val emoji: String,
    val rating: Int,
    val tagline: String,
    val depth: Int,
    val noise: Int,
    val thinkMs: Long,
    val engineLevel: Int = 0,
)

val BOTS: List<Bot> = listOf(
    Bot("beti", "Bé Tí", "🐣", 250, "Mới tập đi quân, hay đi bừa.", 1, 700, 150),
    Bot("meo", "Mèo Con", "🐱", 400, "Thích đuổi theo quân của bạn.", 1, 450, 200),
    Bot("gau", "Gấu Mật", "🐻", 600, "Chậm mà chắc, đôi lúc quên bảo vệ.", 1, 300, 250),
    Bot("cu", "Cú Đêm", "🦉", 800, "Nhìn trước hai nước nhưng hay lơ đễnh.", 2, 220, 400),
    Bot("cao", "Cáo Tinh", "🦊", 1000, "Ranh mãnh, thích bẫy nhỏ.", 2, 140, 500),
    Bot("rua", "Rùa Thông Thái", "🐢", 1200, "Điềm đạm, ít khi sai nước đơn giản.", 3, 100, 800),
    Bot("ho", "Hổ Vằn", "🐯", 1400, "Tấn công mạnh tay.", 3, 60, 1000),
    Bot("rong", "Rồng Lửa", "🐉", 1600, "Tính xa, hiếm khi bỏ quân.", 4, 40, 1500),
    Bot("alpha", "Robot Alpha", "🤖", 1800, "Lạnh lùng và chính xác.", 4, 15, 1800),
    Bot("soi", "Sói Bạc", "🐺", 2000, "Đi săn từng sơ hở nhỏ.", 5, 8, 2500),
    Bot("phuong", "Phượng Hoàng", "🦅", 2200, "Hết sức bằng engine cấp cao.", 6, 0, 4000, 5),
    Bot("dai", "Đại Kiện Tướng", "👑", 2400, "Không nhượng bộ, nghĩ lâu nhất.", 6, 0, 6000, 5),
)

fun botById(id: String): Bot? = BOTS.firstOrNull { it.id == id }

object BotBrain {
    private fun gaussian(): Double {
        val u1 = Math.random().coerceAtLeast(1e-9)
        val u2 = Math.random()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }

    /** Chọn nước đi cho [bot] tại thế [pos]; null nếu hết nước. */
    fun choose(bot: Bot, pos: Position, history: Map<String, Int>, ply: Int, stop: () -> Boolean): Move? {
        val legal = pos.legalMoves()
        if (legal.isEmpty()) return null
        if (legal.size == 1) return legal[0]
        if (bot.noise <= 0) {
            return Engine(bot.engineLevel.coerceIn(1, 5), bot.depth, bot.thinkMs).bestMove(pos, history, stop)
        }
        val ranked = Engine(3, bot.depth, bot.thinkMs).rankMoves(pos, bot.depth, bot.thinkMs, stop)
        // vài nước đầu: bốc ngẫu nhiên trong 3 nước tốt nhất để khai cuộc mỗi ván một khác
        if (ply < 4) {
            val top = ranked.sortedByDescending { it.second }.take(3)
            if (top.isNotEmpty()) return top.random().first
        }
        var best: Move? = null
        var bestValue = Double.NEGATIVE_INFINITY
        for ((m, raw) in ranked) {
            // chiếu hết cũng chỉ là "điểm lớn" để bot yếu có thể bỏ lỡ
            val s = if (abs(raw) > Engine.MATE - 200) (if (raw > 0) 1500 else -1500) else raw.coerceIn(-1500, 1500)
            val v = s + gaussian() * bot.noise
            if (v > bestValue) { bestValue = v; best = m }
        }
        return best ?: legal.random()
    }
}
