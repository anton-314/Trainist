package dev.antonlammers.trainist.domain

/**
 * Verdict of the progressive-overload analysis over the observation window.
 *
 * [INSUFFICIENT_DATA] is a first-class outcome, not an error: a strength trend cannot be separated
 * from normal day-to-day performance variation (roughly ±5 % on an estimated 1RM) inside a few
 * sessions, so the advisor deliberately refuses to interpret a window that is too small rather than
 * telling the user after one workout that they are eating too little.
 */
enum class ProgressionTrend { INSUFFICIENT_DATA, PROGRESSING, MAINTAINING, DECLINING }

/**
 * One actionable, evidence-based tip. Structured — never text: the UI layer maps each constant to a
 * localized string resource (CLAUDE.md: ViewModels don't build display text).
 */
enum class ProgressionAdvice {
    /** Window too small for any interpretation. */
    COLLECT_MORE_DATA,

    /** Strength is rising — reinforce what works (double progression). */
    KEEP_GOING,

    /** Rising overall, but the most recent weeks flattened out. */
    RECENT_STALL,

    /** Holding strength while losing body weight — a success, not a plateau. */
    EXPECTED_IN_DEFICIT,

    /** Losing body weight *and* strength — the deficit is likely too aggressive. */
    ENERGY_DEFICIT,

    /** Protein intake below the range recommended for hypertrophy/strength. */
    PROTEIN_LOW,

    /** Training very frequently with little rest — recovery is the limiter. */
    RECOVERY_FREQUENCY,

    /** Sessions too sparse/irregular for a training stimulus to accumulate. */
    CONSISTENCY,

    /** Long uninterrupted accumulation block — fatigue is masking fitness. */
    DELOAD,

    /** Nutrition and recovery look fine — the programming itself needs to change. */
    PROGRESSION_SCHEME,
}

/**
 * Everything the advisor reasons about, already reduced to plain numbers by the caller
 * (see `OverloadSeries`) so this stays pure and fully unit-testable. Every field that can be
 * unknown is nullable — a missing signal never produces a tip, it only removes one.
 */
data class ProgressionFacts(
    /** Weeks in the window that contained at least one usable training session. */
    val trainingWeeks: Int = 0,
    /** Completed sessions in the window. */
    val sessions: Int = 0,
    /** Strength-index change over the whole window in percent; `null` when not computable. */
    val totalChangePercent: Double? = null,
    /** Strength-index change over the most recent weeks in percent; `null` when not computable. */
    val recentChangePercent: Double? = null,
    /** Body-weight trend in kg per week (negative = losing); `null` when there are too few weigh-ins. */
    val bodyWeightTrendKgPerWeek: Double? = null,
    /** Mean logged kcal ÷ the daily kcal goal (1.0 = on target); `null` when too few days were logged. */
    val kcalVsGoal: Double? = null,
    /** Mean logged protein per kg body weight; `null` when unknown. */
    val proteinGPerKgBodyWeight: Double? = null,
    /** Sessions per week across the trained span (gaps included). */
    val sessionsPerWeek: Double = 0.0,
)

/** The advisor's output: one verdict plus the ranked tips that apply to it. */
data class ProgressionInsight(
    val trend: ProgressionTrend,
    val advice: List<ProgressionAdvice>,
)

/**
 * Turns measured training/nutrition facts into a strength-progression verdict and a short, ranked
 * list of tips — the "smart" half of the progressive-overload card.
 *
 * Design rules, in the order they matter:
 * 1. **Never conclude from a small window.** Below [MIN_TRAINING_WEEKS] trained weeks *or*
 *    [MIN_SESSIONS] sessions the only output is [ProgressionAdvice.COLLECT_MORE_DATA]. No causal
 *    claim ("you eat too little") can be made from a handful of workouts.
 * 2. **A dead band around zero.** Estimated-1RM values fluctuate by a few percent from day to day
 *    (sleep, technique, time of day), so "no change" is a band, not a point — and the decline
 *    threshold is set wider than the progress threshold because calling a regression is the claim
 *    that needs the stronger evidence.
 * 3. **Measured signals beat assumed ones.** Whether the user is in an energy deficit is read from
 *    the *body-weight trend* first (an actual outcome) and only falls back to logged kcal versus the
 *    goal when there are too few weigh-ins — a goal may itself be a deliberate cut, so hitting it
 *    says nothing about energy availability.
 * 4. **At most [MAX_ADVICE] tips**, ranked by how strongly they explain the observed picture, so the
 *    card stays readable and the most probable cause is on top.
 *
 * Thresholds follow current strength/hypertrophy literature: ~1.6–2.2 g protein per kg body weight,
 * ~10–20 hard sets per muscle per week at 0–3 reps in reserve, ≤ ~0.5–0.75 % body weight lost per
 * week to preserve strength while cutting, and a deload after roughly 4–8 weeks of accumulation.
 */
