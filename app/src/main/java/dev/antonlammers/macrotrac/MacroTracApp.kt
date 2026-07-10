package dev.antonlammers.macrotrac

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.antonlammers.macrotrac.data.seed.ExerciseCatalogSeeder
import dev.antonlammers.macrotrac.notification.MealReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MacroTracApp : Application() {

    @Inject lateinit var exerciseCatalogSeeder: ExerciseCatalogSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Keep the daily reminder aligned to the next 17:00. The worker itself respects the
        // enable/disable setting, so scheduling unconditionally here is safe.
        MealReminderScheduler.schedule(this)
        // Seed the bundled exercise catalog once per snapshot version (custom exercises preserved).
        appScope.launch { exerciseCatalogSeeder.seedIfNeeded() }
    }
}
