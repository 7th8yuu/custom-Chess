package com.customchess.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.PathParser
import com.customchess.core.Pc
import com.customchess.data.CustomBoard
import org.json.JSONObject
import java.io.File

/** Mã bàn cờ tự thêm trong cài đặt có dạng "c:<id>". */
const val CUSTOM_PREFIX = "c:"

/** Tìm bàn cờ theo mã: có sẵn hoặc ảnh người dùng thêm. Không thấy thì dùng bàn cờ đầu tiên. */
fun resolveBoard(id: String, customs: List<CustomBoard>, pathOf: (CustomBoard) -> String): BoardTheme {
    BOARD_THEMES.firstOrNull { it.id == id }?.let { return it }
    if (id.startsWith(CUSTOM_PREFIX)) {
        val c = customs.firstOrNull { it.id == id.removePrefix(CUSTOM_PREFIX) }
        if (c != null) return customBoardTheme(c.id, c.name, c.seed, c.muted, pathOf(c))
    }
    return BOARD_THEMES[0]
}

/** Các bộ quân tự thêm đã giải mã (ảnh nằm trong bộ nhớ), khóa là id không có tiền tố "c:". */
object PieceStyleCache {
    val styles = mutableStateMapOf<String, PieceStyle>()
}

fun resolvePieces(id: String): PieceStyle {
    PIECE_STYLES.firstOrNull { it.id == id }?.let { return it }
    if (id.startsWith(CUSTOM_PREFIX)) PieceStyleCache.styles[id.removePrefix(CUSTOM_PREFIX)]?.let { return it }
    return PIECE_STYLES[0]
}

private fun parseColor(s: String?): Color? {
    if (s.isNullOrBlank()) return null
    return try { Color(android.graphics.Color.parseColor(s.trim())) } catch (_: Exception) { null }
}

private fun pieceKey(k: String): Int? {
    if (k.length != 2) return null
    val type = "PNBRQK".indexOf(k[1]) + 1
    if (type <= 0) return null
    return when (k[0]) { 'w' -> type; 'b' -> -type; else -> null }
}

/** Đọc một bộ quân tự thêm từ thư mục [dir] (set.json và ảnh). Chạy ngoài luồng giao diện. */
fun loadPieceStyle(dir: File): PieceStyle? = runCatching {
    val j = JSONObject(File(dir, "set.json").readText())
    val base = PIECE_STYLES.firstOrNull { it.id == j.optString("base") } ?: PIECE_STYLES[1]
    val colors = j.optJSONObject("colors")
    val st = j.optJSONObject("style")
    fun col(k: String, def: Color) = parseColor(colors?.optString(k)) ?: def
    val hasColors = colors != null && colors.length() > 0
    val dynamic = base.dynamic && !hasColors
    val images = HashMap<Int, ImageBitmap>()
    j.optJSONObject("images")?.let { im ->
        for (k in im.keys()) {
            val key = pieceKey(k) ?: continue
            val bmp = BitmapFactory.decodeFile(File(dir, im.optString(k)).path) ?: continue
            images[key] = bmp.asImageBitmap()
        }
    }
    val bodies = HashMap<Int, Path>()
    j.optJSONObject("paths")?.let { pj ->
        for (k in pj.keys()) {
            val key = pieceKey(k) ?: continue
            runCatching { PathParser().parsePathString(pj.optString(k)).toPath() }.getOrNull()?.let { bodies[key] = it }
        }
    }
    PieceStyle(
        id = CUSTOM_PREFIX + dir.name, name = j.optString("name", "Bộ quân của tôi"), family = base.family,
        whiteTop = col("whiteTop", base.whiteTop), whiteBottom = col("whiteBottom", base.whiteBottom),
        blackTop = col("blackTop", base.blackTop), blackBottom = col("blackBottom", base.blackBottom),
        whiteLine = col("whiteLine", base.whiteLine), blackLine = col("blackLine", base.blackLine),
        lineWidth = st?.optDouble("lineWidth", base.lineWidth.toDouble())?.toFloat() ?: base.lineWidth,
        detailAlpha = base.detailAlpha,
        fillAlpha = st?.optDouble("fillAlpha", base.fillAlpha.toDouble())?.toFloat()?.coerceIn(0.05f, 1f) ?: base.fillAlpha,
        glow = st?.optBoolean("glow", base.glow) ?: base.glow,
        gloss = st?.optDouble("gloss", base.gloss.toDouble())?.toFloat()?.coerceIn(0f, 0.8f) ?: base.gloss,
        shadow = st?.optDouble("shadow", base.shadow.toDouble())?.toFloat()?.coerceIn(0f, 0.6f) ?: base.shadow,
        haloWhite = base.haloWhite, haloBlack = base.haloBlack,
        dynamic = dynamic, images = images, bodies = bodies,
    )
}.getOrNull()

/** Tên quân (để mô tả). */
fun pieceTypeName(type: Int) = when (type) {
    Pc.PAWN -> "Tốt"; Pc.KNIGHT -> "Mã"; Pc.BISHOP -> "Tượng"; Pc.ROOK -> "Xe"; Pc.QUEEN -> "Hậu"; else -> "Vua"
}
