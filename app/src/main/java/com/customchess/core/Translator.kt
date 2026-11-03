package com.customchess.core

/**
 * Dịch chuỗi giao diện từ tiếng Việt (ngôn ngữ gốc trong mã) sang ngôn ngữ khác bằng từ điển.
 *
 * Khóa của từ điển là câu tiếng Việt gốc. Câu có chỗ chèn dữ liệu (số, tên...) được ghi dạng mẫu: `Đã học {0} / {1} bài`.
 * Thứ tự tra: khớp nguyên câu, khớp mẫu, rồi thay từng cụm đã biết trong câu ghép ("Bạn thắng. Chiếu hết").
 * Khóa bắt đầu bằng "_" (ví dụ `_meta`) là siêu dữ liệu, không dùng để dịch. Câu không có trong từ điển được giữ nguyên.
 * Lớp này thuần Kotlin nên kiểm thử được mà không cần Android.
 */
class Translator(entries: Map<String, String>) {
    private class Template(val regex: Regex, val target: String, val literalLen: Int)
    private class Fragment(val regex: Regex, val target: String)

    private val exact = HashMap<String, String>()
    private val templates = ArrayList<Template>()
    private val fragments = ArrayList<Fragment>()
    private val cache = HashMap<String, String>()

    val size: Int get() = exact.size + templates.size

    init {
        for ((k, v) in entries) {
            if (k.startsWith("_") || v.isBlank()) continue
            if (PLACEHOLDER.containsMatchIn(k)) templates.add(buildTemplate(k, v)) else exact[k] = v
        }
        templates.sortByDescending { it.literalLen }
        for ((k, v) in exact.entries.sortedByDescending { it.key.length }) {
            val t = k.trim()
            if (t.length < 3) continue
            fragments.add(Fragment(Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(t) + "(?![\\p{L}\\p{N}])"), v.trim()))
        }
    }

    fun translate(s: String): String {
        if (!hasVietnamese(s)) return s
        exact[s]?.let { return it }
        synchronized(cache) { cache[s]?.let { return it } }
        val out = compute(s)
        synchronized(cache) {
            if (cache.size > 4000) cache.clear()
            cache[s] = out
        }
        return out
    }

    private fun compute(s: String): String {
        for (t in templates) {
            val m = t.regex.matchEntire(s) ?: continue
            return PLACEHOLDER.replace(t.target) { r ->
                val i = r.groupValues[1].toInt()
                val g = m.groups[i + 1]?.value
                if (g == null) r.value else translate(g)
            }
        }
        var out = s
        for (f in fragments) {
            if (!hasVietnamese(out)) break
            out = f.regex.replace(out) { f.target }
        }
        return out
    }

    private fun buildTemplate(key: String, target: String): Template {
        val sb = StringBuilder("^")
        var last = 0
        var literal = 0
        for (m in PLACEHOLDER.findAll(key)) {
            val lit = key.substring(last, m.range.first)
            sb.append(Regex.escape(lit)).append("(.+?)")
            literal += lit.length
            last = m.range.last + 1
        }
        val tail = key.substring(last)
        sb.append(Regex.escape(tail)).append('$')
        literal += tail.length
        return Template(Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL), target, literal)
    }

    companion object {
        private val PLACEHOLDER = Regex("\\{(\\d+)\\}")
        private const val VI_CHARS =
            "àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ" +
                "ÀÁẠẢÃÂẦẤẬẨẪĂẰẮẶẲẴÈÉẸẺẼÊỀẾỆỂỄÌÍỊỈĨÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠÙÚỤỦŨƯỪỨỰỬỮỲÝỴỶỸĐ"
        private val VI_SET: Set<Char> = VI_CHARS.toSet()

        /** Chuỗi có chữ cái tiếng Việt có dấu không (chuỗi thuần ASCII như số, giờ, nước đi không cần dịch). */
        fun hasVietnamese(s: String): Boolean {
            for (c in s) if (c in VI_SET) return true
            return false
        }

        /** Các chỗ chèn `{n}` trong một câu, theo thứ tự xuất hiện (dùng để kiểm tra bản dịch giữ đủ chỗ chèn). */
        fun placeholders(s: String): List<Int> = PLACEHOLDER.findAll(s).map { it.groupValues[1].toInt() }.toList()
    }
}
