package com.customchess.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.customchess.core.Translator
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** Một ngôn ngữ hiển thị: [code] (vi, en, ...), [nativeName] hiện trong danh sách, [english] dùng để dặn AI trả lời đúng ngôn ngữ. */
data class LangInfo(val code: String, val nativeName: String, val english: String, val custom: Boolean = false)

/**
 * Ngôn ngữ giao diện. Mã nguồn viết bằng tiếng Việt; ngôn ngữ khác được dịch lúc chạy bằng từ điển JSON
 * (assets/i18n/<mã>.json có sẵn, hoặc file người dùng tự nhập lưu ở thư mục riêng của ứng dụng).
 * Mọi `Text` của ứng dụng đi qua [tr], nên đổi ngôn ngữ có hiệu lực ngay trên mọi màn hình.
 */
object Lang {
    const val AUTO = "auto"
    const val SOURCE = "vi"

    val BUILTIN: List<LangInfo> = listOf(
        LangInfo("vi", "Tiếng Việt", "Vietnamese"),
        LangInfo("en", "English", "English"),
        LangInfo("zh", "简体中文", "Simplified Chinese"),
        LangInfo("es", "Español", "Spanish"),
        LangInfo("fr", "Français", "French"),
        LangInfo("de", "Deutsch", "German"),
        LangInfo("ru", "Русский", "Russian"),
        LangInfo("ja", "日本語", "Japanese"),
        LangInfo("ko", "한국어", "Korean"),
    )

    /** Danh sách đang có: ngôn ngữ dựng sẵn cộng ngôn ngữ người dùng đã nhập. */
    var infos by mutableStateOf(BUILTIN)
        private set

    /** Tăng mỗi lần đổi ngôn ngữ để các `Text` tự vẽ lại. */
    private var version by mutableIntStateOf(0)

    var code: String = SOURCE
        private set

    private var translator: Translator? = null

    /** Locale dùng cho ngày giờ. */
    val locale: Locale get() = if (code == SOURCE) Locale("vi") else Locale.forLanguageTag(code)

    /** Tên tiếng Anh của ngôn ngữ đang dùng, để dặn AI. */
    val english: String get() = infos.firstOrNull { it.code == code }?.english ?: "English"

    fun tr(s: String): String {
        @Suppress("UNUSED_VARIABLE") val v = version // đọc state để Text đăng ký theo dõi
        val t = translator ?: return s
        return t.translate(s)
    }

    private fun customDir(ctx: Context) = File(ctx.filesDir, "i18n")

    /** Đọc lại danh sách ngôn ngữ tự nhập từ thư mục riêng. */
    fun refresh(ctx: Context) {
        val extra = ArrayList<LangInfo>()
        customDir(ctx).listFiles { f -> f.extension == "json" }?.sortedBy { it.name }?.forEach { f ->
            val info = runCatching { metaOf(JSONObject(f.readText())) }.getOrNull() ?: return@forEach
            if (BUILTIN.none { it.code == info.code }) extra.add(info.copy(custom = true))
        }
        infos = BUILTIN + extra
    }

    /** Áp dụng lựa chọn trong Cài đặt: "auto" (theo hệ thống) hoặc một mã ngôn ngữ. [force] nạp lại từ điển dù mã không đổi. */
    fun apply(ctx: Context, setting: String, force: Boolean = false) {
        refresh(ctx)
        val want = if (setting == AUTO) systemCode() else setting
        val use = if (infos.any { it.code == want }) want else "en"
        if (!force && use == code && version > 0) return
        translator = if (use == SOURCE) null else load(ctx, use)
        code = if (translator == null) SOURCE else use
        version++
    }

    /** Ngôn ngữ của hệ thống nếu có trong danh sách, không thì tiếng Anh. */
    private fun systemCode(): String {
        val l = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (infos.any { it.code == l }) l else "en"
    }

    private fun load(ctx: Context, code: String): Translator? {
        val text = runCatching { File(customDir(ctx), "$code.json").takeIf { it.exists() }?.readText() }.getOrNull()
            ?: runCatching { ctx.assets.open("i18n/$code.json").bufferedReader().use { it.readText() } }.getOrNull()
            ?: return null
        return runCatching { Translator(parse(JSONObject(text))) }.getOrNull()
    }

    private fun parse(j: JSONObject): Map<String, String> {
        val m = HashMap<String, String>(j.length() * 2)
        val it = j.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = j.opt(k)
            if (v is String) m[k] = v
        }
        return m
    }

    private fun metaOf(j: JSONObject): LangInfo? {
        val m = j.optJSONObject("_meta") ?: return null
        val code = m.optString("code").trim().lowercase(Locale.ROOT)
        if (!Regex("[a-z]{2,3}(-[a-z0-9]{2,8})?").matches(code)) return null
        val native = m.optString("native").trim().ifEmpty { code }
        val english = m.optString("english").trim().ifEmpty { native }
        return LangInfo(code, native, english)
    }

    /** Kết quả nhập một file ngôn ngữ. */
    class ImportResult(val ok: Boolean, val message: String, val code: String = "")

    /**
     * Nhập file ngôn ngữ do người dùng tự dịch. Phải là JSON phẳng {"câu tiếng Việt": "bản dịch"} và có
     * "_meta": {"code": "th", "native": "ไทย", "english": "Thai"}. Bản dịch phải giữ nguyên các chỗ chèn {0}, {1}...
     */
    fun importText(ctx: Context, text: String): ImportResult {
        val j = try { JSONObject(text) } catch (_: Exception) { return ImportResult(false, "File không phải JSON hợp lệ") }
        val info = metaOf(j) ?: return ImportResult(false, "Thiếu mục _meta (cần code, native, english) hoặc mã ngôn ngữ không hợp lệ")
        if (info.code == SOURCE) return ImportResult(false, "Không ghi đè tiếng Việt (ngôn ngữ gốc)")
        val map = parse(j)
        var bad = 0
        var used = 0
        for ((k, v) in map) {
            if (k.startsWith("_")) continue
            used++
            if (Translator.placeholders(k).sorted() != Translator.placeholders(v).sorted()) bad++
        }
        if (used == 0) return ImportResult(false, "File không có câu nào để dịch")
        if (bad > 0) return ImportResult(false, "$bad câu làm mất hoặc thêm chỗ chèn {0}, {1}... so với câu gốc. Hãy sửa rồi nhập lại")
        val dir = customDir(ctx)
        dir.mkdirs()
        File(dir, "${info.code}.json").writeText(text)
        refresh(ctx)
        return ImportResult(true, "Đã thêm ngôn ngữ ${info.nativeName} ($used câu)", info.code)
    }

    /** Xóa một ngôn ngữ tự nhập. */
    fun delete(ctx: Context, code: String) {
        File(customDir(ctx), "$code.json").delete()
        refresh(ctx)
    }

    /** File mẫu để dịch: mọi câu gốc làm khóa, giá trị để người dùng điền (mặc định lấy bản tiếng Anh nếu có). */
    fun templateJson(ctx: Context): String {
        val base = runCatching { ctx.assets.open("i18n/en.json").bufferedReader().use { it.readText() } }.getOrNull()
        val src = base?.let { runCatching { JSONObject(it) }.getOrNull() }
        val out = JSONObject()
        out.put("_meta", JSONObject().put("code", "xx").put("native", "Tên ngôn ngữ của bạn").put("english", "Name of your language in English"))
        if (src != null) {
            val keys = src.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!k.startsWith("_")) out.put(k, src.optString(k, k))
            }
        }
        return out.toString(1)
    }
}
