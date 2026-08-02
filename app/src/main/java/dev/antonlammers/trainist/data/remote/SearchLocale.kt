package dev.antonlammers.trainist.data.remote

import androidx.appcompat.app.AppCompatDelegate
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
    fun current(): SearchLocale
}

/**
 * Reads the per-app locale ([AppCompatDelegate] is the app's single source of truth for it, see the
 * i18n notes) and falls back to the platform default, which is also where the region comes from —
 * the app-language list carries no country.
 */
class AppSearchLocaleProvider @Inject constructor() : SearchLocaleProvider {

    override fun current(): SearchLocale {
        val appLocale = AppCompatDelegate.getApplicationLocales()[0]
        val default = Locale.getDefault()
        return SearchLocale(
            language = (appLocale ?: default).language.ifBlank { "en" },
            countryCode = default.country,
        )
    }
}
