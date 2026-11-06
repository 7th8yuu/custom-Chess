package com.customchess

import com.customchess.core.Translator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatorTest {
    private val t = Translator(
        mapOf(
            "_meta" to "bỏ qua",
            "Chơi với máy" to "Play the computer",
            "Đến lượt Trắng" to "White to move",
            "Bạn thắng" to "You win",
            "Chiếu hết" to "Checkmate",
            "Đã học {0} / {1} bài" to "Learned {0} of {1} lessons",
            "Thêm API key cho {0}" to "Add an API key for {0}",
            "Dễ" to "Easy",
            "Đen" to "Black",
            "Chính xác {0}%" to "Accuracy {0}%",
            "Lỗi {0}{1}" to "Error {0}{1}",
            " (kiểm tra lại API key)" to " (check your API key)",
        ),
    )

    @Test fun exactMatch() {
        assertEquals("Play the computer", t.translate("Chơi với máy"))
        assertEquals("White to move", t.translate("Đến lượt Trắng"))
    }

    @Test fun templateKeepsPlaceholderValues() {
        assertEquals("Learned 3 of 11 lessons", t.translate("Đã học 3 / 11 bài"))
        assertEquals("Accuracy 87%", t.translate("Chính xác 87%"))
    }

    @Test fun placeholderValuesAreTranslatedToo() {
        // {0} là tên cấp độ tiếng Việt: cũng được dịch
        assertEquals("Add an API key for Easy", t.translate("Thêm API key cho Dễ"))
        assertEquals("Add an API key for Gemini", t.translate("Thêm API key cho Gemini"))
    }

    @Test fun compoundSentencesTranslateByFragment() {
        assertEquals("You win. Checkmate", t.translate("Bạn thắng. Chiếu hết"))
        assertEquals("Error 401: Unauthorized (check your API key)", t.translate("Lỗi 401: Unauthorized (kiểm tra lại API key)").replace("  ", " "))
    }

    @Test fun fragmentsRespectWordBoundaries() {
        // "Đen" nằm trong từ khác thì không được thay, đứng riêng thì được thay
        assertEquals("Đenxyz", t.translate("Đenxyz"))
        assertEquals("Quân Black", t.translate("Quân Đen"))
    }

    @Test fun unknownAndAsciiStringsPassThrough() {
        assertEquals("e4", t.translate("e4"))
        assertEquals("05:32", t.translate("05:32"))
        assertEquals("Câu chưa có trong từ điển", t.translate("Câu chưa có trong từ điển"))
    }

    @Test fun metaKeysAreIgnoredAndHelpers() {
        assertEquals("_meta", t.translate("_meta"))
        assertEquals("Chiếu hết", Translator(mapOf("_meta" to "x", "_a" to "y")).translate("Chiếu hết"))
        assertTrue(Translator.hasVietnamese("Đen"))
        assertFalse(Translator.hasVietnamese("Black"))
        assertEquals(listOf(0, 1), Translator.placeholders("{0} và {1}"))
    }

    @Test fun repeatedCallsUseCacheConsistently() {
        repeat(3) { assertEquals("Learned 5 of 11 lessons", t.translate("Đã học 5 / 11 bài")) }
    }
}
