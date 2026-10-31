package com.customchess.color

import kotlin.math.floor
import kotlin.math.roundToInt

/** Dải màu cùng hue và chroma, đổi độ sáng (tone 0..100). */
class Tonal(val hue: Double, val chroma: Double) {
    private val cache = HashMap<Int, Int>()
    fun tone(t: Int): Int = cache.getOrPut(t) { Hct.solve(hue, chroma, t.toDouble()) }
}

/** Các vai trò màu của Material 3 dưới dạng ARGB. */
data class Roles(
    val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int, val inversePrimary: Int,
    val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
    val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
    val error: Int, val onError: Int, val errorContainer: Int, val onErrorContainer: Int,
    val background: Int, val onBackground: Int, val surface: Int, val onSurface: Int,
    val surfaceVariant: Int, val onSurfaceVariant: Int, val outline: Int, val outlineVariant: Int,
    val inverseSurface: Int, val inverseOnSurface: Int,
    val surfaceDim: Int, val surfaceBright: Int,
    val surfaceContainerLowest: Int, val surfaceContainerLow: Int, val surfaceContainer: Int,
    val surfaceContainerHigh: Int, val surfaceContainerHighest: Int,
) {
    /** Dạng phẳng để nội suy màu khi chuyển giao diện. */
    fun toList(): List<Int> = listOf(
        primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary,
        secondary, onSecondary, secondaryContainer, onSecondaryContainer,
        tertiary, onTertiary, tertiaryContainer, onTertiaryContainer,
        error, onError, errorContainer, onErrorContainer,
        background, onBackground, surface, onSurface,
        surfaceVariant, onSurfaceVariant, outline, outlineVariant,
        inverseSurface, inverseOnSurface,
        surfaceDim, surfaceBright,
        surfaceContainerLowest, surfaceContainerLow, surfaceContainer,
        surfaceContainerHigh, surfaceContainerHighest,
    )

    companion object {
        const val SIZE = 34
        fun fromList(l: List<Int>): Roles {
            require(l.size == SIZE)
            var i = 0
            fun n() = l[i++]
            return Roles(
                n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(),
                n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(), n(),
            )
        }
    }
}

object Scheme {
    /**
     * Bảng màu Material 3 kiểu "Tonal Spot" sinh từ một màu hạt giống.
     * [muted] cho bảng màu gần trung tính (dùng cho bàn cờ màu than chì).
     */
    fun generate(seed: Int, dark: Boolean, muted: Boolean = false): Roles {
        val cam = Hct.cam(seed)
        val h = cam.hue
        val a1: Tonal; val a2: Tonal; val a3: Tonal; val n1: Tonal; val n2: Tonal
        if (muted) {
            a1 = Tonal(h, 10.0); a2 = Tonal(h, 8.0); a3 = Tonal(h + 60.0, 12.0)
            n1 = Tonal(h, 2.0); n2 = Tonal(h, 4.0)
        } else {
            // Nền và bề mặt hơi ngả về màu của ảnh (chroma 7/11, cao hơn mặc định 4/8 của Material) để cả ứng dụng
            // nhìn thấy được sự đổi màu chứ không chỉ các nút.
            a1 = Tonal(h, maxOf(48.0, cam.chroma)); a2 = Tonal(h, 16.0); a3 = Tonal(h + 60.0, 24.0)
            n1 = Tonal(h, 7.0); n2 = Tonal(h, 11.0)
        }
        val err = Tonal(25.0, 84.0)
        return if (dark) Roles(
            a1.tone(80), a1.tone(20), a1.tone(30), a1.tone(90), a1.tone(40),
            a2.tone(80), a2.tone(20), a2.tone(30), a2.tone(90),
            a3.tone(80), a3.tone(20), a3.tone(30), a3.tone(90),
            err.tone(80), err.tone(20), err.tone(30), err.tone(90),
            n1.tone(6), n1.tone(90), n1.tone(6), n1.tone(90),
            n2.tone(30), n2.tone(80), n2.tone(60), n2.tone(30),
            n1.tone(90), n1.tone(20),
            n1.tone(6), n1.tone(24),
            n1.tone(4), n1.tone(10), n1.tone(12), n1.tone(17), n1.tone(22),
        ) else Roles(
            a1.tone(40), a1.tone(100), a1.tone(90), a1.tone(10), a1.tone(80),
            a2.tone(40), a2.tone(100), a2.tone(90), a2.tone(10),
            a3.tone(40), a3.tone(100), a3.tone(90), a3.tone(10),
            err.tone(40), err.tone(100), err.tone(90), err.tone(10),
            n1.tone(98), n1.tone(10), n1.tone(98), n1.tone(10),
            n2.tone(90), n2.tone(30), n2.tone(50), n2.tone(80),
            n1.tone(20), n1.tone(95),
            n1.tone(87), n1.tone(98),
            n1.tone(100), n1.tone(96), n1.tone(94), n1.tone(92), n1.tone(90),
        )
    }
}

/** Chọn màu chủ đạo của một bức ảnh (cách chấm điểm của Material Color Utilities, rút gọn). */
object Dominant {
    const val FALLBACK = 0xFF4285F4.toInt()

    /** [pixels] là mảng ARGB của ảnh đã thu nhỏ (khoảng 100x100 là đủ). */
    fun seed(pixels: IntArray): Int {
        if (pixels.isEmpty()) return FALLBACK
        // gộp các màu gần nhau (5 bit mỗi kênh) để đếm số lượng
        val counts = HashMap<Int, Int>()
        for (p in pixels) {
            if ((p ushr 24) < 128) continue
            val q = 0xFF000000.toInt() or ((p and 0xF8F8F8) or 0x040404)
            counts[q] = (counts[q] ?: 0) + 1
        }
        if (counts.isEmpty()) return FALLBACK
        val colors = ArrayList<Int>(counts.size)
        val hues = ArrayList<Double>(counts.size)
        val chromas = ArrayList<Double>(counts.size)
        val huePopulation = IntArray(360)
        var total = 0
        for ((color, pop) in counts) {
            val cam = Hct.cam(color)
            colors.add(color); hues.add(cam.hue); chromas.add(cam.chroma)
            huePopulation[floor(cam.hue).toInt().coerceIn(0, 359)] += pop
            total += pop
        }
        val excited = DoubleArray(360)
        for (hue in 0 until 360) {
            val proportion = huePopulation[hue].toDouble() / total
            for (i in hue - 14 until hue + 16) excited[((i % 360) + 360) % 360] += proportion
        }
        var best = FALLBACK
        var bestScore = Double.NEGATIVE_INFINITY
        for (i in colors.indices) {
            val hue = ((hues[i].roundToInt() % 360) + 360) % 360
            val proportion = excited[hue]
            if (chromas[i] < 5.0 || proportion <= 0.01) continue
            val proportionScore = proportion * 100.0 * 0.7
            val weight = if (chromas[i] < 48.0) 0.1 else 0.3
            val score = proportionScore + (chromas[i] - 48.0) * weight
            if (score > bestScore) { bestScore = score; best = colors[i] }
        }
        return best
    }
}
