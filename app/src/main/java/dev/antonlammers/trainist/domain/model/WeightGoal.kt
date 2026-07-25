package dev.antonlammers.trainist.domain.model

/**
 * How far the BMR calculator's recommendation deviates from TDEE, as a **fraction of TDEE** rather
 * than a fixed kcal amount. Not persisted — the profile that IS persisted ([BmrProfile]) is the
 * input side, this is chosen fresh each run.
 *
 * Why a fraction: the same absolute deficit is a very different intervention depending on body
 * size. The clinical 500 kcal/day figure (NIH/AHA obesity guideline, from the "3500 kcal ≈ 1 lb"
 * rule) is ~17 % of a 100 kg man's 3000 kcal TDEE but ~29 % of a 55 kg woman's 1750 — for her it
 * means roughly 0.8 % of body weight per week, well past the 0.5–0.75 % that the literature
 * associates with holding onto muscle, and it lands her intake at the BMR floor where hitting
 * 1.6–2.2 g protein/kg gets tight. A fraction self-scales and keeps the recommendation consistent
 * with what [dev.antonlammers.trainist.domain.ProgressionAdvisor] later judges the user against.
 *
 * The bounds are guard rails at the extremes: [minDeltaKcal] stops a very large TDEE from turning
 * 18 % into a crash diet, [maxDeltaKcal] stops a very small one from producing a change that
 * disappears in day-to-day body-weight noise (±1 kg).
 */
enum class WeightGoal(
    val tdeeFraction: Double,
    val minDeltaKcal: Double,
    val maxDeltaKcal: Double,
) {
    LOSE(tdeeFraction = -0.18, minDeltaKcal = -700.0, maxDeltaKcal = -250.0),
    MAINTAIN(tdeeFraction = 0.0, minDeltaKcal = 0.0, maxDeltaKcal = 0.0),

    // Deliberately gentler than the deficit: surplus beyond what the body can turn into muscle
    // is simply stored as fat, so a lean gain is a small one.
    GAIN(tdeeFraction = 0.10, minDeltaKcal = 150.0, maxDeltaKcal = 400.0),
}
