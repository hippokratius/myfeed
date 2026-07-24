package de.hippokratius.myfeed.core.nextcloud

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewsItemMapperTest {

    private fun item(
        guid: String? = null,
        url: String? = null,
        title: String? = null,
        pubDate: Long? = null,
        body: String? = null,
        enclosureMime: String? = null,
        enclosureLink: String? = null,
        mediaThumbnail: String? = null,
    ) = NewsItem(
        id = 7,
        feedId = 1,
        guid = guid,
        url = url,
        title = title,
        pubDate = pubDate,
        body = body,
        enclosureMime = enclosureMime,
        enclosureLink = enclosureLink,
        mediaThumbnail = mediaThumbnail,
    )

    @Test
    fun `image priority is mediaThumbnail then enclosure then body`() {
        assertEquals(
            "https://t.de/1.jpg",
            NewsItemMapper.imageUrl(
                item(
                    mediaThumbnail = "https://t.de/1.jpg",
                    enclosureMime = "image/jpeg",
                    enclosureLink = "https://e.de/2.jpg",
                    body = """<img src="https://b.de/3.jpg">""",
                ),
            ),
        )
        assertEquals(
            "https://e.de/2.jpg",
            NewsItemMapper.imageUrl(
                item(enclosureMime = "image/jpeg", enclosureLink = "https://e.de/2.jpg", body = """<img src="https://b.de/3.jpg">"""),
            ),
        )
        assertEquals(
            "https://b.de/3.jpg",
            NewsItemMapper.imageUrl(item(body = """<p>Text</p><img src="https://b.de/3.jpg">""")),
        )
    }

    @Test
    fun `non-image enclosure and tracker pixels are skipped`() {
        assertNull(
            NewsItemMapper.imageUrl(item(enclosureMime = "audio/mpeg", enclosureLink = "https://e.de/f.mp3")),
        )
        // Tracker-Pixel im Body werden über die bestehende HtmlText-Logik verworfen.
        assertNull(
            NewsItemMapper.imageUrl(item(body = """<img src="https://vgwort.de/px.gif">""")),
        )
    }

    @Test
    fun `pubDate seconds become millis with now as fallback`() {
        assertEquals(1_367_270_544_000, NewsItemMapper.publishedAtMillis(item(pubDate = 1_367_270_544), nowMillis = 5))
        assertEquals(5, NewsItemMapper.publishedAtMillis(item(), nowMillis = 5))
        assertEquals(5, NewsItemMapper.publishedAtMillis(item(pubDate = 0), nowMillis = 5))
    }

    @Test
    fun `guid falls back to url then synthetic id`() {
        assertEquals("g1", NewsItemMapper.guid(item(guid = "g1", url = "https://u.de")))
        assertEquals("https://u.de", NewsItemMapper.guid(item(guid = " ", url = "https://u.de")))
        assertEquals("nc-7", NewsItemMapper.guid(item()))
    }

    @Test
    fun `title is html-cleaned with url fallback`() {
        assertEquals("Ein Titel", NewsItemMapper.title(item(title = "Ein&nbsp;<b>Titel</b>")))
        assertEquals("https://u.de", NewsItemMapper.title(item(url = "https://u.de")))
    }
}
