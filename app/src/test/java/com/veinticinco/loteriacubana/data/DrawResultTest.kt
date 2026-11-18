package com.veinticinco.loteriacubana.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DrawResultTest {
    @Test
    fun preservesLeadingZeroInPick2() {
        val result = DrawResult("2026-11-18", LotteryGame.PICK_2, DrawPeriod.MIDDAY, "07")
        assertEquals("07", result.winningNumbers)
    }

    @Test
    fun preservesLeadingZerosInPick3() {
        val result = DrawResult("2026-11-18", LotteryGame.PICK_3, DrawPeriod.EVENING, "025")
        assertEquals("025", result.winningNumbers)
    }

    @Test
    fun buildsStableUniqueKey() {
        val result = DrawResult("2026-11-18", LotteryGame.PICK_3, DrawPeriod.EVENING, "025")
        assertEquals("2026-11-18_PICK_3_EVENING", result.uniqueKey)
    }

    @Test
    fun rejectsWrongNumberLength() {
        assertThrows(IllegalArgumentException::class.java) {
            DrawResult("2026-11-18", LotteryGame.PICK_2, DrawPeriod.MIDDAY, "123")
        }
    }

    @Test
    fun rejectsImpossibleCalendarDate() {
        assertThrows(IllegalArgumentException::class.java) {
            DrawResult("2026-02-30", LotteryGame.PICK_3, DrawPeriod.MIDDAY, "123")
        }
    }

    @Test
    fun calculatesDigitFrequencyWithoutChangingValues() {
        val results = listOf(
            DrawResult("2026-11-18", LotteryGame.PICK_2, DrawPeriod.MIDDAY, "07"),
            DrawResult("2026-11-18", LotteryGame.PICK_3, DrawPeriod.EVENING, "025")
        )
        val frequency = DigitFrequency.calculate(results)
        assertEquals(2, frequency['0'])
        assertEquals(1, frequency['7'])
        assertEquals(1, frequency['2'])
        assertEquals(1, frequency['5'])
    }
}
