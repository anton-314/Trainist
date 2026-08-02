package dev.antonlammers.trainist.domain.model

/**
 * Anything that can take part in a superset — a planned [TemplateExercise] or a performed
 * [SessionExercise].
 *
 * The two live in different lists and are written by different screens, but the grouping *rules* are
 * identical (see [dev.antonlammers.trainist.domain.Supersets]), and a template's groups are handed
 * straight to the session that starts from it. Sharing one interface is what guarantees the planned
 * grouping and the performed grouping can never drift apart into two near-copies of the same logic.
 */
interface SupersetMember<T : SupersetMember<T>> {
    /** Members of one superset share this id and sit next to each other; null means "stands alone". */
    val supersetGroupId: Int?

    /** This member with a different group id — the one mutation the grouping rules need. */
    fun withSupersetGroupId(groupId: Int?): T
}
