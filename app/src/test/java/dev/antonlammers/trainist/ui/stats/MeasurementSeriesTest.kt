package dev.antonlammers.trainist.ui.stats

import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MeasurementSeriesTest {

    private fun entry(date: LocalDate, type: MeasurementType, cm: Double) =
        BodyMeasurementEntry(date = date, type = type, valueCm = cm)

    @Test
    fun `samples for WEEK filters by type and sorts by date`() {
        val d = LocalDate.of(2026, 6, 10)
        val entries = listOf(
            entry(d.plusDays(1), MeasurementType.WAIST, 80.0),
            entry(d, MeasurementType.WAIST, 81.0),
            entry(d, MeasurementType.CHEST, 100.0), // different type — excluded
        )

        val samples = MeasurementSeries.samples(TimeRange.WEEK, entries, MeasurementType.WAIST)

        assertEquals(2, samples.size)
        assertEquals(listOf(d, d.plusDays(1)), samples.map { it.date })
        assertEquals(81.0, samples.first().cm, 0.001)
    }

    @Test
    fun `samples for YEAR averages per calendar month placed mid-month`() {
        val entries = listOf(
            entry(LocalDate.of(2026, 1, 3), MeasurementType.WAIST, 80.0),
            entry(LocalDate.of(2026, 1, 28), MeasurementType.WAIST, 82.0),
            entry(LocalDate.of(2026, 3, 15), MeasurementType.WAIST, 78.0),
        )

        val samples = MeasurementSeries.samples(TimeRange.YEAR, entries, MeasurementType.WAIST)

        assertEquals(2, samples.size)
        assertEquals(LocalDate.of(2026, 1, 15), samples[0].date)
        assertEquals(81.0, samples[0].cm, 0.001) // (80 + 82) / 2
        assertEquals(LocalDate.of(2026, 3, 15), samples[1].date)
        assertEquals(78.0, samples[1].cm, 0.001)
    }

    @Test
    fun `bounds pad and round outward to nearest half cm`() {
        val samples = listOf(
            MeasurementSample(LocalDate.of(2026, 6, 1), 80.2),
            MeasurementSample(LocalDate.of(2026, 6, 2), 81.0),
        )

        val (min, max) = MeasurementSeries.bounds(samples)

        assertTrue("min below data", min < 80.2)
        assertTrue("max above data", max > 81.0)
        assertEquals("min on half-cm grid", 0.0, min * 2 % 1.0, 0.0001)
        assertEquals("max on half-cm grid", 0.0, max * 2 % 1.0, 0.0001)
    }

    @Test
    fun `bounds are zero when there is nothing to plot`() {
        val (min, max) = MeasurementSeries.bounds(emptyList())
        assertEquals(0.0, min, 0.0)
        assertEquals(0.0, max, 0.0)
    }
}
