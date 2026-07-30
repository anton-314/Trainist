package dev.antonlammers.trainist.domain.model

/** A trackable body-measurement location, in cm. Display names live in the UI layer. */
enum class MeasurementType {
    NECK, CHEST, WAIST, HIPS, BICEPS, THIGH, CALF;

    companion object {
        fun parse(raw: String?): MeasurementType? =
            raw?.trim()?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
