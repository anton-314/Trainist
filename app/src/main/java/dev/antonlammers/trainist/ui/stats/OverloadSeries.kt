package dev.antonlammers.trainist.ui.stats

import dev.antonlammers.trainist.domain.WorkoutMetrics
import dev.antonlammers.trainist.domain.model.ExerciseType
import dev.antonlammers.trainist.domain.model.FoodEntry
import dev.antonlammers.trainist.domain.model.WeightEntry
import dev.antonlammers.trainist.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

/** The index every progression series starts at, so every value reads directly as a percentage. */
internal const val BASE_INDEX = 100.0

/** One point of the overall strength index: a real calendar date and the index level at that time. */
data class OverloadPoint(val date: LocalDate, val index: Double)

/** [OverloadSeries.index]'s output: the series plus how many exercises actually carried it. */
data class OverloadIndex(
    val points: List<OverloadPoint> = emptyList(),
    /** Distinct exercises that contributed at least one week-over-week comparison. */
    val trackedExercises: Int = 0,
)

/**
 * Everything the progressive-overload chart draws. Like the other chart models the x-axis is
 * time-based ([rangeStart]..[rangeEnd]) so points sit at their real dates.
 */
data class OverloadChartData(
    val points: List<OverloadPoint> = emptyList(),
    val rangeStart: LocalDate = LocalDate.now(),
    val rangeEnd: LocalDate = LocalDate.now(),
    val minIndex: Double = BASE_INDEX,
    val maxIndex: Double = BASE_INDEX,
    val trackedExercises: Int = 0,
) {
    /** A single point is a starting line, not a progression — the chart needs two to show a trend. */
    val hasData: Boolean get() = points.size >= 2
}

/**
 * Math behind the **progressive-overload** card: a single *overall* strength index across all
 * exercises, plus the nutrition/body-weight aggregates the [dev.antonlammers.trainist.domain.ProgressionAdvisor]
 * reasons about. Pure and Android-free (`OverloadSeriesTest`), like [WeightSeries]/[WorkoutSeries].
 *
 * **Why an index and not an average of 1RMs.** Raw estimated 1RMs are not comparable across
 * exercises — averaging a 120 kg squat with a 15 kg lateral raise just tracks the squat. And an
 * average over "whatever was trained this week" jumps whenever the split rotates (leg day vs. arm
 * day), which would read as progress that never happened.
 *
 * **The chain-linked index instead.** Weekly buckets; per week and exercise the best estimated 1RM
 * (Epley, warm-ups excluded). Each week's growth is the *geometric mean of the per-exercise ratios*
 * against that same exercise's own previous observation, and the index is that growth chained onto
 * the previous level starting at [BASE_INDEX]. Consequences, all of them deliberate:
 * - Composition-proof: only an exercise compared **with itself** moves the index, so a rotating
 *   split, a newly added exercise or a dropped one never fakes a jump.
 * - A first-time exercise contributes nothing that week; it only starts counting from its second
 *   appearance ([OverloadIndex.trackedExercises] counts exactly the ones that did contribute).
 * - A comparison older than [MAX_LOOKBACK_WEEKS] is dropped rather than squeezed into one weekly
 *   step, so a lift revisited after two months doesn't detonate that week's growth.
 * - Per-step ratios are clamped to [MIN_STEP_RATIO]..[MAX_STEP_RATIO]; a mistyped 500 kg cannot
 *   destroy the series (a genuine ±20 % week-over-week jump on one lift doesn't exist).
 */
internal object OverloadSeries {

    /**
     * Observation window of the card, in weeks — fixed and independent of the screen's time-range
     * chips. A 7-day window cannot separate a trend from normal day-to-day variation, so this card
     * always looks at enough history to say something defensible.
     */
    const val WINDOW_WEEKS = 12

    /** The recent sub-window used to detect a progression that has flattened out lately. */
    const val RECENT_WEEKS = 4L

