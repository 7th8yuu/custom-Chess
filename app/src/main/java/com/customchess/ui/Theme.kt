package com.customchess.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Bảng màu "sơn mài": nền nâu đen như mặt hộp sơn mài, vàng dát cho điểm nhấn duy nhất,
 * trắng vỏ trứng cho chữ và quân trắng.
 */
object Pal {
    val Ink = Color(0xFF14100E)
    val Panel = Color(0xFF1E1714)
    val PanelHi = Color(0xFF2B201B)
    val Hair = Color(0xFF3B2D26)
    val Gold = Color(0xFFD9AE4B)
    val GoldDeep = Color(0xFF9A7A34)
    val Egg = Color(0xFFF2E8D2)
    val EggDim = Color(0xFFB9AA90)
    val Jade = Color(0xFF58BDA3)
    val Alert = Color(0xFFE5614A)
}

data class BoardTheme(val name: String, val light: Color, val dark: Color, val frame: Color, val frameEdge: Color)

val BOARD_THEMES = listOf(
    BoardTheme("Sơn mài", Color(0xFFE6D8BC), Color(0xFF7B3126), Color(0xFFC79B3F), Color(0xFF5A3A12)),
    BoardTheme("Đá xanh", Color(0xFFD7E0E8), Color(0xFF4D6A82), Color(0xFF8FA6BB), Color(0xFF22313F)),
    BoardTheme("Than chì", Color(0xFFD3CEC6), Color(0xFF58524D), Color(0xFF8C857C), Color(0xFF2A2623)),
)

object Type {
    val Display = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp, color = Pal.Egg,
    )
    val Title = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.2).sp, color = Pal.Egg,
    )
    val Heading = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 24.sp, color = Pal.Egg,
    )
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, color = Pal.Egg)
    val Small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = Pal.EggDim)
    val Button = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val Clock = TextStyle(
        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Pal.Egg,
    )
}

@Composable
fun CustomChessTheme(content: @Composable () -> Unit) {
    // Giao diện luôn tối: bàn cờ sơn mài chỉ đẹp trên nền tối.
    @Suppress("UNUSED_VARIABLE") val unused = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pal.Gold,
            onPrimary = Pal.Ink,
            secondary = Pal.Jade,
            background = Pal.Ink,
            onBackground = Pal.Egg,
            surface = Pal.Panel,
            onSurface = Pal.Egg,
            surfaceVariant = Pal.PanelHi,
            onSurfaceVariant = Pal.EggDim,
            outline = Pal.Hair,
            error = Pal.Alert,
        ),
        content = content,
    )
}
