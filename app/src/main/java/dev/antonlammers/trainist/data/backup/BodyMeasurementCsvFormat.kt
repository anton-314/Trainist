package dev.antonlammers.trainist.data.backup

import dev.antonlammers.trainist.domain.model.BodyMeasurementEntry
import dev.antonlammers.trainist.domain.model.MeasurementType
import java.time.LocalDate

object BodyMeasurementCsvFormat {
    private const val DATE = "date"
    private const val TYPE = "type"
    const val VALUE_CM = "value_cm"

    val HEADER: String = listOf(DATE, TYPE, VALUE_CM).joinToString(",")

    fun toRow(entry: BodyMeasurementEntry): String =
        listOf(entry.date, entry.type.name, entry.valueCm).joinToString(",")

    fun fromRow(row: String, headers: Map<String, Int>): BodyMeasurementEntry? {
        val cols = CsvFormat.parseLine(row)
        val date = cols.csvStr(headers, DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val type = MeasurementType.parse(cols.csvStr(headers, TYPE)) ?: return null
        val valueCm = cols.csvDbl(headers, VALUE_CM) ?: return null
        return BodyMeasurementEntry(date = date, type = type, valueCm = valueCm)
    }
}
