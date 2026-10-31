package com.customchess

import com.customchess.color.Dominant
import com.customchess.color.Hct
import com.customchess.color.PhotoSeeds
import com.customchess.color.Roles
import com.customchess.color.Scheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class ColorTest {
    private val seeds = listOf(
        PhotoSeeds.LACQUER, PhotoSeeds.SLATE, PhotoSeeds.GRAPHITE,
        PhotoSeeds.SKY, PhotoSeeds.DUSK, PhotoSeeds.FIRE, PhotoSeeds.ROSE,
    )

    private fun hueGap(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    @Test
    fun materialBaselineAccentsMatchTheReferenceImplementation() {
        // Giá trị chuẩn của Material 3 cho màu hạt giống #6750A4 (bảng màu mặc định của Material You).
        val light = Scheme.generate(0xFF6750A4.toInt(), dark = false)
        assertEquals(0xFF6750A4.toInt(), light.primary)
        assertEquals(0xFFE9DDFF.toInt(), light.primaryContainer)
        assertEquals(0xFF625B71.toInt(), light.secondary)
        assertEquals(0xFF7E5260.toInt(), light.tertiary)
        val dark = Scheme.generate(0xFF6750A4.toInt(), dark = true)
        assertEquals(0xFFCFBCFF.toInt(), dark.primary)
    }

    @Test
    fun solveKeepsHueChromaAndTone() {
        for (c in listOf(0xFF336699.toInt(), 0xFFD9AE4B.toInt(), 0xFFE5614A.toInt(), 0xFF228B22.toInt())) {
            val cam = Hct.cam(c)
            val back = Hct.solve(cam.hue, cam.chroma, Hct.toneOf(c))
            assertEquals(c, back)
        }
        for (tone in listOf(10, 30, 50, 70, 90)) {
            val c = Hct.solve(250.0, 40.0, tone.toDouble())
            assertTrue("tone $tone -> ${Hct.toneOf(c)}", abs(Hct.toneOf(c) - tone) < 1.0)
        }
    }

    @Test
    fun rolesSurviveTheFlatListRoundTrip() {
        val r = Scheme.generate(PhotoSeeds.DUSK, dark = true)
        assertEquals(Roles.SIZE, r.toList().size)
        assertEquals(r, Roles.fromList(r.toList()))
    }

    @Test
    fun textStaysReadableOnEveryBoardPalette() {
        for (seed in seeds) for (dark in listOf(true, false)) for (muted in listOf(false, true)) {
            val r = Scheme.generate(seed, dark, muted)
            fun gap(a: Int, b: Int) = abs(Hct.toneOf(a) - Hct.toneOf(b))
            val tag = "seed=%08X dark=$dark muted=$muted".format(seed)
            assertTrue("$tag onPrimary/primary", gap(r.primary, r.onPrimary) >= 40)
            assertTrue("$tag onSurface/surface", gap(r.surface, r.onSurface) >= 60)
            assertTrue("$tag onSurfaceVariant/surfaceContainer", gap(r.surfaceContainer, r.onSurfaceVariant) >= 45)
            assertTrue("$tag onError/error", gap(r.error, r.onError) >= 40)
            assertTrue("$tag primary/background", gap(r.primary, r.background) >= 40)
        }
    }

    @Test
    fun darkSurfacesLeanTowardsTheSeedHue() {
        // Cả ứng dụng phải ngả về màu của ảnh: nền tối của ảnh trời xanh có sắc lạnh, của ảnh hoàng hôn có sắc ấm.
        val sky = Scheme.generate(PhotoSeeds.SKY, dark = true).surfaceContainer
        val fire = Scheme.generate(PhotoSeeds.FIRE, dark = true).surfaceContainer
        assertTrue("sky blue channel should exceed red", (sky and 255) > ((sky shr 16) and 255))
        assertTrue("fire red channel should exceed blue", ((fire shr 16) and 255) > (fire and 255))
    }

    private fun photo(name: String): File? =
        listOf("src/main/res/drawable-nodpi/$name", "app/src/main/res/drawable-nodpi/$name").map(::File).firstOrNull { it.exists() }

    /**
     * Lấy mẫu 96x96 điểm ảnh của file JPEG. Dùng reflection vì unit test của Android biên dịch với android.jar
     * nên không thấy javax.imageio ở lúc biên dịch, dù JDK chạy test có sẵn.
     */
    private fun samplePixels(f: File): IntArray? = try {
        val img = Class.forName("javax.imageio.ImageIO").getMethod("read", File::class.java).invoke(null, f)
        val cls = img.javaClass
        val w = cls.getMethod("getWidth").invoke(img) as Int
        val h = cls.getMethod("getHeight").invoke(img) as Int
        val rgb = cls.getMethod("getRGB", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        IntArray(96 * 96) { i -> rgb.invoke(img, (i % 96) * w / 96, (i / 96) * h / 96) as Int }
    } catch (_: Exception) {
        null
    }

    @Test
    fun boardSeedsComeFromThePhotos() {
        val expected = mapOf(
            "board_sky.jpg" to PhotoSeeds.SKY, "board_dusk.jpg" to PhotoSeeds.DUSK,
            "board_fire.jpg" to PhotoSeeds.FIRE, "board_rose.jpg" to PhotoSeeds.ROSE,
        )
        for ((file, seed) in expected) {
            val f = photo(file) ?: continue // chạy từ thư mục khác: bỏ qua thay vì báo lỗi giả
            val px = samplePixels(f) ?: continue
            val found = Dominant.seed(px)
            val gap = hueGap(Hct.hueOf(found), Hct.hueOf(seed))
            assertTrue("$file: màu chọn từ ảnh %08X lệch %.0f độ so với seed %08X".format(found, gap, seed), gap <= 30.0)
        }
    }
}
