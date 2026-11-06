package com.customchess

import com.customchess.core.Translator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kiểm tra các file ngôn ngữ dựng sẵn (thư mục app/src/main/assets/i18n, các file .json): đủ câu như bản tiếng Anh gốc,
 * không câu nào rỗng, giữ đúng các chỗ chèn {0}, {1}..., có _meta hợp lệ, và Translator dịch được câu mẫu.
 * Khi thêm câu tiếng Việt mới vào mã, chạy tools/i18n/check.py để biết câu nào còn thiếu bản dịch.
 */
class LanguageFilesTest {
    private val dir = listOf("src/main/assets/i18n", "app/src/main/assets/i18n").map { File(it) }.first { it.isDirectory }

    private fun load(code: String): JSONObject = JSONObject(File(dir, "$code.json").readText(Charsets.UTF_8))

    private fun codes(): List<String> = dir.listFiles { f -> f.extension == "json" }!!.map { it.nameWithoutExtension }.sorted()

    @Test fun englishExistsAndIsComplete() {
        val en = load("en")
        assertTrue("en.json phải có nhiều câu", en.length() > 700)
        assertEquals("en", en.getJSONObject("_meta").getString("code"))
    }

    @Test fun everyLanguageHasSameKeysAsEnglish() {
        val en = load("en")
        val keys = en.keys().asSequence().filter { !it.startsWith("_") }.toSet()
        for (code in codes()) {
            val j = load(code)
            val meta = j.getJSONObject("_meta")
            assertEquals(code, meta.getString("code"))
            assertTrue("$code thiếu native", meta.getString("native").isNotBlank())
            assertTrue("$code thiếu english", meta.getString("english").isNotBlank())
            val mine = j.keys().asSequence().filter { !it.startsWith("_") }.toSet()
            assertEquals("$code: khóa phải trùng bản tiếng Anh", keys, mine)
        }
    }

    @Test fun placeholdersAreKeptAndNothingIsBlank() {
        for (code in codes()) {
            val j = load(code)
            for (k in j.keys()) {
                if (k.startsWith("_")) continue
                val v = j.getString(k)
                assertTrue("$code: bản dịch rỗng cho \"${k.take(40)}\"", v.isNotBlank())
                assertEquals(
                    "$code: sai chỗ chèn ở \"${k.take(40)}\"",
                    Translator.placeholders(k).sorted(), Translator.placeholders(v).sorted(),
                )
            }
        }
    }

    @Test fun translatorWorksOnRealDictionaries() {
        for (code in codes()) {
            val j = load(code)
            val map = j.keys().asSequence().associateWith { j.get(it) as? String ?: "" }
            val t = Translator(map)
            // một câu cố định, một câu có chỗ chèn và một câu ghép
            val white = t.translate("Đến lượt Trắng")
            assertTrue("$code: Đến lượt Trắng chưa được dịch", white != "Đến lượt Trắng" && white.isNotBlank())
            val learned = t.translate("Bạn đã chơi liên tiếp 5 ngày.")
            assertTrue("$code: câu có số phải giữ số 5", learned.contains("5"))
            // (không dùng hasVietnamese: tiếng Tây Ban Nha, Pháp... cũng có chữ như í, é)
            assertFalse("$code: câu có số chưa dịch", learned.contains("ngày") || learned.contains("chơi"))
        }
    }
}
