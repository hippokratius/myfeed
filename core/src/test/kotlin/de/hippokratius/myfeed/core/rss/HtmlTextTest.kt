package de.hippokratius.myfeed.core.rss

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

    @Test
    fun `skips golem counting pixel and takes real image`() {
        // Golem hängt an jede description einen cpx.php-Zählpixel an.
        assertEquals(
            "https://www.golem.de/2607/artikel-bild.jpg",
            HtmlText.firstImageSrc(
                """Text <img src="https://cpx.golem.de/cpx.php?class=17&aid=1" alt="" />""" +
                    """<img src="https://www.golem.de/2607/artikel-bild.jpg">""",
            ),
        )
        assertNull(HtmlText.firstImageSrc("""Nur Pixel: <img src="https://cpx.golem.de/cpx.php?class=17&aid=1" alt="" />"""))
    }

    @Test
    fun `skips vg wort pixel and one pixel sized images`() {
        assertNull(HtmlText.firstImageSrc("""<img src="https://ssl-vg03.met.vgwort.de/na/abc123" width="1" height="1">"""))
        assertNull(HtmlText.firstImageSrc("""<img width="1" height="1" src="https://example.org/zaehler.gif">"""))
        assertEquals(
            "https://example.org/gross.jpg",
            HtmlText.firstImageSrc("""<img width="1" height="1" src="https://example.org/pixel.gif"><img width="800" src="https://example.org/gross.jpg">"""),
        )
    }
}
