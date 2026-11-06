package com.customchess.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * Kiểu nét vẽ trên bàn cờ (mũi tên và ô tô màu), nhập/xuất bằng JSON. Mọi trường đều tùy chọn, thiếu thì lấy mặc định:
 * {
 *   "name": "Kiểu của tôi",
 *   "colors": ["#2FB36D", "#E5484D", "#3B82F6", "#F2B63D"],   // 1 đến 6 màu: #RGB, #RRGGBB hoặc #AARRGGBB
 *   "arrowOpacity": 0.85,     // độ đậm mũi tên, 0.1 đến 1
 *   "arrowWidth": 0.17,       // bề dày thân mũi tên theo cạnh ô, 0.05 đến 0.35
 *   "squareOpacity": 0.55,    // độ đậm ô tô, 0.1 đến 1
 *   "squareStyle": "fill"     // "fill" tô kín ô, "ring" chỉ vẽ vòng tròn
 * }
 */
data class MarkStyle(
    val name: String,
    /** Màu ARGB. */
    val colors: List<Int>,
    val arrowOpacity: Float,
    val arrowWidth: Float,
    val squareOpacity: Float,
    val squareRing: Boolean,
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("name", name)
        o.put("colors", JSONArray(colors.map { hex(it) }))
        o.put("arrowOpacity", round2(arrowOpacity))
        o.put("arrowWidth", round2(arrowWidth))
        o.put("squareOpacity", round2(squareOpacity))
        o.put("squareStyle", if (squareRing) "ring" else "fill")
        return o.toString(2)
    }

    companion object {
        const val MAX_COLORS = 6

        val DEFAULT = MarkStyle(
            name = "Mặc định",
            colors = listOf(0xFF2FB36D.toInt(), 0xFFE5484D.toInt(), 0xFF3B82F6.toInt(), 0xFFF2B63D.toInt()),
            arrowOpacity = 0.85f, arrowWidth = 0.17f, squareOpacity = 0.55f, squareRing = false,
        )

        private fun round2(v: Float) = Math.round(v * 100f) / 100.0

        private fun hex(argb: Int): String =
            if ((argb ushr 24) == 0xFF) String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
            else String.format(Locale.US, "#%08X", argb)

        /** Đọc "#RGB", "#RRGGBB" hoặc "#AARRGGBB" (dấu # không bắt buộc); null nếu sai. */
        fun parseColor(text: String): Int? {
            var t = text.trim().removePrefix("#")
            if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
            if (t.length != 6 && t.length != 8) return null
            if (!t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
            val v = t.toLong(16)
            return if (t.length == 6) (0xFF000000L or v).toInt() else v.toInt()
        }

        private fun num(o: JSONObject, key: String, def: Float, lo: Float, hi: Float): Float {
            if (!o.has(key) || o.isNull(key)) return def
            val v = o.optDouble(key, Double.NaN)
            if (v.isNaN()) throw CustomException("\"$key\" phải là một số")
            return v.toFloat().coerceIn(lo, hi)
        }

        /** Đọc JSON kiểu nét vẽ; sai thì ném [CustomException] với thông báo tiếng Việt. */
        fun parse(text: String): MarkStyle {
            val o = try {
                JSONObject(text.trim().removePrefix("﻿"))
            } catch (e: JSONException) {
                throw CustomException("Không đọc được JSON: kiểm tra dấu ngoặc và dấu phẩy")
            }
            val colors = ArrayList<Int>()
            if (o.has("colors") && !o.isNull("colors")) {
                val arr = o.optJSONArray("colors") ?: throw CustomException("\"colors\" phải là danh sách các màu, ví dụ [\"#2FB36D\", \"#E5484D\"]")
                for (i in 0 until minOf(arr.length(), MAX_COLORS)) {
                    val s = arr.optString(i, "")
                    colors.add(parseColor(s) ?: throw CustomException("Màu thứ ${i + 1} (\"$s\") không hợp lệ, hãy dùng dạng #RRGGBB"))
                }
            }
            if (colors.isEmpty()) colors.addAll(DEFAULT.colors)
            val style = o.optString("squareStyle", "fill").lowercase()
            if (style != "fill" && style != "ring") throw CustomException("\"squareStyle\" chỉ nhận \"fill\" hoặc \"ring\"")
            return MarkStyle(
                name = o.optString("name", "").trim().ifEmpty { "Kiểu của tôi" },
                colors = colors,
                arrowOpacity = num(o, "arrowOpacity", DEFAULT.arrowOpacity, 0.1f, 1f),
                arrowWidth = num(o, "arrowWidth", DEFAULT.arrowWidth, 0.05f, 0.35f),
                squareOpacity = num(o, "squareOpacity", DEFAULT.squareOpacity, 0.1f, 1f),
                squareRing = style == "ring",
            )
        }

        /** Dùng cho giá trị đã lưu: rỗng hoặc hỏng thì quay về mặc định. */
        fun parseOrDefault(text: String): MarkStyle =
            if (text.isBlank()) DEFAULT else try { parse(text) } catch (e: CustomException) { DEFAULT }
    }
}
