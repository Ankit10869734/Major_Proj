package com.moodmix.app.data.provider

import com.moodmix.app.domain.model.Album
import com.moodmix.app.domain.model.Artist
import com.moodmix.app.domain.model.PlayableTrack
import com.moodmix.app.domain.model.Playlist
import com.moodmix.app.domain.model.SearchFilter
import com.moodmix.app.domain.model.SearchQueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Music discovery and catalogue search provider connecting to Apple Music RSS charts and iTunes Search APIs.
 */
class iTunesMusicProvider(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : MusicProvider {

    override val providerName: String = "Apple Music RSS & iTunes Catalog"

    override suspend fun search(query: String, filter: SearchFilter): SearchQueryResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext SearchQueryResult(query = query)

        val encoded = URLEncoder.encode(query, "UTF-8")
        val songs = if (filter == SearchFilter.ALL || filter == SearchFilter.SONGS) fetchSongs("https://itunes.apple.com/search?term=$encoded&entity=song&limit=25") else emptyList()
        val artists = if (filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) fetchArtists("https://itunes.apple.com/search?term=$encoded&entity=musicArtist&limit=10") else emptyList()
        val albums = if (filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) fetchAlbums("https://itunes.apple.com/search?term=$encoded&entity=album&limit=10") else emptyList()

        SearchQueryResult(
            query = query,
            songs = songs.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            playlists = emptyList()
        )
    }

    override suspend fun getTrendingSongs(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        fetchRssTopSongs().distinctBy { it.id }
    }

    override suspend fun getPopularArtists(): List<Artist> = withContext(Dispatchers.IO) {
        fetchRssPopularArtists().distinctBy { it.id }
    }

    override suspend fun getNewReleases(): List<Album> = withContext(Dispatchers.IO) {
        fetchRssNewReleases().distinctBy { it.id }
    }

    override suspend fun getFeaturedPlaylists(): List<Playlist> = withContext(Dispatchers.IO) {
        fetchRssFeaturedPlaylists().distinctBy { it.id }
    }

    override suspend fun getMoodTracks(moodPrompt: String): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val keyword = when {
            moodPrompt.contains("calm", ignoreCase = true) || moodPrompt.contains("study", ignoreCase = true) -> "lofi+piano"
            moodPrompt.contains("energetic", ignoreCase = true) || moodPrompt.contains("workout", ignoreCase = true) -> "electronic+dance"
            moodPrompt.contains("sad", ignoreCase = true) || moodPrompt.contains("piano", ignoreCase = true) -> "acoustic+slow"
            else -> URLEncoder.encode(moodPrompt, "UTF-8")
        }
        fetchSongs("https://itunes.apple.com/search?term=$keyword&entity=song&limit=15").distinctBy { it.id }
    }

    override suspend fun getArtistTopTracks(artistName: String): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(artistName, "UTF-8")
        fetchSongs("https://itunes.apple.com/search?term=$encoded&entity=song&limit=15").distinctBy { it.id }
    }

    override suspend fun getAlbumTracks(albumTitle: String, artistName: String): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode("$albumTitle $artistName", "UTF-8")
        fetchSongs("https://itunes.apple.com/search?term=$encoded&entity=song&limit=20").distinctBy { it.id }
    }

    private fun fetchRssTopSongs(): List<PlayableTrack> {
        return try {
            val url = "https://rss.applemarketingtools.com/api/v2/us/music/most-played/20/songs.json"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val json = JSONObject(body)
                val feed = json.optJSONObject("feed") ?: return emptyList()
                val results = feed.optJSONArray("results") ?: return emptyList()

                val trackIds = mutableListOf<String>()
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    if (id.isNotBlank()) {
                        trackIds.add(id)
                    }
                }

                if (trackIds.isNotEmpty()) {
                    val lookupUrl = "https://itunes.apple.com/lookup?id=${trackIds.joinToString(",")}"
                    fetchSongs(lookupUrl)
                } else {
                    emptyList()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchRssNewReleases(): List<Album> {
        return try {
            val url = "https://rss.applemarketingtools.com/api/v2/us/music/new-releases/15/albums.json"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val json = JSONObject(body)
                val feed = json.optJSONObject("feed") ?: return emptyList()
                val results = feed.optJSONArray("results") ?: return emptyList()
                val list = mutableListOf<Album>()

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    val title = item.optString("name", "")
                    val artist = item.optString("artistName", "Various Artists")
                    val artwork100 = item.optString("artworkUrl100", "")
                    val releaseDate = item.optString("releaseDate", "")
                    val count = item.optInt("trackCount", 0)

                    if (title.isNotBlank()) {
                        val highResArtwork = artwork100.replace("100x100bb", "600x600bb")
                        val year = if (releaseDate.length >= 4) releaseDate.take(4) else null
                        val stableId = if (id.isNotBlank()) "album_$id" else "album_${title.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${abs((title + artist).hashCode())}"
                        list.add(
                            Album(
                                id = stableId,
                                title = title,
                                artist = artist,
                                artworkUrl = highResArtwork.ifBlank { null },
                                releaseYear = year,
                                trackCount = if (count > 0) count else null
                            )
                        )
                    }
                }
                list.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchRssPopularArtists(): List<Artist> {
        return try {
            val url = "https://rss.applemarketingtools.com/api/v2/us/music/most-played/50/songs.json"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val json = JSONObject(body)
                val feed = json.optJSONObject("feed") ?: return emptyList()
                val results = feed.optJSONArray("results") ?: return emptyList()
                val artistMap = LinkedHashMap<String, Pair<String, String>>() // artistName to Pair(artistId, artworkUrl)

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val artistName = item.optString("artistName", "")
                    val artistId = item.optString("artistId", "")
                    val artwork100 = item.optString("artworkUrl100", "")

                    if (artistName.isNotBlank() && !artistMap.containsKey(artistName)) {
                        val highResArtwork = artwork100.replace("100x100bb", "600x600bb")
                        artistMap[artistName] = Pair(artistId, highResArtwork)
                        if (artistMap.size >= 12) break
                    }
                }

                val list = mutableListOf<Artist>()
                for ((name, pair) in artistMap) {
                    val (appleArtistId, artwork) = pair
                    val stableId = if (appleArtistId.isNotBlank()) {
                        "artist_$appleArtistId"
                    } else {
                        "artist_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${abs(name.hashCode())}"
                    }
                    list.add(
                        Artist(
                            id = stableId,
                            name = name,
                            genre = "Music",
                            artworkUrl = artwork.ifBlank { null }
                        )
                    )
                }
                list.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchRssFeaturedPlaylists(): List<Playlist> {
        return try {
            val url = "https://rss.applemarketingtools.com/api/v2/us/music/playlists/10/playlists.json"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val json = JSONObject(body)
                val feed = json.optJSONObject("feed") ?: return emptyList()
                val results = feed.optJSONArray("results") ?: return emptyList()
                val list = mutableListOf<Playlist>()

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val id = item.optString("id", "")
                    val title = item.optString("name", "")
                    val curator = item.optString("artistName", "Apple Music")
                    val artwork100 = item.optString("artworkUrl100", "")
                    val count = item.optInt("trackCount", 0)

                    if (title.isNotBlank()) {
                        val highResArtwork = artwork100.replace("100x100bb", "600x600bb")
                        val stableId = if (id.isNotBlank()) "pl_$id" else "pl_${title.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${abs(title.hashCode())}"
                        list.add(
                            Playlist(
                                id = stableId,
                                title = title,
                                description = "Curated by $curator",
                                artworkUrl = highResArtwork.ifBlank { null },
                                trackCount = if (count > 0) count else null
                            )
                        )
                    }
                }
                list.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchSongs(url: String): List<PlayableTrack> {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return emptyList()
                val list = mutableListOf<PlayableTrack>()

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val trackId = item.optLong("trackId", 0L)
                    val title = item.optString("trackName", "")
                    val artist = item.optString("artistName", "Unknown Artist")
                    val album = item.optString("collectionName", "")
                    val previewUrl = item.optString("previewUrl", "")
                    val artwork100 = item.optString("artworkUrl100", "")
                    val genre = item.optString("primaryGenreName", "Pop")
                    val durationMs = item.optLong("trackTimeMillis", 30000L)

                    if (title.isNotBlank() && previewUrl.isNotBlank()) {
                        val highResArtwork = artwork100.replace("100x100bb", "600x600bb")
                        val stableId = if (trackId > 0) "itunes_$trackId" else "track_${title.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${abs((title + artist).hashCode())}"
                        list.add(
                            PlayableTrack(
                                id = stableId,
                                title = title,
                                artist = artist,
                                album = album,
                                artworkUrl = highResArtwork.ifBlank { null },
                                audioUri = previewUrl,
                                genre = genre,
                                durationMs = durationMs,
                                isPreview = true,
                                isGenerated = false
                            )
                        )
                    }
                }
                list.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchArtists(url: String): List<Artist> {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return emptyList()
                val list = mutableListOf<Artist>()

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val artistId = item.optLong("artistId", 0L)
                    val name = item.optString("artistName", "")
                    val genre = item.optString("primaryGenreName", "Music")

                    if (name.isNotBlank()) {
                        val artistArtwork = fetchArtistArtwork(name)
                        val stableId = if (artistId > 0) "artist_$artistId" else "artist_${name.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${abs(name.hashCode())}"
                        list.add(
                            Artist(
                                id = stableId,
                                name = name,
                                genre = genre,
                                artworkUrl = artistArtwork
                            )
                        )
                    }
                }
                list.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchArtistArtwork(artistName: String): String? {
        return try {
            val encoded = URLEncoder.encode(artistName, "UTF-8")
            val request = Request.Builder().url("https://itunes.apple.com/search?term=$encoded&entity=song&limit=1").build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return null
                if (results.length() > 0) {
                    val first = results.optJSONObject(0) ?: return null
                    val artwork100 = first.optString("artworkUrl100", "")
                    if (artwork100.isNotBlank()) {
                        artwork100.replace("100x100bb", "600x600bb")
                    } else null
                } else null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchAlbums(url: String): List<Album> {
        return try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()

                val json = JSONObject(body)
                val results = json.optJSONArray("results") ?: return emptyList()
                val list = mutableListOf<Album>()

                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val collectionId = item.optLong("collectionId", 0L)
                    val title = item.optString("collectionName", "")
                    val artist = item.optString("artistName", "Unknown")
                    val artwork100 = item.optString("artworkUrl100", "")
                    val releaseDate = item.optString("releaseDate", "")
                    val trackCount = item.optInt("trackCount", 0)

                    if (title.isNotBlank()) {
                        val highResArtwork = artwork100.replace("100x100bb", "600x600bb")
                        val year = if (releaseDate.length >= 4) releaseDate.take(4) else null
                        val stableId = if (collectionId > 0) "album_$collectionId" else "album_${title.lowercase().replace(Regex("[^a-z0-9]"), "_")}_${abs((title + artist).hashCode())}"
                        list.add(
                            Album(
                                id = stableId,
                                title = title,
                                artist = artist,
                                artworkUrl = highResArtwork.ifBlank { null },
                                releaseYear = year,
                                trackCount = if (trackCount > 0) trackCount else null
                            )
                        )
                    }
                }
                list.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
