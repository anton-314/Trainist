package dev.antonlammers.trainist.ui.overview

import dev.antonlammers.trainist.domain.model.FoodEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class WeekSummaryTest {

    @Test
    fun `weekOf runs Monday to Sunday around a midweek day`() {
        val week = weekOf(LocalDate.of(2026, 10, 21)) // Wednesday
        assertEquals(LocalDate.of(2026, 10, 19), week.first())
        assertEquals(LocalDate.of(2026, 10, 25), week.last())
        assertEquals(DayOfWeek.entries.toList(), week.map { it.dayOfWeek })
    }

    @Test
    fun `weekOf keeps a Monday and a Sunday in their own week`() {
        assertEquals(LocalDate.of(2026, 10, 19), weekOf(LocalDate.of(2026, 10, 19)).first())
        assertEquals(LocalDate.of(2026, 10, 19), weekOf(LocalDate.of(2026, 10, 25)).first())
    }

    @Test
    fun `weekOf crosses a year boundary`() {
        val week = weekOf(LocalDate.of(2027, 1, 1)) // Friday
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
    }

    @Test
    fun `weekSummary sums per day, fills empty days with zero and ignores other weeks`() {
        val entries = listOf(
            entry(LocalDate.of(2026, 10, 19), 300.0),
            entry(LocalDate.of(2026, 10, 19), 150.0),
            entry(LocalDate.of(2026, 10, 25), 800.0),
            entry(LocalDate.of(2026, 10, 26), 999.0), // next Monday
        )
        val summary = weekSummary(LocalDate.of(2026, 10, 22), entries)

        assertEquals(7, summary.size)
        assertEquals(450.0, summary[0].kcal, 0.001)
        assertEquals(0.0, summary[3].kcal, 0.001)
        assertEquals(800.0, summary[6].kcal, 0.001)
        assertEquals(1250.0, summary.sumOf { it.kcal }, 0.001)
    }

    private fun entry(date: LocalDate, kcal: Double) = FoodEntry(
        foodName = "x",
        brand = null,
        amountGrams = 100.0,
        kcal = kcal,
        proteinG = 0.0,
        carbsG = 0.0,
        fatG = 0.0,
        date = date,
        timestampMs = 0L,
    )
}
