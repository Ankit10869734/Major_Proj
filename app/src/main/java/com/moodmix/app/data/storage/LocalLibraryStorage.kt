package com.moodmix.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.moodmix.app.domain.model.PlayableTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalLibraryStorage(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "moodmix_library.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_LIKED = "liked_tracks"
        private const val TABLE_HISTORY = "listening_history"
        private const val TABLE_SEARCH_HISTORY = "search_history"
        private const val TABLE_GENERATED = "generated_tracks"
        private const val TABLE_PLAYLISTS = "playlists"
        private const val TABLE_PLAYLIST_TRACKS = "playlist_tracks"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_LIKED (
                id TEXT PRIMARY KEY,
                title TEXT,
                artist TEXT,
                album TEXT,
                artwork_url TEXT,
                audio_uri TEXT,
                genre TEXT,
                duration_ms INTEGER,
                is_preview INTEGER,
                is_generated INTEGER,
                added_time INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_HISTORY (
                id TEXT,
                title TEXT,
                artist TEXT,
                album TEXT,
                artwork_url TEXT,
                audio_uri TEXT,
                genre TEXT,
                duration_ms INTEGER,
                is_preview INTEGER,
                is_generated INTEGER,
                played_time INTEGER,
                PRIMARY KEY (id, played_time)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SEARCH_HISTORY (
                search_query TEXT PRIMARY KEY,
                searched_time INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_GENERATED (
                file_name TEXT PRIMARY KEY,
                title TEXT,
                prompt TEXT,
                lyrics TEXT,
                file_path TEXT,
                created_time INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLISTS (
                id TEXT PRIMARY KEY,
                title TEXT,
                description TEXT,
                artwork_url TEXT,
                created_time INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PLAYLIST_TRACKS (
                playlist_id TEXT,
                track_id TEXT,
                title TEXT,
                artist TEXT,
                audio_uri TEXT,
                artwork_url TEXT,
                added_time INTEGER,
                PRIMARY KEY (playlist_id, track_id)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_LIKED")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SEARCH_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_GENERATED")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYLISTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PLAYLIST_TRACKS")
        onCreate(db)
    }

    suspend fun migrateAndGetGeneratedTracks(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val persistentDir = File(context.filesDir, "generated_music")
        if (!persistentDir.exists()) {
            persistentDir.mkdirs()
        }

        // Migrate cached files from all potential cache directories to persistent storage
        val cacheFolderNames = listOf("moodmix_generated", "generated_audio")
        for (folderName in cacheFolderNames) {
            val cacheDir = File(context.cacheDir, folderName)
            if (cacheDir.exists() && cacheDir.isDirectory) {
                val cachedFiles = cacheDir.listFiles { _, name -> name.endsWith(".wav", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true) }.orEmpty()
                for (file in cachedFiles) {
                    if (file.length() > 0L) {
                        val targetFile = File(persistentDir, file.name)
                        if (!targetFile.exists()) {
                            file.copyTo(targetFile, overwrite = true)
                        }
                    }
                }
            }
        }

        // List all persistent generated tracks
        val persistentFiles = persistentDir.listFiles { _, name -> name.endsWith(".wav", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        persistentFiles.map { file ->
            val isMp3 = file.name.endsWith(".mp3", ignoreCase = true)
            val genreLabel = if (isMp3) "MP3 AI Audio" else "ACE-Step WAV"
            PlayableTrack(
                id = file.name,
                title = file.nameWithoutExtension.replace('_', ' '),
                artist = "MoodMix AI",
                album = "Generated Creations",
                artworkUrl = null,
                audioUri = Uri.fromFile(file).toString(),
                genre = genreLabel,
                durationMs = 30000L,
                isPreview = false,
                isGenerated = true,
                addedTimestamp = file.lastModified()
            )
        }
    }

    suspend fun saveLikedTrack(track: PlayableTrack) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("artwork_url", track.artworkUrl)
            put("audio_uri", track.audioUri)
            put("genre", track.genre)
            put("duration_ms", track.durationMs)
            put("is_preview", if (track.isPreview) 1 else 0)
            put("is_generated", if (track.isGenerated) 1 else 0)
            put("added_time", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_LIKED, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun removeLikedTrack(trackId: String) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_LIKED, "id = ?", arrayOf(trackId))
    }

    suspend fun isTrackLiked(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(TABLE_LIKED, arrayOf("id"), "id = ?", arrayOf(trackId), null, null, null)
        val exists = cursor.use { it.moveToFirst() }
        exists
    }

    suspend fun getLikedTracks(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(TABLE_LIKED, null, null, null, null, null, "added_time DESC")
        val list = mutableListOf<PlayableTrack>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PlayableTrack(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        artist = it.getString(it.getColumnIndexOrThrow("artist")),
                        album = it.getString(it.getColumnIndexOrThrow("album")),
                        artworkUrl = it.getString(it.getColumnIndexOrThrow("artwork_url")),
                        audioUri = it.getString(it.getColumnIndexOrThrow("audio_uri")),
                        genre = it.getString(it.getColumnIndexOrThrow("genre")),
                        durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                        isPreview = it.getInt(it.getColumnIndexOrThrow("is_preview")) == 1,
                        isGenerated = it.getInt(it.getColumnIndexOrThrow("is_generated")) == 1,
                        isLiked = true,
                        addedTimestamp = it.getLong(it.getColumnIndexOrThrow("added_time"))
                    )
                )
            }
        }
        list
    }

    suspend fun recordPlayedTrack(track: PlayableTrack) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("artwork_url", track.artworkUrl)
            put("audio_uri", track.audioUri)
            put("genre", track.genre)
            put("duration_ms", track.durationMs)
            put("is_preview", if (track.isPreview) 1 else 0)
            put("is_generated", if (track.isGenerated) 1 else 0)
            put("played_time", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getRecentlyPlayedTracks(): List<PlayableTrack> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(TABLE_HISTORY, null, null, null, null, null, "played_time DESC", "20")
        val list = mutableListOf<PlayableTrack>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PlayableTrack(
                        id = it.getString(it.getColumnIndexOrThrow("id")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        artist = it.getString(it.getColumnIndexOrThrow("artist")),
                        album = it.getString(it.getColumnIndexOrThrow("album")),
                        artworkUrl = it.getString(it.getColumnIndexOrThrow("artwork_url")),
                        audioUri = it.getString(it.getColumnIndexOrThrow("audio_uri")),
                        genre = it.getString(it.getColumnIndexOrThrow("genre")),
                        durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                        isPreview = it.getInt(it.getColumnIndexOrThrow("is_preview")) == 1,
                        isGenerated = it.getInt(it.getColumnIndexOrThrow("is_generated")) == 1
                    )
                )
            }
        }
        list.distinctBy { it.id }
    }

    suspend fun addSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        val db = writableDatabase
        val values = ContentValues().apply {
            put("search_query", query.trim())
            put("searched_time", System.currentTimeMillis())
        }
        db.insertWithOnConflict(TABLE_SEARCH_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun getRecentSearchQueries(): List<String> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val cursor = db.query(TABLE_SEARCH_HISTORY, arrayOf("search_query"), null, null, null, null, "searched_time DESC", "10")
        val list = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(it.getString(0))
            }
        }
        list
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.delete(TABLE_SEARCH_HISTORY, null, null)
    }
}
