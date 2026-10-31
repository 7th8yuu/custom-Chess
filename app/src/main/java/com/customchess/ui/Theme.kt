package com.customchess.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.customchess.R
import com.customchess.color.Hct
import com.customchess.color.PhotoSeeds
import com.customchess.color.Roles
import com.customchess.color.Scheme
import com.customchess.color.Tonal

/**
 * Trạng thái giao diện dùng chung: bảng màu Material 3 đang áp dụng, bộ quân, tọa độ.
 * Là state của Compose nên mọi chỗ đọc ([Pal], bàn cờ...) tự vẽ lại khi đổi, kể cả trong lúc chuyển màu.
 */
object Skin {
    var roles: Roles by mutableStateOf(Scheme.generate(PhotoSeeds.LACQUER, dark = true))
    var dark: Boolean by mutableStateOf(true)
    var pieceStyle: PieceStyle by mutableStateOf(PIECE_STYLES[0])
    /** Dải màu cùng hue với màu chủ đạo, dùng cho bộ quân "Theo ảnh". */
    var tint: Tonal by mutableStateOf(Tonal(Hct.hueOf(PhotoSeeds.LACQUER), 12.0))
    var showCoords: Boolean by mutableStateOf(true)
}

/**
 * Tên màu giữ từ thiết kế "sơn mài" ban đầu, nay ánh xạ vào các vai trò Material 3 của bảng màu hiện hành
 * nên đổi theo ảnh bàn cờ và theo chế độ sáng/tối.
 */
object Pal {
    /** Nền ứng dụng. */
    val Ink: Color get() = Color(Skin.roles.background)
    /** Thẻ, ô nhập. */
    val Panel: Color get() = Color(Skin.roles.surfaceContainer)
    /** Thẻ đang chọn, nút phụ. */
    val PanelHi: Color get() = Color(Skin.roles.surfaceContainerHigh)
    /** Đường viền mảnh. */
    val Hair: Color get() = Color(Skin.roles.outlineVariant)
    /** Màu nhấn chính (primary). */
    val Gold: Color get() = Color(Skin.roles.primary)
    /** Chữ và biểu tượng nằm trên [Gold]. */
    val OnGold: Color get() = Color(Skin.roles.onPrimary)
    val GoldDeep: Color get() = Color(Skin.roles.outline)
    /** Chữ chính. */
    val Egg: Color get() = Color(Skin.roles.onSurface)
    /** Chữ phụ. */
    val EggDim: Color get() = Color(Skin.roles.onSurfaceVariant)
    val Jade: Color get() = if (Skin.dark) Color(0xFF58BDA3) else Color(0xFF1E7F69)
    val Alert: Color get() = Color(Skin.roles.error)
}

/**
 * Một bàn cờ. Bàn cờ ảnh ([photo] != 0) vẽ ảnh làm nền rồi phủ lớp màu mờ: [light] và [dark] là màu phủ
 * (có độ trong suốt) lên ô sáng và ô tối. Bàn cờ thường dùng [light] và [dark] làm màu ô.
 * [seed] là màu hạt giống tạo bảng màu Material 3 cho cả ứng dụng.
 */
data class BoardTheme(
    val id: String,
    val name: String,
    val light: Color,
    val dark: Color,
    val frame: Color,
    val frameEdge: Color,
    val seed: Int,
    val muted: Boolean = false,
    @DrawableRes val photo: Int = 0,
    @DrawableRes val thumb: Int = 0,
    val highlight: Color = Color(0xFFD9AE4B),
) {
    val isPhoto: Boolean get() = photo != 0
}

private fun photoTheme(
    id: String, name: String, seed: Int, @DrawableRes photo: Int, @DrawableRes thumb: Int, darkVeil: Color,
): BoardTheme {
    val hue = Hct.hueOf(seed)
    return BoardTheme(
        id = id, name = name,
        light = Color(0x47FFFFFF), dark = darkVeil,
        frame = Color(Hct.solve(hue, 30.0, 68.0)), frameEdge = Color(Hct.solve(hue, 28.0, 20.0)),
        seed = seed, photo = photo, thumb = thumb, highlight = Color(0xFFFFD24D),
    )
}

