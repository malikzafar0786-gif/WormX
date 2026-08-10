package com.wormx.app.downloads

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Turns a pasted link into one or more downloadable [QualityOption]s.
 *
 * WormX ships with [DirectLinkResolver], which handles any URL that already
 * points straight at a file (a CDN link, a "download" button href, a direct
 * .mp4/.jpg/.pdf link, etc.) by reading its headers.
 *
 * Social platforms that only expose *page* URLs (not the underlying media
 * URL) need their own resolver behind this same interface — e.g. one that
 * calls a self-hosted extraction backend you control and trust. That
 * platform-specific extraction logic is intentionally not included here:
 * write and maintain it yourself against each platform's current, official
 * surface (their own APIs/oEmbed endpoints where available), since scraping
 * private endpoints tends to break without notice and can conflict with a
 * platform's terms of service.
 */
interface QualityResolver {
    /** Returns null if this resolver doesn't know how to handle [url]. */
    suspend fun resolve(url: String): List<QualityOption>?
}

class DirectLinkResolver : QualityResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun resolve(url: String): List<QualityOption>? {
        return try {
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val size = response.header("Content-Length")?.toLongOrNull() ?: -1L
                val contentType = response.header("Content-Type") ?: ""
                val isAudio = contentType.startsWith("audio/")
                listOf(QualityOption(label = "Original", url = url, approxSizeBytes = size, isAudioOnly = isAudio))
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Tries each registered resolver in order and returns the first match. */
class QualityResolverRegistry(private val resolvers: List<QualityResolver> = listOf(DirectLinkResolver())) {
    suspend fun resolve(url: String): List<QualityOption> {
        for (resolver in resolvers) {
            resolver.resolve(url)?.let { return it }
        }
        // Fall back to treating the raw URL as a single option so downloads never dead-end.
        return listOf(QualityOption(label = "Default", url = url))
    }
}
