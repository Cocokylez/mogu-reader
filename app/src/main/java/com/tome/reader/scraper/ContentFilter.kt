package com.tome.reader.scraper

/**
 * Whether 18+ material may be shown.
 *
 * Scrapers run far from the settings store, and threading a preference through
 * every constructor and call site would touch all of them, so the current value
 * is mirrored here and refreshed whenever settings change.
 */
object ContentFilter {

    @Volatile
    var allowAdult: Boolean = false
        private set

    fun update(allow: Boolean) {
        allowAdult = allow
    }

    /** Sources that are adult-oriented as a whole disappear while 18+ is off. */
    fun isSourceAllowed(scraper: Scraper): Boolean = allowAdult || !scraper.isAdultSource

    /** Titles a source flagged as 18+ are dropped while the setting is off. */
    fun isSeriesAllowed(series: ScrapedSeries): Boolean = allowAdult || !series.isAdult
}