val BOARD_THEMES: List<BoardTheme> = listOf(
    BoardTheme("lacquer", "Sơn mài", Color(0xFFE6D8BC), Color(0xFF7B3126), Color(0xFFC79B3F), Color(0xFF5A3A12), PhotoSeeds.LACQUER),
    BoardTheme("slate", "Đá xanh", Color(0xFFD7E0E8), Color(0xFF4D6A82), Color(0xFF8FA6BB), Color(0xFF22313F), PhotoSeeds.SLATE),
    BoardTheme("graphite", "Than chì", Color(0xFFD3CEC6), Color(0xFF58524D), Color(0xFF8C857C), Color(0xFF2A2623), PhotoSeeds.GRAPHITE, muted = true),
    photoTheme("sky", "Trời xanh", PhotoSeeds.SKY, R.drawable.board_sky, R.drawable.thumb_sky, Color(0x7508285A)),
    photoTheme("dusk", "Chiều tím", PhotoSeeds.DUSK, R.drawable.board_dusk, R.drawable.thumb_dusk, Color(0x750A1028)),
    photoTheme("fire", "Hoàng hôn", PhotoSeeds.FIRE, R.drawable.board_fire, R.drawable.thumb_fire, Color(0x75280A1E)),
    photoTheme("rose", "Mây hồng", PhotoSeeds.ROSE, R.drawable.board_rose, R.drawable.thumb_rose, Color(0x752D0F3C)),
)

object Type {
    // Các kiểu chữ là getter để màu lấy theo bảng màu hiện hành mỗi lần vẽ.
    val Display: TextStyle get() = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp, color = Pal.Egg,
    )
    val Title: TextStyle get() = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp, color = Pal.Egg,
    )
    val Heading: TextStyle get() = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, color = Pal.Egg,
    )
    val Body: TextStyle get() = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Pal.Egg)
    val Small: TextStyle get() = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Pal.EggDim)
    val Button: TextStyle get() = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val Clock: TextStyle get() = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Pal.Egg,
    )
}

private fun Roles.toColorScheme(dark: Boolean): ColorScheme {
    fun c(v: Int) = Color(v)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(primary), onPrimary = c(onPrimary),
        primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer),
        inversePrimary = c(inversePrimary),
        secondary = c(secondary), onSecondary = c(onSecondary),
        secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary),
        tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        background = c(background), onBackground = c(onBackground),
        surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant),
        surfaceTint = c(primary),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
        error = c(error), onError = c(onError),
        errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
        outline = c(outline), outlineVariant = c(outlineVariant),
        surfaceBright = c(surfaceBright), surfaceDim = c(surfaceDim),
        surfaceContainerLowest = c(surfaceContainerLowest), surfaceContainerLow = c(surfaceContainerLow),
        surfaceContainer = c(surfaceContainer), surfaceContainerHigh = c(surfaceContainerHigh),
        surfaceContainerHighest = c(surfaceContainerHighest),
    )
}

private fun blend(from: List<Int>, to: List<Int>, t: Float): List<Int> =
    List(from.size) { lerp(Color(from[it]), Color(to[it]), t).toArgb() }

/**
 * Áp bảng màu Material 3 sinh từ [seed] (màu chủ đạo của bàn cờ). Khi [seed] hoặc [dark] đổi,
 * toàn bộ màu của ứng dụng trượt mượt sang bảng màu mới.
 */
@Composable
fun CustomChessTheme(
    seed: Int,
    muted: Boolean,
    dark: Boolean,
    pieceStyle: PieceStyle,
    showCoords: Boolean,
    content: @Composable () -> Unit,
) {
    val target = remember(seed, muted, dark) { Scheme.generate(seed, dark, muted) }
    // Lần đầu đặt thẳng, không chạy hiệu ứng, để lúc mở ứng dụng không bị nháy màu từ bảng màu mặc định.
    remember { Skin.roles = target; Skin.dark = dark; true }
    // Các giá trị này phải sẵn sàng trước khi các màn hình bên dưới vẽ khung hình đầu tiên.
    remember(seed) { Skin.tint = Tonal(Hct.hueOf(seed), 12.0); true }
    remember(pieceStyle) { Skin.pieceStyle = pieceStyle; true }
    remember(showCoords) { Skin.showCoords = showCoords; true }
    SideEffect { Skin.dark = dark }

    LaunchedEffect(target) {
        val from = Skin.roles.toList()
        val to = target.toList()
        if (from != to) {
            animate(0f, 1f, animationSpec = tween(520, easing = FastOutSlowInEasing)) { t, _ ->
                Skin.roles = Roles.fromList(blend(from, to, t))
            }
        }
        Skin.roles = target
    }

    MaterialTheme(colorScheme = Skin.roles.toColorScheme(dark), content = content)
}
