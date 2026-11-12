package com.customchess

import com.customchess.core.MarkArrow
import com.customchess.core.MarkSquare
import com.customchess.core.Marks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksTest {
    private val e2e4 = MarkArrow(12, 28, 0)

    @Test fun drawingTheSameArrowTwiceRemovesIt() {
        val once = Marks.toggleArrow(emptyList(), e2e4)
        assertEquals(listOf(e2e4), once)
        assertTrue(Marks.toggleArrow(once, e2e4).isEmpty())
    }

    @Test fun sameArrowInAnotherColourChangesTheColour() {
        val red = e2e4.copy(color = 1)
        val list = Marks.toggleArrow(listOf(e2e4), red)
        assertEquals(listOf(red), list)
    }

    @Test fun differentArrowsAreKeptTogether() {
        val g1f3 = MarkArrow(6, 21, 2)
        val list = Marks.toggleArrow(Marks.toggleArrow(emptyList(), e2e4), g1f3)
        assertEquals(listOf(e2e4, g1f3), list)
        // một mũi tên theo hướng ngược lại là mũi tên khác
        assertEquals(3, Marks.toggleArrow(list, MarkArrow(28, 12, 0)).size)
    }

    @Test fun zeroLengthOrOffBoardArrowsAreIgnored() {
        assertTrue(Marks.toggleArrow(emptyList(), MarkArrow(5, 5, 0)).isEmpty())
        assertTrue(Marks.toggleArrow(emptyList(), MarkArrow(-1, 5, 0)).isEmpty())
        assertTrue(Marks.toggleArrow(emptyList(), MarkArrow(5, 64, 0)).isEmpty())
    }

    @Test fun squaresToggleAndRecolour() {
        val green = MarkSquare(27, 0)
        val on = Marks.toggleSquare(emptyList(), green)
        assertEquals(listOf(green), on)
        assertEquals(listOf(MarkSquare(27, 3)), Marks.toggleSquare(on, MarkSquare(27, 3)))
        assertTrue(Marks.toggleSquare(on, green).isEmpty())
        assertTrue(Marks.toggleSquare(emptyList(), MarkSquare(64, 0)).isEmpty())
    }
}
