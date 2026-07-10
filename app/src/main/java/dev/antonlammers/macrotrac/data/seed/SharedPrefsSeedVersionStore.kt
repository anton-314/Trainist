package dev.antonlammers.macrotrac.data.seed

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** SharedPreferences-backed [SeedVersionStore] (own prefs file, separate from app settings). */
class SharedPrefsSeedVersionStore @Inject constructor(
    @ApplicationContext context: Context,
) : SeedVersionStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun seededVersion(): Int = prefs.getInt(KEY_SEEDED_VERSION, 0)

    override suspend fun setSeededVersion(version: Int) {
        prefs.edit { putInt(KEY_SEEDED_VERSION, version) }
    }

    private companion object {
        const val PREFS_NAME = "macrotrac_catalog"
        const val KEY_SEEDED_VERSION = "seeded_exercise_snapshot_version"
    }
}
