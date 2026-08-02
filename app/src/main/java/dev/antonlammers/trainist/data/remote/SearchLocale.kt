package dev.antonlammers.trainist.data.remote

import android.content.Context
import android.telephony.TelephonyManager
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.antonlammers.trainist.domain.repository.SettingsRepository
import java.util.Locale
import javax.inject.Inject

/**
 * The two locale facts a food search needs: which language to rank in, and which country's products
 * to show. They come from different places on purpose — [language] follows the **app's** language
 * setting, [countryCode] the **device's** region, because someone using the app in English while
 * living in Germany still shops in German supermarkets.
 */
data class SearchLocale(val language: String, val countryCode: String)

/**
 * Interface seam so [dev.antonlammers.trainist.data.repository.FoodSearchRepositoryImpl] stays
 * reachable by a plain JVM test — resolving the locale touches AppCompat, which a unit test has no
 * access to.
 */
interface SearchLocaleProvider {
    suspend fun current(): SearchLocale
}

/**
 * Picks which country's shelf the food search should show, in order of how well each signal answers
 * "where does this person actually buy groceries". Pure and Android-free so the priority itself is
 * testable — the Android APIs behind each signal are not.
 */
object CountryResolution {

    /**
     * @param override the user's explicit choice in Settings; wins outright when set.
     * @param networkCountry the mobile network currently attached to — the best automatic signal,
     *   because it follows the user when they move rather than describing where they came from.
     * @param simCountry the SIM's home country; used when there is no network reading (no signal,
     *   flight mode), and still far better than a language setting.
     * @param deviceRegion the region of the device locale. Last, because it is a *language*
     *   preference wearing a country: someone who set their phone to "English (United States)" while
     *   living in Berlin is not shopping in American supermarkets.
     * @return an ISO 3166-1 alpha-2 code, or null when nothing usable is known (search runs
     *   worldwide then).
     */
    fun resolve(
        override: String?,
        networkCountry: String?,
        simCountry: String?,
        deviceRegion: String?,
    ): String? = listOf(override, networkCountry, simCountry, deviceRegion)
        .firstOrNull { !it.isNullOrBlank() }
        ?.uppercase(Locale.ROOT)
}

/**
 * Resolves the search locale from the device: language from the per-app locale ([AppCompatDelegate]
 * is the app's single source of truth for it, see the i18n notes), country from the user's override
 * or, failing that, the telephony signals — see [CountryResolution] for the priority and why.
 *
 * Neither telephony call needs a permission, and both are simply empty on a Wi-Fi-only device.
 */
class AppSearchLocaleProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
) : SearchLocaleProvider {

    override suspend fun current(): SearchLocale {
        val appLocale = AppCompatDelegate.getApplicationLocales()[0]
        val default = Locale.getDefault()
        val telephony = context.getSystemService(TelephonyManager::class.java)
        return SearchLocale(
            language = (appLocale ?: default).language.ifBlank { "en" },
            countryCode = CountryResolution.resolve(
                override = settings.getSearchCountry(),
                networkCountry = telephony?.networkCountryIso,
                simCountry = telephony?.simCountryIso,
                deviceRegion = default.country,
            ).orEmpty(),
        )
    }
}
