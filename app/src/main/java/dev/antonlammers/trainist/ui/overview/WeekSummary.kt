package dev.antonlammers.trainist.ui.overview

import dev.antonlammers.trainist.domain.model.FoodEntry
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One cell of the Overview's week strip: a day and the kcal logged on it. */
data class DaySummary(val date: LocalDate, val kcal: Double)

/** The ISO week (Monday–Sunday) of [date], Monday first. */
fun weekOf(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map { monday.plusDays(it) }
}

/** Per-day kcal totals for the week of [date]; entries outside that week are ignored. */
fun weekSummary(date: LocalDate, entries: List<FoodEntry>): List<DaySummary> {
    val kcalByDate = entries.groupBy { it.date }.mapValues { (_, dayEntries) -> dayEntries.sumOf { it.kcal } }
    return weekOf(date).map { DaySummary(it, kcalByDate[it] ?: 0.0) }
}
