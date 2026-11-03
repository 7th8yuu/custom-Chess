package com.customchess

import com.customchess.ai.CoachPrompt
import com.customchess.ai.Provider
import com.customchess.core.Coach
import com.customchess.core.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPromptTest {
    @Test fun promptContainsPositionAndEngineLines() {
        val pos = Position.start()
        val lines = Coach.topMoves(pos, count = 2, depth = 2, limitMs = 2000)
        assertEquals(2, lines.size)
        val u = CoachPrompt.user(pos, "1. e4", lines, "e4")
        assertTrue(u.contains(pos.toFen()))
        assertTrue(u.contains("Moves so far: 1. e4"))
        assertTrue(u.contains("Last move played: e4"))
        assertTrue(u.contains("1. ${lines[0].san} (eval "))
        assertTrue(u.contains("2. ${lines[1].san} (eval "))
        assertTrue(u.contains("Side to move: White"))
    }

    @Test fun systemPromptNamesAnswerLanguage() {
        assertTrue(CoachPrompt.system("Vietnamese").contains("in Vietnamese"))
        assertTrue(CoachPrompt.system("French").contains("in French"))
    }

    @Test fun geminiIsMainAndOthersAreOptional() {
        assertEquals(Provider.GEMINI, Provider.MAIN)
        assertEquals(listOf(Provider.OPENAI, Provider.CLAUDE), Provider.OPTIONAL)
        assertFalse(Provider.GEMINI.isOptional)
        assertTrue(Provider.OPENAI.isOptional && Provider.CLAUDE.isOptional)
    }
}
