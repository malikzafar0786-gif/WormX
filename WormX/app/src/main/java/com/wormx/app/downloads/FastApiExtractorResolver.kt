package com.wormx.app.downloads

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Calls the FastAPI (yt-dlp-backed) extractor server for a resolved,
 * directly-downloadable URL plus title/thumbnail — this is the "real"
 * engine (per-site extractors, signature solving, etc.) that a generic
 * WebView sniffer can't replicate. See ServerConfig for the endpoint.
 *
 * Fails fast (short timeout) and returns null on any error — including the
 * server simply not running — so the rest of the resolver chain
 * (direct-file check, og:video tag, headless sniffer) still works as a
 * fallback when the backend isn't reachable.
 *
 * Uses org.json (built into Android) rather than adding a new JSON library
 * dependency.
 */
class FastApiExtractorResolver(private val context: Context) : QualityResolver {

    private val client = OkHttpClient.Builder()
        .connectTimeout(50, TimeUnit.SECONDS) // Render's free tier can take 30-40s to wake from sleep
        .readTimeout(50, TimeUnit.SECONDS)
        .build()

    override suspend fun resolve(url: String): List<QualityOption>? = withContext(Dispatchers.IO) {
        try {
            val baseUrl = ServerConfig.getBaseUrl(context)
            val requestJson = JSONObject().put("url", url).toString()
            val body = requestJson.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/extract")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val responseText = response.body?.string() ?: return@withContext null
                val json = JSONObject(responseText)

                if (json.optString("status") != "success") return@withContext null
                val downloadUrl = json.optString("download_url").takeIf { it.isNotBlank() }
                    ?: return@withContext null

                listOf(
                    QualityOption(
                        label = json.optString("title", "Video").ifBlank { "Video" },
                        url = downloadUrl,
                        title = json.optString("title").takeIf { it.isNotBlank() },
                        thumbnailUrl = json.optString("thumbnail").takeIf { it.isNotBlank() },
                        fileExtension = json.optString("ext").takeIf { it.isNotBlank() },
                        referer = url
                    )
                )
            }
        } catch (e: Exception) {
            // Server down/unreachable/timeout/malformed response — treat the
            // same as "this resolver doesn't have an answer" and let the
            // chain fall through, rather than surfacing a scary error for
            // what's often just "forgot to start the laptop server."
            null
        }
    }
}
