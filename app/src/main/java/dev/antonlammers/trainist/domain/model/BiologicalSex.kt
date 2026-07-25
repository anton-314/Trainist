package dev.antonlammers.trainist.domain.model

/** The two-branch input the Mifflin-St Jeor BMR formula's sex term needs. */
enum class BiologicalSex {
    MALE, FEMALE;

    companion object {
        /** Unlike most enum parses in this app, unparseable input means "no profile", not a default. */
        fun parse(raw: String?): BiologicalSex? =
            raw?.trim()?.uppercase()?.let { v -> entries.firstOrNull { it.name == v } }
    }
}
