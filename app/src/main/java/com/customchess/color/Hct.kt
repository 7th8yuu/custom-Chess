package com.customchess.color

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Không gian màu HCT (hue, chroma, tone) dùng trong Material 3: màu cảm nhận theo CAM16, độ sáng theo L*.
 * Viết thuần Kotlin (không phụ thuộc Android) để kiểm thử được trên máy tính.
 */
object Hct {
    private const val E = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    class Cam(val hue: Double, val chroma: Double, val j: Double)

    // ---- điều kiện xem: sRGB, nền xám L*=50, ánh sáng D65 (mặc định của Material)
    private val white = doubleArrayOf(95.047, 100.0, 108.883)
    private val adaptingLuminance = 200.0 / PI * yFromLstar(50.0) / 100.0
    private val n = yFromLstar(50.0) / white[1]
    private val c = 0.69
    private val nc = 1.0
    private val z = 1.48 + sqrt(n)
    private val nbb = 0.725 / n.pow(0.2)
    private val ncb = nbb
    private val rgbD = DoubleArray(3)
    private val fl: Double
    private val flRoot: Double
    private val aw: Double

    init {
        val rW = white[0] * 0.401288 + white[1] * 0.650173 + white[2] * -0.051461
        val gW = white[0] * -0.250268 + white[1] * 1.204414 + white[2] * 0.045854
        val bW = white[0] * -0.002079 + white[1] * 0.048952 + white[2] * 0.953127
        val f = 1.0
        val d = (f * (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0))).coerceIn(0.0, 1.0)
        rgbD[0] = d * (100.0 / rW) + 1.0 - d
        rgbD[1] = d * (100.0 / gW) + 1.0 - d
        rgbD[2] = d * (100.0 / bW) + 1.0 - d
        val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
        val k4 = k * k * k * k
        val k4F = 1.0 - k4
        fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * Math.cbrt(5.0 * adaptingLuminance)
        flRoot = fl.pow(0.25)
        val rA = (fl * rgbD[0] * rW / 100.0).pow(0.42).let { 400.0 * it / (it + 27.13) }
        val gA = (fl * rgbD[1] * gW / 100.0).pow(0.42).let { 400.0 * it / (it + 27.13) }
        val bA = (fl * rgbD[2] * bW / 100.0).pow(0.42).let { 400.0 * it / (it + 27.13) }
        aw = (40.0 * rA + 20.0 * gA + bA) / 20.0 * nbb
    }

    // ---- chuyển đổi cơ bản
    fun linearized(channel: Int): Double {
        val v = channel / 255.0
        return (if (v <= 0.040449936) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)) * 100.0
    }

    fun delinearized(linear: Double): Int {
        val v = linear / 100.0
        val d = if (v <= 0.0031308) v * 12.92 else 1.055 * v.pow(1.0 / 2.4) - 0.055
        return (d * 255.0).roundToInt().coerceIn(0, 255)
    }

    fun yFromLstar(lstar: Double): Double {
        val ft = (lstar + 16.0) / 116.0
        val ft3 = ft * ft * ft
        return 100.0 * (if (ft3 > E) ft3 else (116.0 * ft - 16.0) / KAPPA)
    }

    fun lstarFromY(y: Double): Double {
        val t = y / 100.0
        val f = if (t > E) Math.cbrt(t) else (KAPPA * t + 16.0) / 116.0
        return 116.0 * f - 16.0
    }

    private fun argb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    fun grayArgb(tone: Double): Int {
        val v = delinearized(yFromLstar(tone))
        return argb(v, v, v)
    }

    fun toneOf(argb: Int): Double {
        val y = 0.2126 * linearized((argb shr 16) and 255) +
            0.7152 * linearized((argb shr 8) and 255) +
            0.0722 * linearized(argb and 255)
        return lstarFromY(y)
    }

    private fun adapt(d: Double): Double {
        val f = (fl * abs(d) / 100.0).pow(0.42)
        return sign(d) * 400.0 * f / (f + 27.13)
    }

    fun cam(argb: Int): Cam {
        val r = linearized((argb shr 16) and 255)
        val g = linearized((argb shr 8) and 255)
        val b = linearized(argb and 255)
        val x = 0.41233895 * r + 0.35762064 * g + 0.18051042 * b
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val zz = 0.01932141 * r + 0.11916382 * g + 0.95034478 * b
        val rC = 0.401288 * x + 0.650173 * y - 0.051461 * zz
        val gC = -0.250268 * x + 1.204414 * y + 0.045854 * zz
        val bC = -0.002079 * x + 0.048952 * y + 0.953127 * zz
        val rA = adapt(rgbD[0] * rC)
        val gA = adapt(rgbD[1] * gC)
        val bA = adapt(rgbD[2] * bC)
        val a = (11.0 * rA + -12.0 * gA + bA) / 11.0
        val bb = (rA + gA - 2.0 * bA) / 9.0
        val u = (20.0 * rA + 20.0 * gA + 21.0 * bA) / 20.0
        val p2 = (40.0 * rA + 20.0 * gA + bA) / 20.0
        var hue = Math.toDegrees(atan2(bb, a))
        if (hue < 0) hue += 360.0 else if (hue >= 360.0) hue -= 360.0
        val ac = p2 * nbb
        val j = 100.0 * (ac / aw).pow(c * z)
        val huePrime = if (hue < 20.14) hue + 360.0 else hue
        val eHue = 0.25 * (cos(Math.toRadians(huePrime) + 2.0) + 3.8)
        val p1 = 50000.0 / 13.0 * eHue * nc * ncb
        val t = p1 * hypot(a, bb) / (u + 0.305)
        val alpha = t.pow(0.9) * (1.64 - 0.29.pow(n)).pow(0.73)
        val chroma = alpha * sqrt(j / 100.0)
        return Cam(hue, chroma, j)
    }

    /** XYZ (thang 0..100) từ (J, C, h) của CAM16. */
    private fun xyz(j: Double, chroma: Double, hue: Double): DoubleArray {
        val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(j / 100.0)
        val t = (alpha / (1.64 - 0.29.pow(n)).pow(0.73)).pow(1.0 / 0.9)
        val hRad = Math.toRadians(hue)
        val eHue = 0.25 * (cos(hRad + 2.0) + 3.8)
        val ac = aw * (j / 100.0).pow(1.0 / c / z)
        val p1 = eHue * (50000.0 / 13.0) * nc * ncb
        val p2 = ac / nbb
        val hSin = sin(hRad)
        val hCos = cos(hRad)
        val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hCos + 108.0 * t * hSin)
        val a = gamma * hCos
        val b = gamma * hSin
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
        fun unadapt(x: Double): Double {
            val base = max(0.0, 27.13 * abs(x) / (400.0 - abs(x)))
            return sign(x) * (100.0 / fl) * base.pow(1.0 / 0.42)
        }
        val rF = unadapt(rA) / rgbD[0]
        val gF = unadapt(gA) / rgbD[1]
        val bF = unadapt(bA) / rgbD[2]
        return doubleArrayOf(
            1.86206786 * rF - 1.01125463 * gF + 0.14918677 * bF,
            0.38752654 * rF + 0.62144744 * gF - 0.00897398 * bF,
            -0.01584150 * rF - 0.03412294 * gF + 1.04996444 * bF,
        )
    }

    private fun linearRgb(xyz: DoubleArray) = doubleArrayOf(
        3.2413774792388685 * xyz[0] - 1.5376652402851851 * xyz[1] - 0.49885366846268053 * xyz[2],
        -0.9691452513005321 * xyz[0] + 1.8758853451067872 * xyz[1] + 0.04156585616912061 * xyz[2],
        0.05562093689691305 * xyz[0] - 0.20395524564742123 * xyz[1] + 1.0571799111220335 * xyz[2],
    )

    /** Tìm J để màu (J, chroma, hue) có độ sáng L* = tone; trả về null nếu màu đó nằm ngoài gam sRGB. */
    private fun solveAtChroma(hue: Double, chroma: Double, tone: Double): Int? {
        val targetY = yFromLstar(tone)
        var lo = 0.0
        var hi = 100.0
        var rgb = doubleArrayOf(0.0, 0.0, 0.0)
        repeat(28) {
            val mid = (lo + hi) / 2.0
            val xyz = xyz(mid, chroma, hue)
            if (xyz[1] < targetY) lo = mid else hi = mid
            rgb = linearRgb(xyz)
        }
        val xyz = xyz((lo + hi) / 2.0, chroma, hue)
        rgb = linearRgb(xyz)
        for (v in rgb) if (v < -0.5 || v > 100.5) return null
        return argb(delinearized(rgb[0]), delinearized(rgb[1]), delinearized(rgb[2]))
    }

    /** Màu sRGB (ARGB) có hue, chroma gần nhất có thể và độ sáng [tone] cho trước. */
    fun solve(hue: Double, chroma: Double, tone: Double): Int {
        if (chroma < 1.0 || tone <= 0.5 || tone >= 99.5) return grayArgb(tone.coerceIn(0.0, 100.0))
        val h = ((hue % 360.0) + 360.0) % 360.0
        solveAtChroma(h, chroma, tone)?.let { return it }
        var lo = 0.0
        var hi = chroma
        var best: Int? = null
        repeat(16) {
            val mid = (lo + hi) / 2.0
            val r = solveAtChroma(h, mid, tone)
            if (r != null) { best = r; lo = mid } else hi = mid
        }
        return best ?: grayArgb(tone)
    }

    fun hueOf(argb: Int) = cam(argb).hue
    fun chromaOf(argb: Int) = cam(argb).chroma

    fun sanitize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    internal fun floorHue(argb: Int): Int = floor(cam(argb).hue).toInt().coerceIn(0, 359)
}
