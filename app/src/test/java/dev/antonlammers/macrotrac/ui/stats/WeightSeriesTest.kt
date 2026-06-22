package dev.antonlammers.macrotrac.ui.stats

import dev.antonlammers.macrotrac.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeightSeriesTest {

    private fun entry(date: LocalDate, kg: Double) =
        WeightEntry(weightKg = kg, date = date, timestampMs = 0L)

    @Test
    fun `samples for WEEK keeps one point per entry sorted by date`() {
        val d = LocalDate.of(2026, 6, 10)
        val entries = listOf(
            entry(d.plusDays(2), 80.0),
            entry(d, 81.0),
            entry(d.plusDays(1), 80.5),
        )

        val samples = WeightSeries.samples(TimeRange.WEEK, entries)

        assertEquals(3, samples.size)
        assertEquals(listOf(d, d.plusDays(1), d.plusDays(2)), samples.map { it.date })
        assertEquals(81.0, samples.first().kg, 0.001)
    }

    @Test
    fun `samples for YEAR averages per calendar month placed mid-month`() {
        val entries = listOf(
            entry(LocalDate.of(2026, 1, 3), 80.0),
            entry(LocalDate.of(2026, 1, 28), 82.0),
            entry(LocalDate.of(2026, 3, 15), 78.0),
        )

        val samples = WeightSeries.samples(TimeRange.YEAR, entries)

        assertEquals(2, samples.size)
        assertEquals(LocalDate.of(2026, 1, 15), samples[0].date)
        assertEquals(81.0, samples[0].kg, 0.001) // (80 + 82) / 2
        assertEquals(LocalDate.of(2026, 3, 15), samples[1].date)
        assertEquals(78.0, samples[1].kg, 0.001)
    }

    @Test
    fun `movingAverage smooths within trailing window`() {
        val d = LocalDate.of(2026, 6, 1)
        val samples = listOf(
            WeightSample(d, 80.0),
            WeightSample(d.plusDays(1), 82.0),
            WeightSample(d.plusDays(2), 84.0),
        )

        // 2-day window: each point averages itself with the previous day.
        val avg = WeightSeries.movingAverage(samples, windowDays = 2)

        assertEquals(80.0, avg[0].kg, 0.001) // only itself
        assertEquals(81.0, avg[1].kg, 0.001) // (80 + 82) / 2
        assertEquals(83.0, avg[2].kg, 0.001) // (82 + 84) / 2
    }

    @Test
    fun `movingAverage is empty for zero window or too few samples`() {
        val d = LocalDate.of(2026, 6, 1)
        val one = listOf(WeightSample(d, 80.0))
        val two = listOf(WeightSample(d, 80.0), WeightSample(d.plusDays(1), 81.0))

        assertTrue(WeightSeries.movingAverage(two, windowDays = 0).isEmpty())
        assertTrue(WeightSeries.movingAverage(one, windowDays = 7).isEmpty())
    }

    @Test
    fun `trend window is disabled only for WEEK`() {
        assertEquals(0, WeightSeries.trendWindowDays(TimeRange.WEEK))
        assertTrue(WeightSeries.trendWindowDays(TimeRange.MONTH) > 0)
        assertTrue(WeightSeries.trendWindowDays(TimeRange.YEAR) > 0)
    }

    @Test
    fun `bounds pad and round outward to nearest half kg`() {
        val samples = listOf(
            WeightSample(LocalDate.of(2026, 6, 1), 80.2),
            WeightSample(LocalDate.of(2026, 6, 2), 81.0),
        )

        val (min, max) = WeightSeries.bounds(samples, target = null)

        assertTrue("min below data", min < 80.2)
        assertTrue("max above data", max > 81.0)
        assertEquals("min on half-kg grid", 0.0, min * 2 % 1.0, 0.0001)
        assertEquals("max on half-kg grid", 0.0, max * 2 % 1.0, 0.0001)
    }

    @Test
    fun `bounds include the target so the target line stays visible`() {
        val samples = listOf(
            WeightSample(LocalDate.of(2026, 6, 1), 80.0),
            WeightSample(LocalDate.of(2026, 6, 2), 80.2),
        )

        val (min, max) = WeightSeries.bounds(samples, target = 72.0)

        assertTrue("target within bounds", min <= 72.0 && 72.0 <= max)
    }

    @Test
    fun `bounds are zero when there is nothing to plot`() {
        val (min, max) = WeightSeries.bounds(emptyList(), target = null)
        assertEquals(0.0, min, 0.0)
        assertEquals(0.0, max, 0.0)
    }
}
