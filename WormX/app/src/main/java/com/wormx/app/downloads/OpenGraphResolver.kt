package com.wormx.app.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Many sites (Facebook public posts, most blogs/news sites, plenty of others)
 * embed their real video URL directly in the page's HTML as an Open Graph
 * meta tag — originally meant for link-preview cards, but it's a fast,
 * reliable way to grab the actual media URL without opening any visible
 * browser or waiting for a page to fully render. This is the "few seconds,
 * no browser window" path.
 *
 * Falls through (returns null) if the page doesn't expose one — the caller
 * moves on to [HeadlessVideoSniffer] as a fallback.
 */
class OpenGraphResolver : QualityResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    companion object {
        private val META_VIDEO_PATTERNS = listOf(
            Regex("""<meta[^>]+property=["']og:video:secure_url["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video:secure_url["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+property=["']og:video["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:video["']""", RegexOption.IGNORE_CASE)
        )
    }

    override suspend fun resolve(url: String): List<QualityOption>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) WormX")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val contentType = response.header("Content-Type") ?: ""
                if (!contentType.startsWith("text/html")) return@withContext null

                // Cap how much HTML we read — the meta tags we need are
                // always in <head>, near the top of the document.
                val html = response.body?.string()?.take(200_000) ?: return@withContext null

                for (pattern in META_VIDEO_PATTERNS) {
                    val match = pattern.find(html)
                    if (match != null) {
                        val videoUrl = match.groupValues[1]
                            .replace("&amp;", "&")
                        return@withContext listOf(QualityOption(label = "Original", url = videoUrl))
                    }
                }
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
