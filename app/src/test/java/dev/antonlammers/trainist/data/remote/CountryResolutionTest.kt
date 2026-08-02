package dev.antonlammers.trainist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryResolutionTest {

    @Test
    fun `the user's own choice beats every detected signal`() {
        assertEquals(
            "ES",
            CountryResolution.resolve(
                override = "ES",
                networkCountry = "de",
                simCountry = "de",
                deviceRegion = "US",
            ),
        )
    }

    @Test
    fun `without an override the mobile network decides`() {
        // Someone who moved abroad shops where they are, not where their SIM was bought.
        assertEquals(
            "ES",
            CountryResolution.resolve(
                override = null,
                networkCountry = "es",
                simCountry = "de",
                deviceRegion = "DE",
            ),
        )
    }

    @Test
    fun `the SIM stands in when there is no network reading`() {
        // Flight mode or no reception: still a far better guess than a language setting.
        assertEquals(
            "DE",
            CountryResolution.resolve(
                override = null,
                networkCountry = "",
                simCountry = "de",
                deviceRegion = "US",
            ),
        )
    }

    @Test
    fun `the device region is the last resort`() {
        // A Wi-Fi-only tablet has neither telephony signal.
        assertEquals(
            "AT",
            CountryResolution.resolve(
                override = null,
                networkCountry = null,
                simCountry = null,
                deviceRegion = "AT",
            ),
        )
    }

    @Test
    fun `the device region losing to telephony is the whole point`() {
        // The case the setting exists for: a phone set to "English (United States)" in Berlin.
        assertEquals(
            "DE",
            CountryResolution.resolve(
                override = null,
                networkCountry = "de",
                simCountry = null,
                deviceRegion = "US",
            ),
        )
    }

    @Test
    fun `codes are normalized to upper case`() {
        // Telephony hands them back lower-case; OffSearchQuery expects a clean ISO code.
        assertEquals("FR", CountryResolution.resolve(null, "fr", null, null))
    }

    @Test
    fun `nothing known means no country filter at all`() {
        assertNull(CountryResolution.resolve(null, null, null, null))
        assertNull(CountryResolution.resolve(null, "", "", ""))
    }

    @Test
    fun `a blank signal is skipped rather than treated as an answer`() {
        assertEquals("DE", CountryResolution.resolve(null, "  ", null, "de"))
    }
}
