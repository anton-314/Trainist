package dev.antonlammers.trainist.domain.model

/**
 * Kcal/day adjustment applied to TDEE for the BMR calculator's goal step. Not persisted — the
 * profile that IS persisted ([BmrProfile]) is the input side, this is chosen fresh each run.
 */
enum class WeightGoal(val kcalDelta: Double) {
    LOSE(-500.0),
    MAINTAIN(0.0),
    GAIN(350.0),
}