    /** Oldest previous observation still usable for a week-over-week comparison. */
    private const val MAX_LOOKBACK_WEEKS = 4L

    private const val MAX_STEP_RATIO = 1.20
    private const val MIN_STEP_RATIO = 0.80

    /** Logged days required before mean intake is treated as representative. */
    const val MIN_LOGGED_DAYS = 21

    /** Weigh-ins (and days spanned) required before a body-weight trend is reported. */
    const val MIN_WEIGH_INS = 3
    const val MIN_TREND_DAYS = 21L

    /** Monday of [date]'s week — the app's calendar is Monday-first throughout. */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** First day of the card's fixed [WINDOW_WEEKS]-week window ending on [today]. */
    fun windowStart(today: LocalDate): LocalDate = weekStart(today.minusWeeks(WINDOW_WEEKS - 1L))

    /**
     * The chain-linked overall strength index over [sessions] (already filtered to the window and to
     * completed sessions), one point per week that produced usable data, date-ascending. Each point
     * is dated at the last training day of its week so it sits truthfully on the time axis.
     */
    fun index(
        sessions: List<WorkoutSession>,
        typeOf: (String) -> ExerciseType,
        bodyWeightForDate: (LocalDate) -> Double?,
    ): OverloadIndex {
        // Best estimated 1RM per (week, exercise), plus the week's last training day for the x-axis.
        val best = mutableMapOf<LocalDate, MutableMap<String, Double>>()
        val lastDayOfWeek = mutableMapOf<LocalDate, LocalDate>()
        sessions.forEach { session ->
            val week = weekStart(session.date)
            session.exercises.forEach { exercise ->
                val oneRepMax = WorkoutMetrics.bestEstimatedOneRepMaxKg(
                    exercise.sets,
                    typeOf(exercise.exerciseStableId),
                    bodyWeightForDate(session.date),
                ) ?: return@forEach
                if (oneRepMax <= 0.0) return@forEach
                val perExercise = best.getOrPut(week) { mutableMapOf() }
                perExercise[exercise.exerciseStableId] =
                    maxOf(perExercise[exercise.exerciseStableId] ?: 0.0, oneRepMax)
                val known = lastDayOfWeek[week]
                if (known == null || session.date.isAfter(known)) lastDayOfWeek[week] = session.date
            }
        }

        val weeks = best.keys.sorted()
        if (weeks.isEmpty()) return OverloadIndex()

        // Per exercise: the week and value of its most recent observation so far.
        val lastSeen = mutableMapOf<String, Pair<LocalDate, Double>>()
        val tracked = mutableSetOf<String>()
        var level = BASE_INDEX
        val points = mutableListOf<OverloadPoint>()

        weeks.forEach { week ->
            val observed = best.getValue(week)
            val ratios = observed.mapNotNull { (exerciseId, value) ->
                val (previousWeek, previousValue) = lastSeen[exerciseId] ?: return@mapNotNull null
                if (ChronoUnit.WEEKS.between(previousWeek, week) > MAX_LOOKBACK_WEEKS) return@mapNotNull null
                tracked += exerciseId
                (value / previousValue).coerceIn(MIN_STEP_RATIO, MAX_STEP_RATIO)
            }
            // Geometric mean: growth is multiplicative, so a +10 %/−10 % pair must net out to 1.
            if (ratios.isNotEmpty()) level *= exp(ratios.sumOf { ln(it) } / ratios.size)
            observed.forEach { (exerciseId, value) -> lastSeen[exerciseId] = week to value }
            points += OverloadPoint(lastDayOfWeek[week] ?: week, level)
        }
        return OverloadIndex(points, tracked.size)
    }

    /**
     * Index change over the whole series in percent (the series starts at [BASE_INDEX], so this is
     * simply the final level minus that base). Null below two points — a single week is a baseline,
     * not a change.
     */
    fun totalChangePercent(points: List<OverloadPoint>): Double? =
        if (points.size < 2) null else points.last().index - BASE_INDEX

