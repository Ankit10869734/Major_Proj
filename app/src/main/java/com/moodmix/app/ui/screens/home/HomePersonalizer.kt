package com.moodmix.app.ui.screens.home

import androidx.compose.runtime.Immutable
import com.moodmix.app.domain.model.PlayableTrack
import java.util.Calendar
import kotlin.random.Random

@Immutable
data class HomeHeader(
    val greeting: String,
    val splashText: String,
    val aiPrompt: String,
    val moodSectionTitle: String,
    val moods: List<Pair<String, String>>,
    val feedTitle: String? = null,   // e.g. Because you searched “arijit”
    val feedTerm: String? = null     // search term used to fetch that row
)

object HomePersonalizer {

    private class MoodDef(val label: String, val prompt: String, val keywords: List<String>)

    private val moodCatalog = listOf(
        MoodDef("😊 Happy", "Upbeat happy energetic pop track", listOf("happy", "pop", "upbeat", "dance", "party", "summer", "fun")),
        MoodDef("😌 Calm", "Peaceful calm ambient acoustic music", listOf("calm", "ambient", "acoustic", "chill", "relax", "sleep", "peace")),
        MoodDef("🔥 Energetic", "High energy workout electronic music", listOf("energy", "workout", "gym", "electronic", "edm", "hip-hop", "rap", "rock", "metal", "house")),
        MoodDef("💭 Focus", "Instrumental lo-fi beats for deep study", listOf("focus", "study", "lofi", "lo-fi", "instrumental", "piano", "classical", "jazz")),
        MoodDef("🌙 Sad", "Melancholic slow piano melody", listOf("sad", "slow", "melancholic", "heartbreak", "blues", "ballad", "lonely")),
        MoodDef("❤️ Romantic", "Warm romantic soulful love songs", listOf("love", "romantic", "r&b", "soul", "romance")),
        MoodDef("🚗 Road Trip", "Feel-good indie and country road trip songs", listOf("road", "trip", "travel", "indie", "country", "folk"))
    )

    private class DayPart(val greetings: List<String>, val splashes: List<String>, val prompt: String)

    private fun dayPart(hour: Int): DayPart = when (hour) {
        in 0..4 -> DayPart(
            listOf("Late night session 🌙", "Late night vibes 🌌", "Midnight hours 🦉"),
            listOf("Want some music to drift off to sleep?", "Soft melodies for quiet late-night thoughts ✨", "The world is asleep, but the music is alive 🎧"),
            "quiet ambient music for sleep and relaxation"
        )
        in 5..11 -> DayPart(
            listOf("Good morning 🌅", "Morning rhythm ☕", "Sunrise beats ☀️"),
            listOf("Start your day with the perfect rhythm ☕", "Fresh morning coffee & fresh beats ☀️", "Sunrise melodies to power your morning 🌄"),
            "upbeat acoustic morning music for focus"
        )
        in 12..16 -> DayPart(
            listOf("Good afternoon ☀️", "Midday flow 🚀", "Afternoon vibes ⚡"),
            listOf("Midday boost to keep you in the flow 🚀", "Unwind with your afternoon soundtrack ☕", "High focus or relaxing chill beats?"),
            "energetic electronic beats for afternoon work"
        )
        else -> DayPart(
            listOf("Good evening 🌆", "Evening chill 🕯️", "Twilight soundscape 🌙"),
            listOf("Time to relax and tune into your vibe 🌆", "Unwinding after a long day 🎶", "Let the music take over the night ✨"),
            "calm acoustic melody for evening relaxation"
        )
    }

    private fun String.shortened(max: Int) = if (length > max) take(max).trimEnd() + "…" else this

    fun build(
        recent: List<PlayableTrack>,
        liked: List<PlayableTrack>,
        queries: List<String>
    ): HomeHeader {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = dayPart(hour)

        // Ignore tiny half-typed queries like "ar"
        val cleanQueries = queries.map { it.trim() }.filter { it.length >= 3 }
        val lastQuery = cleanQueries.firstOrNull()

        val genres = (liked + recent).mapNotNull { track ->
            track.genre?.trim()?.takeIf { it.isNotBlank() && !it.equals("Music", ignoreCase = true) }
        }
        val topGenre = genres.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

        val hasTaste = cleanQueries.isNotEmpty() || genres.isNotEmpty()

        // Score every mood against searches (weight 3) and library genres (weight 2)
        val searchText = cleanQueries.take(10).joinToString(" ").lowercase()
        val libraryText = genres.joinToString(" ").lowercase()
        val scored = moodCatalog.map { mood ->
            val score = mood.keywords.sumOf { kw ->
                (if (searchText.contains(kw)) 3 else 0) + (if (libraryText.contains(kw)) 2 else 0) as Int
            }
            mood to score
        }
        val topMood = scored.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first

        // Mood chips: first launch = default order; later = personal chips first, best-matching moods next
        val ordered = if (hasTaste) scored.sortedByDescending { it.second }.map { it.first } else moodCatalog
        val personalChips = buildList {
            lastQuery?.let { add("🔎 More ${it.shortened(18)}" to "Songs similar to $it") }
            topGenre?.let { add("🎧 $it Mix" to "Best $it songs with a fresh modern sound") }
        }
        val moods = (personalChips + ordered.map { it.label to it.prompt }).distinctBy { it.first }.take(8)

        // Splash text: mostly personal once we know the user
        val tasteSplashes = buildList {
            topGenre?.let { add("Fresh $it picks, just for you 🎧"); add("Still in a $it mood? 🎶") }
            lastQuery?.let { add("More of what you searched: “${it.shortened(22)}” ✨") }
        }
        val splash = if (tasteSplashes.isNotEmpty() && Random.nextInt(100) < 70) tasteSplashes.random() else part.splashes.random()

        val aiPrompt = when {
            lastQuery != null -> "A new track inspired by $lastQuery"
            topMood != null -> topMood.prompt
            else -> part.prompt
        }

        val moodTitle = if (hasTaste) listOf("Picked for you", "Your vibe right now", "Based on your taste").random() else "Your Mood"

        val feedTitle: String?
        val feedTerm: String?
        when {
            lastQuery != null -> { feedTitle = "Because you searched “${lastQuery.shortened(24)}”"; feedTerm = lastQuery }
            topGenre != null -> { feedTitle = "Because you like $topGenre"; feedTerm = topGenre }
            else -> { feedTitle = null; feedTerm = null }
        }

        return HomeHeader(
            greeting = part.greetings.random(),
            splashText = splash,
            aiPrompt = aiPrompt,
            moodSectionTitle = moodTitle,
            moods = moods,
            feedTitle = feedTitle,
            feedTerm = feedTerm
        )
    }
}