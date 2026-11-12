package com.customchess

import com.customchess.data.CustomException
import com.customchess.data.MarkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class MarkStyleTest {
    private fun error(json: String): String =
        try { MarkStyle.parse(json); fail("lẽ ra phải báo lỗi"); "" } catch (e: CustomException) { e.message.orEmpty() }

    @Test fun defaultStyleSurvivesAJsonRoundTrip() {
        assertEquals(MarkStyle.DEFAULT, MarkStyle.parse(MarkStyle.DEFAULT.toJson()))
    }

    @Test fun emptyObjectGivesDefaultsWithAFriendlyName() {
        val s = MarkStyle.parse("{}")
        assertEquals("Kiểu của tôi", s.name)
        assertEquals(MarkStyle.DEFAULT.colors, s.colors)
        assertEquals(MarkStyle.DEFAULT.arrowOpacity, s.arrowOpacity, 0f)
        assertFalse(s.squareRing)
    }

    @Test fun readsColoursInThreeHexForms() {
        val s = MarkStyle.parse("""{"colors": ["#f00", "00FF00", "#80112233"]}""")
        assertEquals(listOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0x80112233.toInt()), s.colors)
        assertEquals(0xFFAABBCC.toInt(), MarkStyle.parseColor(" #AABBCC "))
        assertNull(MarkStyle.parseColor("#12"))
        assertNull(MarkStyle.parseColor("#GGGGGG"))
    }

    @Test fun numbersAreClampedAndRingIsRead() {
        val s = MarkStyle.parse("""{"arrowOpacity": 5, "arrowWidth": 0, "squareOpacity": -1, "squareStyle": "RING", "name": "Mờ"}""")
        assertEquals(1f, s.arrowOpacity, 0f)
        assertEquals(0.05f, s.arrowWidth, 0f)
        assertEquals(0.1f, s.squareOpacity, 0f)
        assertTrue(s.squareRing)
        assertEquals("Mờ", s.name)
    }

    @Test fun keepsOnlyTheFirstSixColours() {
        val s = MarkStyle.parse("""{"colors": ["#111111","#222222","#333333","#444444","#555555","#666666","#777777","#888888"]}""")
        assertEquals(6, s.colors.size)
    }

    @Test fun explainsWhatIsWrong() {
        assertTrue(error("{ không phải json").contains("JSON"))
        assertTrue(error("""{"colors": ["#2FB36D", "đỏ"]}""").contains("Màu thứ 2"))
        assertTrue(error("""{"colors": "#2FB36D"}""").contains("colors"))
        assertTrue(error("""{"squareStyle": "tròn"}""").contains("squareStyle"))
        assertTrue(error("""{"arrowOpacity": "mờ"}""").contains("arrowOpacity"))
    }

    @Test fun savedValueFallsBackToDefaultWhenMissingOrBroken() {
        assertEquals(MarkStyle.DEFAULT, MarkStyle.parseOrDefault(""))
        assertEquals(MarkStyle.DEFAULT, MarkStyle.parseOrDefault("{hỏng"))
        assertEquals(1, MarkStyle.parseOrDefault("""{"colors": ["#123456"]}""").colors.size)
    }
}
