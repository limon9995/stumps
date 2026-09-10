package com.mdlimonhossain.stumps.data.remote.news

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL

/** One news article, parsed out of the RSS feed's <item> elements. */
data class NewsArticle(
    val title: String,
    val description: String,
    val link: String,
    val imageUrl: String?,
    val pubDate: String?
)

/**
 * Fetches real cricket news from ESPN Cricinfo's public RSS feed — no API key needed, it's a
 * plain, freely-published XML feed anyone can read. This is a genuinely live network call (not a
 * mock/placeholder), parsed with `android.util.Xml`'s pull parser, which is part of the Android
 * SDK itself — no extra networking or XML library dependency needed for this.
 *
 * RSS is just XML in a specific shape: one <channel> containing many <item> elements, each item
 * being one article with a <title>, <description>, <link>, and usually a <pubDate>. This parser
 * walks through the file tag-by-tag ("pull parsing" — we pull the next tag ourselves in a loop,
 * rather than the more memory-hungry approach of loading the whole XML tree into memory at once).
 */
class NewsRepository {
    private val feedUrl = "https://www.espncricinfo.com/rss/content/story/feeds/0.xml"

    /** Fetches and parses the feed. Throws on a network/parsing failure — the caller decides how to show that (see NewsScreen.kt). */
    suspend fun fetchLatest(): List<NewsArticle> = withContext(Dispatchers.IO) {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        // Some servers refuse requests with no User-Agent at all — a plain browser-like one keeps this reliable.
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) StumpsApp")
        try {
            connection.inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(stream, null)
                parseItems(parser)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun parseItems(parser: XmlPullParser): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        var eventType = parser.eventType
        // Fields for whichever <item> we're currently inside — reset every time a new <item> starts.
        var inItem = false
        var title = ""
        var description = ""
        var link = ""
        var imageUrl: String? = null
        var pubDate: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = ""; description = ""; link = ""; imageUrl = null; pubDate = null
                    }
                    "title" -> if (inItem) title = parser.nextTextSafe()
                    "description" -> if (inItem) description = parser.nextTextSafe()
                    "link" -> if (inItem) link = parser.nextTextSafe()
                    "pubDate" -> if (inItem) pubDate = parser.nextTextSafe()
                    // <media:content medium="image" url="..."/> — the article's thumbnail, if the feed includes one.
                    "media:content" -> if (inItem && imageUrl == null) {
                        imageUrl = parser.getAttributeValue(null, "url")
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    inItem = false
                    if (title.isNotBlank() && link.isNotBlank()) {
                        articles.add(NewsArticle(title, description, link, imageUrl, pubDate))
                    }
                }
            }
            eventType = parser.next()
        }
        return articles
    }

    // nextText() throws if the current tag isn't positioned right at a text-only element (e.g. an
    // empty tag) — this wraps that in a safe fallback so one malformed item can't crash the whole feed.
    private fun XmlPullParser.nextTextSafe(): String = runCatching { nextText() }.getOrDefault("")
}
