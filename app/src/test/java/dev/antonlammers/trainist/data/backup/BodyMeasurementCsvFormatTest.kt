package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BodyMeasurementCsvFormatTest {

    private val headers = CsvFormat.parseHeaders(BodyMeasurementCsvFormat.HEADER)

    @Test
    fun `toRow and fromRow round-trip preserves all fields`() {
        val entry = BodyMeasurementEntry(
            date = LocalDate.of(2026, 5, 28),
            type = MeasurementType.CHEST,
            valueCm = 101.5,
        )
        val row = BodyMeasurementCsvFormat.toRow(entry)
        val parsed = BodyMeasurementCsvFormat.fromRow(row, headers)!!

        assertEquals(entry.date, parsed.date)
        assertEquals(entry.type, parsed.type)
        assertEquals(entry.valueCm, parsed.valueCm, 0.001)
    }

    @Test
    fun `fromRow returns null when date is missing or invalid`() {
        val badHeaders = mapOf("type" to 0, "value_cm" to 1)
        assertNull(BodyMeasurementCsvFormat.fromRow("WAIST,81.5", badHeaders))
    }

    @Test
    fun `fromRow returns null when type is missing or unrecognised`() {
        val sparseHeaders = CsvFormat.parseHeaders("date,value_cm")
        assertNull(BodyMeasurementCsvFormat.fromRow("2026-05-28,81.5", sparseHeaders))

        val fullHeaders = CsvFormat.parseHeaders("date,type,value_cm")
        assertNull(BodyMeasurementCsvFormat.fromRow("2026-05-28,FOREARM,81.5", fullHeaders))
    }

    @Test
    fun `fromRow returns null when value_cm is missing`() {
        val sparseHeaders = mapOf("date" to 0, "type" to 1)
        assertNull(BodyMeasurementCsvFormat.fromRow("2026-05-28,WAIST", sparseHeaders))
    }

    @Test
    fun `fromRow ignores extra unknown columns`() {
        val extendedHeaders = CsvFormat.parseHeaders("date,type,value_cm,extra_col")
        val entry = BodyMeasurementCsvFormat.fromRow("2026-05-28,WAIST,82.0,ignored", extendedHeaders)!!
        assertEquals(82.0, entry.valueCm, 0.001)
    }
}
