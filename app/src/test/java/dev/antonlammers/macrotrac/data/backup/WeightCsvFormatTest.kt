package dev.antonlammers.macrotrac.data.backup

import dev.antonlammers.macrotrac.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeightCsvFormatTest {

    private val headers = CsvFormat.parseHeaders(WeightCsvFormat.HEADER)

    @Test
    fun `toRow and fromRow round-trip preserves all fields`() {
        val entry = WeightEntry(
            weightKg = 80.5,
            date = LocalDate.of(2026, 5, 28),
            timestampMs = 1748000000000L,
        )
        val row = WeightCsvFormat.toRow(entry)
        val parsed = WeightCsvFormat.fromRow(row, headers)!!

        assertEquals(entry.date, parsed.date)
        assertEquals(entry.weightKg, parsed.weightKg, 0.001)
        assertEquals(entry.timestampMs, parsed.timestampMs)
    }

    @Test
    fun `fromRow returns null when date is missing or invalid`() {
        val badHeaders = mapOf("weight_kg" to 0, "timestamp_ms" to 1)
        assertNull(WeightCsvFormat.fromRow("80.5,1748000000000", badHeaders))
    }

    @Test
    fun `fromRow returns null when weight_kg is missing`() {
        val sparseHeaders = mapOf("date" to 0, "timestamp_ms" to 1)
        assertNull(WeightCsvFormat.fromRow("2026-05-28,1748000000000", sparseHeaders))
    }

    @Test
    fun `fromRow uses current time when timestamp_ms is missing`() {
        val sparseHeaders = CsvFormat.parseHeaders("date,weight_kg")
        val before = System.currentTimeMillis()
        val entry = WeightCsvFormat.fromRow("2026-05-28,75.0", sparseHeaders)!!
        val after = System.currentTimeMillis()

        assertNotNull(entry)
        assert(entry.timestampMs in before..after)
    }

    @Test
    fun `parseHeaders returns correct index map`() {
        val h = CsvFormat.parseHeaders("date,weight_kg,timestamp_ms")
        assertEquals(0, h["date"])
        assertEquals(1, h["weight_kg"])
        assertEquals(2, h["timestamp_ms"])
    }

    @Test
    fun `fromRow ignores extra unknown columns`() {
        val extendedHeaders = CsvFormat.parseHeaders("date,weight_kg,timestamp_ms,extra_col")
        val entry = WeightCsvFormat.fromRow("2026-05-28,82.0,1748000000000,ignored", extendedHeaders)!!
        assertEquals(82.0, entry.weightKg, 0.001)
    }
}
