package dev.antonlammers.trainist.domain.model

import java.time.LocalDate

/** One measurement location's value on a given day — at most one per (date, type). */
data class BodyMeasurementEntry(
    val id: Long = 0,
    val date: LocalDate,
    val type: MeasurementType,
    val valueCm: Double,
)
