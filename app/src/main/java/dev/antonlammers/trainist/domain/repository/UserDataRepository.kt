package dev.antonlammers.trainist.domain.repository

/** Whether the database already holds anything the user created — tells a restored install from a fresh one. */
interface UserDataRepository {
    /**
     * True as soon as any diary, training or body data exists (food entries, weigh-ins, measurements,
     * custom foods, saved meals, custom exercises, templates or sessions). The daily goal and the
     * seeded exercise catalog don't count — neither is something the user logged.
     */
    suspend fun hasUserData(): Boolean
}
