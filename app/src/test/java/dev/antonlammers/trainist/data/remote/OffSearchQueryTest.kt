package dev.antonlammers.trainist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OffSearchQueryTest {

    // --- query building ---

    @Test
    fun `terms are combined with the country filter`() {
        assertEquals(
            "milch AND countries_tags:\"en:germany\"",
            OffSearchQuery.build("milch", "en:germany"),
        )
    }

    @Test
    fun `without a country tag only the terms are sent`() {
        assertEquals("milch", OffSearchQuery.build("milch", null))
    }

    @Test
    fun `surrounding and repeated whitespace collapses`() {
        assertEquals("griechischer joghurt", OffSearchQuery.build("  griechischer   joghurt ", null))
    }

    @Test
    fun `a blank query produces no request`() {
        assertNull(OffSearchQuery.build("   ", "en:germany"))
    }

    @Test
    fun `a query of nothing but syntax produces no request`() {
        assertNull(OffSearchQuery.build("*:? ()", null))
    }

    @Test
    fun `lucene syntax characters are stripped rather than escaped`() {
        // A user typing a product name with punctuation must not produce a query syntax error. Only
        // actual Lucene syntax goes — "3,5%" is part of the product's name and stays searchable.
        assertEquals("milch 3,5% fett", OffSearchQuery.build("milch (3,5%) fett", null))
        assertEquals("milch fett", OffSearchQuery.build("milch [fett]", null))
        // A boost like "^2" loses its operator and leaves the digit behind as an ordinary term —
        // harmless, and far better than handing Lucene an expression the user never intended.
        assertEquals("milch 2 fett", OffSearchQuery.build("milch^2 fett~", null))
    }

    @Test
    fun `a quote cannot break out of the country filter`() {
        assertEquals(
            "milch AND countries_tags:\"en:germany\"",
            OffSearchQuery.build("milch\"", "en:germany"),
        )
    }

    @Test
    fun `bare boolean operators are dropped so they cannot alter the query`() {
        assertEquals("milch butter", OffSearchQuery.build("milch AND butter", null))
        assertEquals("milch butter", OffSearchQuery.build("milch OR butter", null))
    }

    @Test
    fun `umlauts survive untouched`() {
        // The index matches them; only the accent folding for country slugs strips diacritics.
        assertEquals("hähnchenbrust", OffSearchQuery.build("hähnchenbrust", null))
    }

    // --- country tags ---

    @Test
    fun `common country codes map to their Open Food Facts tag`() {
        assertEquals("en:germany", OffSearchQuery.countryTag("DE"))
        assertEquals("en:france", OffSearchQuery.countryTag("FR"))
        assertEquals("en:spain", OffSearchQuery.countryTag("ES"))
        assertEquals("en:austria", OffSearchQuery.countryTag("AT"))
    }

    @Test
    fun `multi-word countries become dashed slugs`() {
        assertEquals("en:united-kingdom", OffSearchQuery.countryTag("GB"))
        assertEquals("en:united-states", OffSearchQuery.countryTag("US"))
    }

    @Test
    fun `accents and apostrophes fold into the slug`() {
        assertEquals("en:cote-d-ivoire", OffSearchQuery.countryTag("CI"))
    }

    @Test
    fun `a lowercase code still resolves`() {
        assertEquals("en:germany", OffSearchQuery.countryTag("de"))
    }

    @Test
    fun `an unknown or missing region yields no filter`() {
        assertNull(OffSearchQuery.countryTag(""))
        assertNull(OffSearchQuery.countryTag("ZZ"))
        assertNull(OffSearchQuery.countryTag("GERMANY"))
    }
}