    /**
     * Index change in percent from the last point on or before [cutoff] to the final point — used to
     * spot a progression that has flattened out recently. Null when the cutoff has no earlier point
     * or would compare a point with itself.
     */
    fun changeSince(points: List<OverloadPoint>, cutoff: LocalDate): Double? {
        if (points.size < 2) return null
        val base = points.lastOrNull { !it.date.isAfter(cutoff) } ?: return null
        val last = points.last()
        if (base.date == last.date || base.index <= 0.0) return null
        return (last.index / base.index - 1.0) * 100.0
    }

    /**
     * Weeks spanned from the first to the last point, inclusive — the denominator for "sessions per
     * week". Deliberately *not* the number of weeks that contain data: a three-week gap has to lower
     * the frequency, otherwise a burst of training followed by silence would look consistent.
     */
    fun spanWeeks(points: List<OverloadPoint>): Int {
        if (points.isEmpty()) return 0
        val first = weekStart(points.first().date)
        val last = weekStart(points.last().date)
        return (ChronoUnit.WEEKS.between(first, last) + 1).toInt()
    }

    /**
     * Padded y-axis bounds covering all index values **and** the [BASE_INDEX] reference line (so the
     * starting level is always visible), rounded outward to whole index points.
     */
    fun bounds(points: List<OverloadPoint>): Pair<Double, Double> {
        val values = points.map { it.index } + BASE_INDEX
        val lo = values.min()
        val hi = values.max()
        val pad = maxOf(1.0, (hi - lo) * 0.15)
        return floor(lo - pad) to ceil(hi + pad)
    }

    /**
     * Body-weight trend in kg per week, as the least-squares slope over the weigh-ins (robust to a
     * single noisy day, unlike a first-versus-last difference). Null unless there are at least
     * [MIN_WEIGH_INS] entries spanning [MIN_TREND_DAYS] days — a shorter series is water weight.
     */
    fun bodyWeightTrendKgPerWeek(entries: List<WeightEntry>): Double? {
        if (entries.size < MIN_WEIGH_INS) return null
        val sorted = entries.sortedBy { it.date }
        val spanDays = ChronoUnit.DAYS.between(sorted.first().date, sorted.last().date)
        if (spanDays < MIN_TREND_DAYS) return null
        val xs = sorted.map { (it.date.toEpochDay() - sorted.first().date.toEpochDay()).toDouble() }
        val ys = sorted.map { it.weightKg }
        val meanX = xs.average()
        val meanY = ys.average()
        val denominator = xs.sumOf { (it - meanX) * (it - meanX) }
        if (denominator <= 0.0) return null
        val slopePerDay = xs.indices.sumOf { (xs[it] - meanX) * (ys[it] - meanY) } / denominator
        return slopePerDay * 7.0
    }

    /**
     * Mean kcal of the **logged** days divided by [goalKcal]. Unlogged days are excluded on purpose:
     * counting them as 0 kcal would report a crash diet to anyone who simply forgot to track.
     * Null below [MIN_LOGGED_DAYS] logged days or without a usable goal.
     */
    fun kcalVsGoal(entries: List<FoodEntry>, goalKcal: Double): Double? {
        if (goalKcal <= 0.0) return null
        val perDay = entries.groupBy { it.date }.mapValues { (_, day) -> day.sumOf { it.kcal } }
        if (perDay.size < MIN_LOGGED_DAYS) return null
        return perDay.values.average() / goalKcal
    }

    /** Mean protein of the logged days per kg body weight; null under the same rules as [kcalVsGoal]. */
    fun proteinPerKgBodyWeight(entries: List<FoodEntry>, bodyWeightKg: Double?): Double? {
        if (bodyWeightKg == null || bodyWeightKg <= 0.0) return null
        val perDay = entries.groupBy { it.date }.mapValues { (_, day) -> day.sumOf { it.proteinG } }
        if (perDay.size < MIN_LOGGED_DAYS) return null
        return perDay.values.average() / bodyWeightKg
    }
}
