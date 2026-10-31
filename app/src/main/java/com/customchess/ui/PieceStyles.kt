package com.customchess.ui

import androidx.compose.ui.graphics.Color

/**
 * Các bộ quân có sẵn. [PieceStyle.family] là chỉ số trong [PieceShapes.FAMILIES]:
 * 0 = họ "sơn mài" ban đầu, 1 = Staunton nhiều chi tiết, 2 = hình khối phẳng.
 */
val PIECE_STYLES: List<PieceStyle> = listOf(
    PieceStyle(
        id = "lacquer", name = "Sơn mài", family = 0,
        whiteTop = Color(0xFFFFF9EA), whiteBottom = Color(0xFFD9C7A0), blackTop = Color(0xFF4B3B36), blackBottom = Color(0xFF150E0C),
        whiteLine = Color(0xFF2B1D17), blackLine = Color(0xFFE3C98A),
        lineWidth = 4.8f, detailAlpha = 0.75f, fillAlpha = 1.0f,
        glow = false, gloss = 0f, shadow = 0f,
        dynamic = false,
    ),
    PieceStyle(
        id = "royal", name = "Hoàng gia", family = 1,
        whiteTop = Color(0xFFFFFBF1), whiteBottom = Color(0xFFE2D1AA), blackTop = Color(0xFF46403B), blackBottom = Color(0xFF141010),
        whiteLine = Color(0xFF3A2A1D), blackLine = Color(0xFFCDB783),
        lineWidth = 4.4f, detailAlpha = 0.8f, fillAlpha = 1.0f,
        glow = false, gloss = 0.16f, shadow = 0.2f,
        dynamic = false,
    ),
    PieceStyle(
        id = "wood", name = "Gỗ", family = 1,
        whiteTop = Color(0xFFF3D9A8), whiteBottom = Color(0xFFC9964F), blackTop = Color(0xFF85532F), blackBottom = Color(0xFF3A2013),
        whiteLine = Color(0xFF55341A), blackLine = Color(0xFF1B0E07),
        lineWidth = 4.4f, detailAlpha = 0.55f, fillAlpha = 1.0f,
        glow = false, gloss = 0.14f, shadow = 0.22f,
        dynamic = false,
    ),
    PieceStyle(
        id = "metal", name = "Bạc & Vàng", family = 1,
        whiteTop = Color(0xFFFFFFFF), whiteBottom = Color(0xFFA9B3C0), blackTop = Color(0xFFE3B64E), blackBottom = Color(0xFF6E4811),
        whiteLine = Color(0xFF3C4756), blackLine = Color(0xFF3D2705),
        lineWidth = 4.4f, detailAlpha = 0.7f, fillAlpha = 1.0f,
        glow = false, gloss = 0.3f, shadow = 0.2f,
        dynamic = false,
    ),
    PieceStyle(
        id = "jade", name = "Ngọc", family = 0,
        whiteTop = Color(0xFFF3FBF3), whiteBottom = Color(0xFFB2D5BC), blackTop = Color(0xFF3C7C66), blackBottom = Color(0xFF0C2C25),
        whiteLine = Color(0xFF244A3D), blackLine = Color(0xFFBFE8D2),
        lineWidth = 4.6f, detailAlpha = 0.6f, fillAlpha = 1.0f,
        glow = false, gloss = 0.34f, shadow = 0.2f,
        dynamic = false,
    ),
    PieceStyle(
        id = "photo", name = "Theo ảnh", family = 2,
        whiteTop = Color.Unspecified, whiteBottom = Color.Unspecified, blackTop = Color.Unspecified, blackBottom = Color.Unspecified,
        whiteLine = Color.Unspecified, blackLine = Color.Unspecified,
        lineWidth = 4.2f, detailAlpha = 0.7f, fillAlpha = 1.0f,
        glow = false, gloss = 0.18f, shadow = 0.2f,
        dynamic = true,
    ),
    PieceStyle(
        id = "neon", name = "Neon", family = 2,
        whiteTop = Color(0xFF0B1B2A), whiteBottom = Color(0xFF0B1B2A), blackTop = Color(0xFF2A0B27), blackBottom = Color(0xFF2A0B27),
        whiteLine = Color(0xFF5CE6FF), blackLine = Color(0xFFFF4FD8),
        lineWidth = 3.0f, detailAlpha = 0.9f, fillAlpha = 0.62f,
        glow = true, gloss = 0f, shadow = 0f,
        dynamic = false,
    ),
    PieceStyle(
        id = "line", name = "Nét vẽ", family = 2,
        whiteTop = Color(0xFFFFFFFF), whiteBottom = Color(0xFFFFFFFF), blackTop = Color(0xFF12100E), blackBottom = Color(0xFF12100E),
        whiteLine = Color(0xFFFFFFFF), blackLine = Color(0xFF14110F),
        lineWidth = 3.2f, detailAlpha = 0.9f, fillAlpha = 0.28f,
        glow = false, gloss = 0f, shadow = 0f, haloWhite = Color(0xFF1A1613), haloBlack = Color(0xFFFFFFFF),
        dynamic = false,
    ),
)
