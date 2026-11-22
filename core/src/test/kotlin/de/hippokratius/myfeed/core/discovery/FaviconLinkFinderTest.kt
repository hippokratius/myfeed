package de.hippokratius.myfeed.core.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FaviconLinkFinderTest {

    @Test
    fun `origin strips path and keeps scheme host and port`() {
        assertEquals(
            "https://www.heise.de",
            FaviconLinkFinder.origin("https://www.heise.de/rss/heise-atom.xml"),
        )
        assertEquals(
            "http://example.org:8080",
            FaviconLinkFinder.origin("http://example.org:8080/feed.xml"),
        )
        assertNull(FaviconLinkFinder.origin("ftp://example.org/feed"))
        assertNull(FaviconLinkFinder.origin("keine url"))
    }

    @Test
    fun `favicon and manifest urls point at the site root`() {
        assertEquals(
            "https://www.heise.de/favicon.ico",
            FaviconLinkFinder.originFaviconUrl("https://www.heise.de/rss/heise-atom.xml"),
        )
        assertEquals(
            "https://www.derstandard.at/manifest.json",
            FaviconLinkFinder.manifestUrl("https://www.derstandard.at/rss"),
        )
    }

    @Test
    fun `prefers apple-touch-icon png over ico and svg`() {
        val html = """
            <html><head>
            <link rel="manifest" href="/manifest.json">
            <link rel="icon" href="/favicon.ico"/>
            <link rel="apple-touch-icon" href="/icons/apple-touch-icon-180x180.png" sizes="180x180"/>
            <link rel="icon" href="/icons/favicon-16x16.png" sizes="16x16" type="image/png"/>
            <link rel="icon" href="/icons/favicon.svg" type="image/svg+xml"/>
            </head></html>
        """.trimIndent()

        assertEquals(
            "https://www.heise.de/icons/apple-touch-icon-180x180.png",
            FaviconLinkFinder.iconFromHtml(html, "https://www.heise.de/"),
        )
    }

    @Test
    fun `resolves relative and protocol-relative hrefs`() {
        val html = """<head><link rel="icon" href="//cdn.example.org/fav.png"></head>"""
        assertEquals(
            "https://cdn.example.org/fav.png",
            FaviconLinkFinder.iconFromHtml(html, "https://www.example.org/"),
        )
    }

    @Test
    fun `respects base href`() {
        val html = """<head><base href="https://static.example.org/"><link rel="icon" href="fav.png"></head>"""
        assertEquals(
            "https://static.example.org/fav.png",
            FaviconLinkFinder.iconFromHtml(html, "https://www.example.org/"),
        )
    }

    @Test
    fun `returns null without icon links`() {
        assertNull(FaviconLinkFinder.iconFromHtml("<html><head></head></html>", "https://example.org/"))
    }

    @Test
    fun `manifest prefers 192 png over 512`() {
        val manifest = """
            {
              "name": "DER STANDARD",
              "icons": [
                {"src": "https://b.staticfiles.at/s/icons/nachrichten/android-chrome-512x512.png", "type": "image/png", "sizes": "512x512"},
                {"src": "https://b.staticfiles.at/s/icons/nachrichten/android-chrome-192x192.png", "type": "image/png", "sizes": "192x192"}
              ]
            }
        """.trimIndent()

        assertEquals(
            "https://b.staticfiles.at/s/icons/nachrichten/android-chrome-192x192.png",
            FaviconLinkFinder.iconFromManifest(manifest, "https://www.derstandard.at/manifest.json"),
        )
    }

    @Test
    fun `manifest with relative src resolves against manifest url`() {
        val manifest = """{"icons":[{"src": "icons/icon-192.png", "sizes": "192x192", "type": "image/png"}]}"""
        assertEquals(
            "https://www.example.org/icons/icon-192.png",
            FaviconLinkFinder.iconFromManifest(manifest, "https://www.example.org/manifest.json"),
        )
    }

    @Test
    fun `manifest without icons array returns null`() {
        assertNull(FaviconLinkFinder.iconFromManifest("""{"name":"x"}""", "https://example.org/manifest.json"))
    }
}
