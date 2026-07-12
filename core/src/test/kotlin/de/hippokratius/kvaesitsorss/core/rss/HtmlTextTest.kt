package de.hippokratius.kvaesitsorss.core.rss

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HtmlTextTest {

    @Test
    fun `strips tags and normalizes whitespace`() {
        assertEquals(
            "Hallo Welt",
            HtmlText.clean("<p>Hallo\n   <b>Welt</b></p>"),
        )
    }

    @Test
    fun `decodes named and numeric entities`() {
        assertEquals("Müller & Söhne – 5 €", HtmlText.clean("M&uuml;ller &amp; S&ouml;hne &#8211; 5 &euro;"))
        assertEquals("Ärger", HtmlText.clean("&#xC4;rger"))
    }

    @Test
    fun `decodes double encoded entities`() {
        assertEquals("Q&A", HtmlText.clean("Q&amp;amp;A"))
    }

    @Test
    fun `extracts first http image source`() {
        assertEquals(
            "https://example.org/a.jpg",
            HtmlText.firstImageSrc("""<p><img class="x" src="https://example.org/a.jpg"><img src="https://example.org/b.jpg"></p>"""),
        )
        assertNull(HtmlText.firstImageSrc("<img src=\"data:image/png;base64,AAAA\">"))
        assertNull(HtmlText.firstImageSrc("kein bild"))
        assertNull(HtmlText.firstImageSrc(null))
    }
}