object ProgressionAdvisor {

    /** Trained weeks required before any verdict other than [ProgressionTrend.INSUFFICIENT_DATA]. */
    const val MIN_TRAINING_WEEKS = 3

    /** Sessions required before any verdict other than [ProgressionTrend.INSUFFICIENT_DATA]. */
    const val MIN_SESSIONS = 6

    /** Index gain (percent) from which the trend counts as real progress. */
    const val PROGRESS_THRESHOLD_PERCENT = 2.0

    /** Index loss (percent) from which the trend counts as a real decline (wider than progress). */
    const val DECLINE_THRESHOLD_PERCENT = -3.0

    /** Body-weight trend (kg/week) at or below which the user is treated as being in a deficit. */
    const val DEFICIT_KG_PER_WEEK = -0.25

    /** Logged-kcal ÷ goal below which intake counts as low — fallback when weigh-ins are missing. */
    const val LOW_KCAL_RATIO = 0.85

    /** Lower end of the protein intake recommended for strength/hypertrophy, in g per kg. */
    const val MIN_PROTEIN_G_PER_KG = 1.6

    /** Sessions per week from which recovery becomes the likely limiter. */
    const val HIGH_FREQUENCY_PER_WEEK = 5.0

    /** Sessions per week below which the stimulus is too sparse to accumulate. */
    const val LOW_FREQUENCY_PER_WEEK = 1.5

    /** Uninterrupted trained weeks after which a deload is worth suggesting. */
    const val DELOAD_AFTER_WEEKS = 8

    /** Hard cap on how many tips a single card shows. */
    const val MAX_ADVICE = 3

    fun evaluate(facts: ProgressionFacts): ProgressionInsight {
        val change = facts.totalChangePercent
        if (change == null || facts.trainingWeeks < MIN_TRAINING_WEEKS || facts.sessions < MIN_SESSIONS) {
            return ProgressionInsight(
                trend = ProgressionTrend.INSUFFICIENT_DATA,
                advice = listOf(ProgressionAdvice.COLLECT_MORE_DATA),
            )
        }

        val trend = when {
            change >= PROGRESS_THRESHOLD_PERCENT -> ProgressionTrend.PROGRESSING
            change <= DECLINE_THRESHOLD_PERCENT -> ProgressionTrend.DECLINING
            else -> ProgressionTrend.MAINTAINING
        }

        // Measured outcome first, logged intake only as a fallback (see rule 3 above).
        val inDeficit = facts.bodyWeightTrendKgPerWeek?.let { it <= DEFICIT_KG_PER_WEEK }
            ?: facts.kcalVsGoal?.let { it < LOW_KCAL_RATIO }
            ?: false
        val lowProtein = facts.proteinGPerKgBodyWeight?.let { it < MIN_PROTEIN_G_PER_KG } == true

        val advice = buildList {
            if (trend == ProgressionTrend.PROGRESSING) {
                add(ProgressionAdvice.KEEP_GOING)
                if (facts.recentChangePercent != null && facts.recentChangePercent < 0.0) {
                    add(ProgressionAdvice.RECENT_STALL)
                }
                if (lowProtein) add(ProgressionAdvice.PROTEIN_LOW)
            } else {
                if (inDeficit) {
                    // Holding strength while losing weight is the expected — and good — outcome;
                    // only an actual decline points at the deficit being too aggressive.
                    add(
                        if (trend == ProgressionTrend.MAINTAINING) ProgressionAdvice.EXPECTED_IN_DEFICIT
                        else ProgressionAdvice.ENERGY_DEFICIT,
                    )
                }
                if (facts.sessionsPerWeek < LOW_FREQUENCY_PER_WEEK) add(ProgressionAdvice.CONSISTENCY)
                if (facts.sessionsPerWeek >= HIGH_FREQUENCY_PER_WEEK) add(ProgressionAdvice.RECOVERY_FREQUENCY)
                if (lowProtein) add(ProgressionAdvice.PROTEIN_LOW)
                if (facts.trainingWeeks >= DELOAD_AFTER_WEEKS) add(ProgressionAdvice.DELOAD)
                // Always last so a stalling user is never left without an actionable tip.
                add(ProgressionAdvice.PROGRESSION_SCHEME)
            }
        }.take(MAX_ADVICE)

        return ProgressionInsight(trend, advice)
    }
}
