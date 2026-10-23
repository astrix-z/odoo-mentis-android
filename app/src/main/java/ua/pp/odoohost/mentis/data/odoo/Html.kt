package ua.pp.odoohost.mentis.data.odoo

/** The little HTML handling the app needs for Odoo's html fields and chatter bodies. */
object Html {
    private val SIMPLE_TAGS = setOf("p", "br", "div", "span", "b", "strong", "i", "em", "u")
    private val TAG = Regex("""<\s*/?\s*([a-zA-Z0-9]+)""")

    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** One paragraph per line, as Odoo's editor stores typed text. */
    fun fromPlainText(text: String): String {
        val lines = text.trim().lines()
        if (lines.singleOrNull()?.isEmpty() == true) return ""
        return lines.joinToString("") { if (it.isBlank()) "<p><br></p>" else "<p>${escape(it)}</p>" }
    }

    /** True when the HTML has more than paragraphs and inline emphasis: lists, tables, images, links... */
    fun hasRichFormatting(html: String): Boolean =
        TAG.findAll(html).any { it.groupValues[1].lowercase() !in SIMPLE_TAGS }
}
