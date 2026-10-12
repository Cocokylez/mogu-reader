package com.tome.reader.scraper

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Turns a low-level network failure into something the reader can act on.
 *
 * Most source failures are not the app's fault and not the site's either — they
 * are the user's network blocking the request (DNS filtering, a VPN, a school or
 * office firewall, or Cloudflare wanting a browser check). Surfacing
 * `UnknownHostException: toonily.com` helps nobody, so each case says plainly
 * what is wrong and what to do about it.
 */
object ScraperFailure {

    /**
     * True when the only way forward is the source's own website: a Cloudflare
     * check the reader has to clear, or a publisher that serves its chapters
     * through its own viewer. Callers use this to send the reader straight to the
     * in-app browser instead of showing a dead end they cannot act on.
     */
    fun needsWebsite(error: Throwable?): Boolean {
        val message = error?.message.orEmpty()
        if (message.isBlank()) return false
        return message.contains("Cloudflare", ignoreCase = true) ||
            message.contains("browser access", ignoreCase = true) ||
            message.contains("cannot be imported", ignoreCase = true) ||
            message.contains("in the in-app browser", ignoreCase = true)
    }

    fun humanize(error: Throwable, sourceName: String): String {
        val message = error.message.orEmpty()

        return when {
            // Cloudflare's interstitial — solvable by loading the site once in the
            // in-app browser so the clearance cookie is issued.
            message.contains("Cloudflare", ignoreCase = true) ->
                "$sourceName is behind a Cloudflare check. Open it once in the in-app browser " +
                    "to clear the check, then retry. If it keeps failing, a VPN or DNS filter " +
                    "on your network is likely blocking it."

            // The host did not resolve: almost always DNS-level blocking.
            error is UnknownHostException ->
                "Can't reach $sourceName — the address didn't resolve. Your network is blocking " +
                    "it: check your DNS (Pi-hole, AdGuard, NextDNS), your VPN, or your router's " +
                    "firewall, then retry."

            error is SSLException ->
                "Secure connection to $sourceName failed. A firewall or filtering proxy is " +
                    "likely intercepting the connection — disable it for this app and retry."

            error is SocketTimeoutException || message.contains("timeout", ignoreCase = true) ->
                "$sourceName timed out. It may be slow right now, or your network is throttling " +
                    "or blocking it. Check your connection or VPN and retry."

            message.contains("requires browser access", ignoreCase = true) ||
                message.contains("403") || message.contains("401") ->
                "$sourceName refused a direct request. Open it in the in-app browser to continue."

            error is IOException && message.isNotBlank() ->
                "$sourceName is unreachable: $message. If other sources work, this one is " +
                    "blocked or down; if none work, check your network or firewall."

            message.isNotBlank() -> "$sourceName failed: ${message.take(160)}"

            else -> "$sourceName failed for an unknown reason. Check your network and retry."
        }
    }
}
