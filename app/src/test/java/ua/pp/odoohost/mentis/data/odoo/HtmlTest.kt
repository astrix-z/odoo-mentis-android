package ua.pp.odoohost.mentis.data.odoo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTest {
    @Test
    fun `plain text becomes paragraphs`() {
        assertEquals("<p>a &amp; b</p><p><br></p><p>c</p>", Html.fromPlainText("a & b\n\nc\n"))
        assertEquals("", Html.fromPlainText("  "))
    }

    @Test
    fun `rich formatting is detected`() {
        assertFalse(Html.hasRichFormatting("<p>Hi <b>there</b><br></p>"))
        assertTrue(Html.hasRichFormatting("<ul><li>one</li></ul>"))
        assertTrue(Html.hasRichFormatting("<p><img src=\"/web/image/1\"></p>"))
    }
}
