package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

data class YouTubeVideoMetadata(
    val videoId: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String
)

object YouTubeService {
    private const val TAG = "YouTubeService"
    private val client = OkHttpClient()

    fun extractVideoId(url: String): String? {
        val cleanUrl = url.trim()
        // Enforce strict regex sanitization on URL
        val pattern = "^(?:https?:\\/\\/)?(?:www\\.)?(?:youtube\\.com\\/(?:watch\\?v=|embed\\/|v\\/|shorts\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})(?:\\S+)?$"
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val matchResult = regex.find(cleanUrl)
        return matchResult?.groupValues?.get(1)
    }

    suspend fun fetchVideoMetadata(url: String): Result<YouTubeVideoMetadata> = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid YouTube URL. Please provide a valid YouTube watch link."))

        // 1. Try YouTube Data API v3 if key is configured
        val apiKey = try {
            BuildConfig.YOUTUBE_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNotEmpty() && apiKey != "MY_YOUTUBE_API_KEY" && apiKey != "YOUTUBE_API_KEY") {
            try {
                val requestUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet&id=$videoId&key=$apiKey"
                val request = Request.Builder().url(requestUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val items = json.optJSONArray("items")
                            if (items != null && items.length() > 0) {
                                val snippet = items.getJSONObject(0).getJSONObject("snippet")
                                val title = snippet.optString("title", "No Title")
                                val description = snippet.optString("description", "No Description")
                                val thumbnails = snippet.optJSONObject("thumbnails")
                                var thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                if (thumbnails != null) {
                                    val high = thumbnails.optJSONObject("high")
                                        ?: thumbnails.optJSONObject("medium")
                                        ?: thumbnails.optJSONObject("default")
                                    if (high != null) {
                                        thumbnailUrl = high.optString("url", thumbnailUrl)
                                    }
                                }
                                return@withContext Result.success(
                                    YouTubeVideoMetadata(videoId, title, description, thumbnailUrl)
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching from YouTube Data API, falling back to OEmbed", e)
            }
        }

        // 2. Fallback: YouTube OEmbed API (Always works without key!)
        try {
            val encodedUrl = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val requestUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"
            val request = Request.Builder().url(requestUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val title = json.optString("title", "YouTube Video")
                        val author = json.optString("author_name", "Unknown Author")
                        val thumbnailUrl = json.optString("thumbnail_url", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")
                        return@withContext Result.success(
                            YouTubeVideoMetadata(
                                videoId = videoId,
                                title = title,
                                description = "Study content by $author. Click to open and play.",
                                thumbnailUrl = thumbnailUrl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching from YouTube OEmbed API", e)
        }

        // 3. Fallback: Local estimation if offline or both APIs fail
        return@withContext Result.success(
            YouTubeVideoMetadata(
                videoId = videoId,
                title = "Study Video ($videoId)",
                description = "Self-study educational content. Select to open the interactive player.",
                thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
            )
        )
    }
}
