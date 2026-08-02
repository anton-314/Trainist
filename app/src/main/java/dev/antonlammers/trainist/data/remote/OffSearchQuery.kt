package dev.antonlammers.trainist.data.remote

import java.text.Normalizer
import java.util.Locale

/**
 * Builds the Lucene query string Open Food Facts' search service expects, and the country tag that
 * keeps its results relevant.
 *
 * **Why a country filter at all.** The search index is worldwide and its relevance scoring alone is
 * not selective enough: searching "skyr" unfiltered returns products from the Netherlands, the UK,
 * Denmark and Iceland interleaved, and "milch" ranks an Austrian *skyr* above actual milk. Filtering
 * on `countries_tags` is what turns that into a list of products the user can actually buy — verified
 * against the live API for German, French and Spanish queries alike.
 *
 * **Why no popularity sort.** `sort_by=-unique_scans_n` looks tempting and is actively harmful: it
 * discards the relevance score, so "milch" comes back as Lindt chocolate and Alpro oat drink — the
 * most-scanned products that merely match somewhere. The service's default (relevance) is correct.
 *
 * Pure and Android-free, so the whole query shape is unit-testable without a device.
 */
object OffSearchQuery {

    /**
     * The query for [terms], restricted to [countryTag] when one is known.
     *
     * User input is stripped of Lucene's operator characters rather than escaped: a query is a few
     * words typed into a search field, never a hand-written expression, so a stray `:` or unbalanced
     * `(` should quietly mean nothing instead of producing a syntax error the user cannot decode.
     */
    fun build(terms: String, countryTag: String?): String? {
        val sanitized = sanitizeTerms(terms)
        if (sanitized.isEmpty()) return null
        return if (countryTag == null) sanitized else "$sanitized AND countries_tags:\"$countryTag\""
    }

    /** Strips Lucene syntax and bare boolean operators, collapsing what is left into plain terms. */
    private fun sanitizeTerms(terms: String): String = terms
        .map { if (it in LUCENE_SYNTAX_CHARS) ' ' else it }
        .joinToString("")
        .split(WHITESPACE)
        .filter { it.isNotBlank() && it.uppercase(Locale.ROOT) !in BOOLEAN_OPERATORS }
        .joinToString(" ")

    /**
     * The Open Food Facts country tag for an ISO 3166-1 country code (`"DE"` → `"en:germany"`), or
     * null when the code is empty or unknown — in which case the search simply runs unfiltered.
     *
     * The tags are the English country names, slugified. Deriving them from [Locale] rather than
     * hard-coding a 200-entry table means every country works, including the awkward ones: `CI` →
     * "Côte d'Ivoire" → `en:cote-d-ivoire`, `GB` → `en:united-kingdom`, `US` → `en:united-states`.
     */
    fun countryTag(countryCode: String): String? {
        val code = countryCode.trim().uppercase(Locale.ROOT)
        // Checking against the ISO list rather than the display name: an unassigned code like "ZZ"
        // does not fail, it resolves to the literal name "Unknown Region" — which would otherwise be
        // slugified into a filter that silently matches nothing.
        if (code !in ISO_COUNTRIES) return null
        val englishName = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
        if (englishName.isBlank()) return null
        return "en:" + slugify(englishName)
    }

    /** "Côte d'Ivoire" → "cote-d-ivoire": accents folded away, everything else collapsed to dashes. */
    private fun slugify(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ENGLISH)
        .map { if (it in 'a'..'z' || it in '0'..'9') it else '-' }
        .joinToString("")
        .split('-')
        .filter { it.isNotEmpty() }
        .joinToString("-")

    private val ISO_COUNTRIES: Set<String> = Locale.getISOCountries().toSet()

    private val WHITESPACE = Regex("\\s+")
    private val DIACRITICS = Regex("\\p{Mn}+")

    /** Everything Lucene's query parser treats as syntax; harmless to drop from typed search terms. */
    private val LUCENE_SYNTAX_CHARS = setOf(
        '+', '-', '&', '|', '!', '(', ')', '{', '}', '[', ']', '^', '"', '~', '*', '?', ':', '\\', '/',
    )

    private val BOOLEAN_OPERATORS = setOf("AND", "OR", "NOT", "TO")
}
